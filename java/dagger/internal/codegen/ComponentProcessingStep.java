/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import dagger.Component;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentValidator.ComponentValidationReport;
import dagger.internal.codegen.DiagnosticReporterFactory.ErrorCountingDiagnosticReporter;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that is responsible for dealing with a component or production component
 * as part of the {@link ComponentProcessor}.
 */
final class ComponentProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final BuilderValidator builderValidator;
  private final ComponentHierarchyValidator componentHierarchyValidator;
  private final BindingGraphValidator bindingGraphValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;
  private final BindingGraphConverter bindingGraphConverter;
  private final ImmutableSet<BindingGraphPlugin> validationPlugins;
  private final ImmutableList<BindingGraphPlugin> spiPlugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;

  @Inject
  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      BuilderValidator builderValidator,
      ComponentHierarchyValidator componentHierarchyValidator,
      BindingGraphValidator bindingGraphValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      ComponentGenerator componentGenerator,
      @Validation Set<BindingGraphPlugin> validationPlugins,
      BindingGraphConverter bindingGraphConverter,
      ImmutableList<BindingGraphPlugin> spiPlugins,
      DiagnosticReporterFactory diagnosticReporterFactory) {
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.builderValidator = builderValidator;
    this.componentHierarchyValidator = componentHierarchyValidator;
    this.bindingGraphValidator = bindingGraphValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
    this.validationPlugins = ImmutableSet.copyOf(validationPlugins);
    this.bindingGraphConverter = bindingGraphConverter;
    this.spiPlugins = spiPlugins;
    this.diagnosticReporterFactory = diagnosticReporterFactory;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(
        Component.class,
        Component.Builder.class,
        ProductionComponent.class,
        ProductionComponent.Builder.class,
        Subcomponent.class,
        Subcomponent.Builder.class,
        ProductionSubcomponent.class,
        ProductionSubcomponent.Builder.class);
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();

    ImmutableSet<Element> componentElements =
        getElementsFromAnnotations(
            elementsByAnnotation, Component.class, ProductionComponent.class);
    ImmutableSet<Element> componentBuilderElements =
        getElementsFromAnnotations(
            elementsByAnnotation, Component.Builder.class, ProductionComponent.Builder.class);

    ImmutableSet<Element> subcomponentElements =
        getElementsFromAnnotations(
            elementsByAnnotation, Subcomponent.class, ProductionSubcomponent.class);
    ImmutableSet<Element> subcomponentBuilderElements =
        getElementsFromAnnotations(
            elementsByAnnotation, Subcomponent.Builder.class, ProductionSubcomponent.Builder.class);

    Map<Element, ValidationReport<TypeElement>> builderReportsByComponent =
        processBuilders(componentBuilderElements);
    Map<Element, ValidationReport<TypeElement>> builderReportsBySubcomponent =
        processBuilders(subcomponentBuilderElements);
    Map<Element, ValidationReport<TypeElement>> reportsBySubcomponent =
        processSubcomponents(subcomponentElements, subcomponentBuilderElements);

    for (TypeElement componentTypeElement : typesIn(componentElements)) {
      try {
        ComponentValidationReport validationReport =
            componentValidator.validate(
                componentTypeElement, subcomponentElements, subcomponentBuilderElements);
        validationReport.report().printMessagesTo(messager);
        if (!isClean(
            validationReport,
            builderReportsByComponent,
            reportsBySubcomponent,
            builderReportsBySubcomponent)) {
          continue;
        }
        ComponentDescriptor componentDescriptor =
            componentDescriptorFactory.forComponent(componentTypeElement);
        ValidationReport<TypeElement> hierarchyReport =
            componentHierarchyValidator.validate(componentDescriptor);
        hierarchyReport.printMessagesTo(messager);
        if (!hierarchyReport.isClean()) {
          continue;
        }
        BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
        ValidationReport<TypeElement> graphReport = bindingGraphValidator.validate(bindingGraph);
        graphReport.printMessagesTo(messager);
        if (!graphReport.isClean()) {
          continue;
        }

        if (!validationPlugins.isEmpty() || !spiPlugins.isEmpty()) {
          dagger.model.BindingGraph modelGraph = bindingGraphConverter.convert(bindingGraph);
          boolean reportedErrors =
              executePlugins(modelGraph, validationPlugins)
                  || executePlugins(modelGraph, spiPlugins);
          if (reportedErrors) {
            continue;
          }
        }

        generateComponent(bindingGraph);
      } catch (TypeNotPresentException e) {
        rejectedElements.add(componentTypeElement);
      }
    }
    return rejectedElements.build();
  }

  /**
   * Calls {@link BindingGraphPlugin#visitGraph(dagger.model.BindingGraph, DiagnosticReporter)} on
   * each of {@code plugins}.
   *
   * @return {@code true} if any plugin reported errors
   */
  private boolean executePlugins(
      dagger.model.BindingGraph graph, Iterable<BindingGraphPlugin> plugins) {
    // TODO(ronshapiro): Should we validate the uniqueness of plugin names?
    boolean reportedErrors = false;
    for (BindingGraphPlugin plugin : plugins) {
      ErrorCountingDiagnosticReporter reporter = diagnosticReporterFactory.reporter(graph, plugin);
      plugin.visitGraph(graph, reporter);
      reportedErrors |= reporter.reportedErrors();
    }

    return reportedErrors;
  }

  private void generateComponent(BindingGraph bindingGraph) {
    componentGenerator.generate(bindingGraph, messager);
  }

  static ImmutableSet<Element> getElementsFromAnnotations(
      final SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation,
      Class<? extends Annotation>... annotations) {
    return ImmutableSet.copyOf(
        Multimaps.filterKeys(elementsByAnnotation, Predicates.in(ImmutableSet.copyOf(annotations)))
            .values());
  }

  private Map<Element, ValidationReport<TypeElement>> processBuilders(
      Set<? extends Element> builderElements) {
    Map<Element, ValidationReport<TypeElement>> builderReportsByComponent = Maps.newHashMap();
    for (Element element : builderElements) {
      ValidationReport<TypeElement> report =
          builderValidator.validate(MoreElements.asType(element));
      report.printMessagesTo(messager);
      builderReportsByComponent.put(element.getEnclosingElement(), report);
    }
    return builderReportsByComponent;
  }

  private Map<Element, ValidationReport<TypeElement>> processSubcomponents(
      Set<? extends Element> subcomponentElements,
      Set<? extends Element> subcomponentBuilderElements) {
    Map<Element, ValidationReport<TypeElement>> reportsBySubcomponent = Maps.newHashMap();
    for (Element element : subcomponentElements) {
      ComponentValidationReport report =
          componentValidator.validate(
              MoreElements.asType(element), subcomponentElements, subcomponentBuilderElements);
      report.report().printMessagesTo(messager);
      reportsBySubcomponent.put(element, report.report());
    }
    return reportsBySubcomponent;
  }

  /**
   * Returns true if the component's report is clean, its builder report is clean, and all
   * referenced subcomponent reports and subcomponent builder reports are clean.
   */
  private boolean isClean(
      ComponentValidationReport report,
      Map<Element, ValidationReport<TypeElement>> builderReportsByComponent,
      Map<Element, ValidationReport<TypeElement>> reportsBySubcomponent,
      Map<Element, ValidationReport<TypeElement>> builderReportsBySubcomponent) {
    Element component = report.report().subject();
    ValidationReport<?> componentReport = report.report();
    if (!componentReport.isClean()) {
      return false;
    }
    ValidationReport<?> builderReport = builderReportsByComponent.get(component);
    if (builderReport != null && !builderReport.isClean()) {
      return false;
    }
    for (Element element : report.referencedSubcomponents()) {
      ValidationReport<?> subcomponentBuilderReport = builderReportsBySubcomponent.get(element);
      if (subcomponentBuilderReport != null && !subcomponentBuilderReport.isClean()) {
        return false;
      }
      ValidationReport<?> subcomponentReport = reportsBySubcomponent.get(element);
      if (subcomponentReport != null && !subcomponentReport.isClean()) {
        return false;
      }
    }
    return true;
  }
}
