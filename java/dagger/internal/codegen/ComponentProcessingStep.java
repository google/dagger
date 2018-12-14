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

import static dagger.internal.codegen.ComponentKind.allComponentAndBuilderAnnotations;
import static dagger.internal.codegen.ComponentKind.annotationsFor;
import static dagger.internal.codegen.ComponentKind.builderAnnotationsFor;
import static dagger.internal.codegen.ComponentKind.subcomponentKinds;
import static dagger.internal.codegen.ComponentKind.topLevelComponentKinds;
import static java.util.Collections.disjoint;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import dagger.internal.codegen.ComponentValidator.ComponentValidationReport;
import java.lang.annotation.Annotation;
import java.util.HashMap;
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
final class ComponentProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final ComponentCreatorValidator creatorValidator;
  private final ComponentDescriptorValidator componentDescriptorValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final SourceFileGenerator<BindingGraph> componentGenerator;
  private final BindingGraphConverter bindingGraphConverter;
  private final BindingGraphPlugins validationPlugins;
  private final BindingGraphPlugins spiPlugins;
  private final CompilerOptions compilerOptions;
  private ImmutableSet<Element> subcomponentElements;
  private ImmutableSet<Element> subcomponentBuilderElements;
  private ImmutableMap<Element, ValidationReport<TypeElement>> builderReportsByComponent;
  private ImmutableMap<Element, ValidationReport<TypeElement>> builderReportsBySubcomponent;
  private ImmutableMap<Element, ValidationReport<TypeElement>> reportsBySubcomponent;

  @Inject
  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      ComponentCreatorValidator creatorValidator,
      ComponentDescriptorValidator componentDescriptorValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      SourceFileGenerator<BindingGraph> componentGenerator,
      BindingGraphConverter bindingGraphConverter,
      @Validation BindingGraphPlugins validationPlugins,
      BindingGraphPlugins spiPlugins,
      CompilerOptions compilerOptions) {
    super(MoreElements::asType);
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.creatorValidator = creatorValidator;
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
    return allComponentAndBuilderAnnotations();
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    subcomponentElements =
        getElementsFromAnnotations(elementsByAnnotation, annotationsFor(subcomponentKinds()));
    subcomponentBuilderElements =
        getElementsFromAnnotations(
            elementsByAnnotation, builderAnnotationsFor(subcomponentKinds()));

    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();

    builderReportsByComponent =
        processBuilders(
            getElementsFromAnnotations(
                elementsByAnnotation, builderAnnotationsFor(topLevelComponentKinds())),
            rejectedElements);
    builderReportsBySubcomponent = processBuilders(subcomponentBuilderElements, rejectedElements);
    reportsBySubcomponent =
        processSubcomponents(subcomponentElements, subcomponentBuilderElements, rejectedElements);

    return rejectedElements.addAll(super.process(elementsByAnnotation)).build();
  }

  @Override
  protected void process(
      TypeElement element, ImmutableSet<Class<? extends Annotation>> annotations) {
    if (!disjoint(annotations, annotationsFor(topLevelComponentKinds()))) {
      ComponentValidationReport validationReport =
          componentValidator.validate(element, subcomponentElements, subcomponentBuilderElements);
      validationReport.report().printMessagesTo(messager);
      if (!isClean(validationReport)) {
        return;
      }
      ComponentDescriptor componentDescriptor = componentDescriptorFactory.forTypeElement(element);
      ValidationReport<TypeElement> componentDescriptorReport =
          componentDescriptorValidator.validate(componentDescriptor);
      componentDescriptorReport.printMessagesTo(messager);
      if (!componentDescriptorReport.isClean()) {
        return;
      }
      BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
      if (isValid(bindingGraph)) {
        generateComponent(bindingGraph);
      }
    }
    if (compilerOptions.aheadOfTimeSubcomponents()
        && !disjoint(annotations, annotationsFor(subcomponentKinds()))) {
      if (!subcomponentIsClean(element)) {
        return;
      }
      ComponentDescriptor componentDescriptor = componentDescriptorFactory.forTypeElement(element);
      BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
      if (isValid(bindingGraph)) {
        generateComponent(bindingGraph);
      }
    }
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
      Set<Class<? extends Annotation>> annotations) {
    return ImmutableSet.copyOf(
        Multimaps.filterKeys(elementsByAnnotation, Predicates.in(annotations)).values());
  }

  private ImmutableMap<Element, ValidationReport<TypeElement>> processBuilders(
      Set<? extends Element> builderElements, ImmutableSet.Builder<Element> rejectedElements) {
    // Can't use an ImmutableMap.Builder here because a component may have (invalidly) more than one
    // builder type, and that would make ImmutableMap.Builder throw.
    Map<Element, ValidationReport<TypeElement>> reports = new HashMap<>();
    for (Element element : builderElements) {
      try {
        ValidationReport<TypeElement> report =
            creatorValidator.validate(MoreElements.asType(element));
        report.printMessagesTo(messager);
        reports.put(element.getEnclosingElement(), report);
      } catch (TypeNotPresentException e) {
        rejectedElements.add(element);
      }
    }
    return ImmutableMap.copyOf(reports);
  }

  private ImmutableMap<Element, ValidationReport<TypeElement>> processSubcomponents(
      Set<? extends Element> subcomponentElements,
      Set<? extends Element> subcomponentBuilderElements,
      ImmutableSet.Builder<Element> rejectedElements) {
    ImmutableMap.Builder<Element, ValidationReport<TypeElement>> reports = ImmutableMap.builder();
    for (Element element : subcomponentElements) {
      try {
        ComponentValidationReport report =
            componentValidator.validate(
                MoreElements.asType(element), subcomponentElements, subcomponentBuilderElements);
        report.report().printMessagesTo(messager);
        reports.put(element, report.report());
      } catch (TypeNotPresentException e) {
        rejectedElements.add(element);
      }
    }
    return reports.build();
  }

  /**
   * Returns true if the component's report is clean, its builder report is clean, and all
   * referenced subcomponent reports and subcomponent builder reports are clean.
   */
  private boolean isClean(ComponentValidationReport report) {
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
      if (!subcomponentIsClean(element)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if the reports associated with the subcomponent are clean. */
  private boolean subcomponentIsClean(Element subcomponentElement) {
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
