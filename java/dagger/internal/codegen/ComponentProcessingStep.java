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

import static com.google.common.collect.Sets.union;
import static dagger.internal.codegen.base.ComponentAnnotation.allComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.allCreatorAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.rootComponentCreatorAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.subcomponentCreatorAnnotations;
import static java.util.Collections.disjoint;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingGraphConverter;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptorFactory;
import dagger.internal.codegen.validation.BindingGraphValidator;
import dagger.internal.codegen.validation.ComponentCreatorValidator;
import dagger.internal.codegen.validation.ComponentDescriptorValidator;
import dagger.internal.codegen.validation.ComponentValidator;
import dagger.internal.codegen.validation.ComponentValidator.ComponentValidationReport;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
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
  private final ComponentDescriptorFactory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final SourceFileGenerator<BindingGraph> componentGenerator;
  private final BindingGraphConverter bindingGraphConverter;
  private final BindingGraphValidator bindingGraphValidator;
  private ImmutableSet<Element> subcomponentElements;
  private ImmutableSet<Element> subcomponentCreatorElements;
  private ImmutableMap<Element, ValidationReport<TypeElement>> creatorReportsByComponent;
  private ImmutableMap<Element, ValidationReport<TypeElement>> creatorReportsBySubcomponent;
  private ImmutableMap<Element, ValidationReport<TypeElement>> reportsBySubcomponent;

  @Inject
  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      ComponentCreatorValidator creatorValidator,
      ComponentDescriptorValidator componentDescriptorValidator,
      ComponentDescriptorFactory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      SourceFileGenerator<BindingGraph> componentGenerator,
      BindingGraphConverter bindingGraphConverter,
      BindingGraphValidator bindingGraphValidator) {
    super(MoreElements::asType);
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.creatorValidator = creatorValidator;
    this.componentDescriptorValidator = componentDescriptorValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
    this.bindingGraphConverter = bindingGraphConverter;
    this.bindingGraphValidator = bindingGraphValidator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return union(allComponentAnnotations(), allCreatorAnnotations());
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    subcomponentElements =
        getElementsFromAnnotations(elementsByAnnotation, subcomponentAnnotations());
    subcomponentCreatorElements =
        getElementsFromAnnotations(elementsByAnnotation, subcomponentCreatorAnnotations());

    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();

    creatorReportsByComponent =
        processCreators(
            getElementsFromAnnotations(elementsByAnnotation, rootComponentCreatorAnnotations()),
            rejectedElements);
    creatorReportsBySubcomponent = processCreators(subcomponentCreatorElements, rejectedElements);
    reportsBySubcomponent =
        processSubcomponents(subcomponentElements, subcomponentCreatorElements, rejectedElements);

    return rejectedElements.addAll(super.process(elementsByAnnotation)).build();
  }

  @Override
  protected void process(
      TypeElement element, ImmutableSet<Class<? extends Annotation>> annotations) {
    if (!disjoint(annotations, rootComponentAnnotations())) {
      processRootComponent(element);
    }
    if (!disjoint(annotations, subcomponentAnnotations())) {
      processSubcomponent(element);
    }
  }

  private void processRootComponent(TypeElement component) {
    if (!isRootComponentValid(component)) {
      return;
    }
    ComponentDescriptor componentDescriptor =
        componentDescriptorFactory.rootComponentDescriptor(component);
    if (!isValid(componentDescriptor)) {
      return;
    }
    if (!validateFullBindingGraph(componentDescriptor)) {
      return;
    }
    BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor, false);
    if (isValid(bindingGraph)) {
      generateComponent(bindingGraph);
    }
  }

  private void processSubcomponent(TypeElement subcomponent) {
    if (!bindingGraphValidator.shouldDoFullBindingGraphValidation(subcomponent)) {
      return;
    }
    if (!isSubcomponentValid(subcomponent)) {
      return;
    }
    ComponentDescriptor subcomponentDescriptor =
        componentDescriptorFactory.subcomponentDescriptor(subcomponent);
    // TODO(dpb): ComponentDescriptorValidator for subcomponents, as we do for root components.
    validateFullBindingGraph(subcomponentDescriptor);
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

  private ImmutableMap<Element, ValidationReport<TypeElement>> processCreators(
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

  private boolean isRootComponentValid(TypeElement rootComponent) {
    ComponentValidationReport validationReport =
        componentValidator.validate(
            rootComponent, subcomponentElements, subcomponentCreatorElements);
    validationReport.report().printMessagesTo(messager);
    return isClean(validationReport);
  }

  // TODO(dpb): Clean up generics so this can take TypeElement.
  private boolean isSubcomponentValid(Element subcomponentElement) {
    ValidationReport<?> subcomponentCreatorReport =
        creatorReportsBySubcomponent.get(subcomponentElement);
    if (subcomponentCreatorReport != null && !subcomponentCreatorReport.isClean()) {
      return false;
    }
    ValidationReport<?> subcomponentReport = reportsBySubcomponent.get(subcomponentElement);
    return subcomponentReport == null || subcomponentReport.isClean();
  }

  @CanIgnoreReturnValue
  private boolean validateFullBindingGraph(ComponentDescriptor componentDescriptor) {
    TypeElement component = componentDescriptor.typeElement();
    if (!bindingGraphValidator.shouldDoFullBindingGraphValidation(component)) {
      return true;
    }
    BindingGraph fullBindingGraph = bindingGraphFactory.create(componentDescriptor, true);
    return isValid(fullBindingGraph);
  }

  private boolean isValid(ComponentDescriptor componentDescriptor) {
    ValidationReport<TypeElement> componentDescriptorReport =
        componentDescriptorValidator.validate(componentDescriptor);
    componentDescriptorReport.printMessagesTo(messager);
    return componentDescriptorReport.isClean();
  }

  private boolean isValid(BindingGraph bindingGraph) {
    return bindingGraphValidator.isValid(bindingGraphConverter.convert(bindingGraph));
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
    ValidationReport<?> builderReport = creatorReportsByComponent.get(component);
    if (builderReport != null && !builderReport.isClean()) {
      return false;
    }
    for (Element element : report.referencedSubcomponents()) {
      if (!isSubcomponentValid(element)) {
        return false;
      }
    }
    return true;
  }
}
