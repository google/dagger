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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import dagger.Component;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentValidator.ComponentValidationReport;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
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
  private final ComponentDescriptorValidator componentDescriptorValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;
  private final BindingGraphConverter bindingGraphConverter;
  private final BindingGraphPlugins validationPlugins;
  private final BindingGraphPlugins spiPlugins;
  private final CompilerOptions compilerOptions;

  @Inject
  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      BuilderValidator builderValidator,
      ComponentDescriptorValidator componentDescriptorValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      ComponentGenerator componentGenerator,
      BindingGraphConverter bindingGraphConverter,
      @Validation BindingGraphPlugins validationPlugins,
      BindingGraphPlugins spiPlugins,
      CompilerOptions compilerOptions) {
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.builderValidator = builderValidator;
    this.componentDescriptorValidator = componentDescriptorValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
    this.bindingGraphConverter = bindingGraphConverter;
    this.validationPlugins = validationPlugins;
    this.spiPlugins = spiPlugins;
    this.compilerOptions = compilerOptions;
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
        ValidationReport<TypeElement> componentDescriptorReport =
            componentDescriptorValidator.validate(componentDescriptor);
        componentDescriptorReport.printMessagesTo(messager);
        if (!componentDescriptorReport.isClean()) {
          continue;
        }
        BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
        if (isValid(bindingGraph)) {
          generateComponent(bindingGraph);
        }
      } catch (TypeNotPresentException e) {
        rejectedElements.add(componentTypeElement);
      }
    }

    if (compilerOptions.aheadOfTimeSubcomponents()) {
      for (TypeElement subcomponentTypeElement : typesIn(subcomponentElements)) {
        if (!subcomponentIsClean(
            subcomponentTypeElement, reportsBySubcomponent, builderReportsBySubcomponent)) {
          continue;
        }
        try {
          ComponentDescriptor componentDescriptor =
              componentDescriptorFactory.forComponent(subcomponentTypeElement);
          BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
          // TODO(b/72748365): Do subgraph validation.
          generateComponent(bindingGraph);
        } catch (TypeNotPresentException e) {
          rejectedElements.add(subcomponentTypeElement);
        }
      }
    }

    return rejectedElements.build();
  }

  private boolean isValid(BindingGraph bindingGraph) {
    dagger.model.BindingGraph modelGraph = bindingGraphConverter.convert(bindingGraph);
    return !validationPlugins.pluginsReportErrors(modelGraph)
        && !spiPlugins.pluginsReportErrors(modelGraph);
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
      if (!subcomponentIsClean(element, reportsBySubcomponent, builderReportsBySubcomponent)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if the reports associated with the subcomponent are clean. */
  private boolean subcomponentIsClean(
      Element subcomponentElement,
      Map<Element, ValidationReport<TypeElement>> reportsBySubcomponent,
      Map<Element, ValidationReport<TypeElement>> builderReportsBySubcomponent) {
    ValidationReport<?> subcomponentBuilderReport =
        builderReportsBySubcomponent.get(subcomponentElement);
    if (subcomponentBuilderReport != null && !subcomponentBuilderReport.isClean()) {
      return false;
    }
    ValidationReport<?> subcomponentReport = reportsBySubcomponent.get(subcomponentElement);
    if (subcomponentReport != null && !subcomponentReport.isClean()) {
      return false;
    }
    return true;
  }
}
