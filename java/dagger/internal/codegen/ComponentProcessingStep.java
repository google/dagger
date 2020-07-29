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

import static com.google.auto.common.MoreElements.asType;
import static com.google.common.collect.Sets.union;
import static dagger.internal.codegen.base.ComponentAnnotation.allComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.allCreatorAnnotations;
import static java.util.Collections.disjoint;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.autocommon.BasicAnnotationProcessor.ProcessingStep;
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
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import java.lang.annotation.Annotation;
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
  protected void process(
      TypeElement element, ImmutableSet<Class<? extends Annotation>> annotations) {
    if (!disjoint(annotations, rootComponentAnnotations())) {
      processRootComponent(element);
    }
    if (!disjoint(annotations, subcomponentAnnotations())) {
      processSubcomponent(element);
    }
    if (!disjoint(annotations, allCreatorAnnotations())) {
      processCreator(element);
    }
  }

  private void processRootComponent(TypeElement component) {
    if (!isComponentValid(component)) {
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
    if (!isComponentValid(subcomponent)) {
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

  private void processCreator(Element creator) {
    creatorValidator.validate(MoreElements.asType(creator)).printMessagesTo(messager);
  }

  private boolean isComponentValid(Element component) {
    ValidationReport<?> report = componentValidator.validate(asType(component));
    report.printMessagesTo(messager);
    return report.isClean();
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
}
