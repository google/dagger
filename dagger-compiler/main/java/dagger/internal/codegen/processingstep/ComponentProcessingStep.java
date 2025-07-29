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

package dagger.internal.codegen.processingstep;

import static com.google.common.collect.Sets.union;
import static dagger.internal.codegen.base.ComponentAnnotation.allComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotations;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotations;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.allCreatorAnnotations;
import static java.util.Collections.disjoint;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.validation.BindingGraphValidator;
import dagger.internal.codegen.validation.ComponentCreatorValidator;
import dagger.internal.codegen.validation.ComponentDescriptorValidator;
import dagger.internal.codegen.validation.ComponentValidator;
import dagger.internal.codegen.validation.ValidationReport;
import java.util.Set;
import javax.inject.Inject;

/**
 * A {@link ProcessingStep} that is responsible for dealing with a component or production component
 * as part of the {@link ComponentProcessor}.
 */
final class ComponentProcessingStep extends TypeCheckingProcessingStep<XTypeElement> {
  private final ComponentValidator componentValidator;
  private final ComponentCreatorValidator creatorValidator;
  private final ComponentDescriptorValidator componentDescriptorValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraphFactory bindingGraphFactory;
  private final SourceFileGenerator<BindingGraph> componentGenerator;
  private final BindingGraphValidator bindingGraphValidator;

  @Inject
  ComponentProcessingStep(
      ComponentValidator componentValidator,
      ComponentCreatorValidator creatorValidator,
      ComponentDescriptorValidator componentDescriptorValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraphFactory bindingGraphFactory,
      SourceFileGenerator<BindingGraph> componentGenerator,
      BindingGraphValidator bindingGraphValidator) {
    this.componentValidator = componentValidator;
    this.creatorValidator = creatorValidator;
    this.componentDescriptorValidator = componentDescriptorValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
    this.bindingGraphValidator = bindingGraphValidator;
  }

  @Override
  public Set<XClassName> annotationClassNames() {
    return union(allComponentAnnotations(), allCreatorAnnotations());
  }

  @Override
  protected void process(XTypeElement element, ImmutableSet<XClassName> annotations) {
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

  private void processRootComponent(XTypeElement component) {
    if (!isComponentValid(component)) {
      return;
    }
    ComponentDescriptor componentDescriptor =
        componentDescriptorFactory.rootComponentDescriptor(component);
    if (!isValid(componentDescriptor)) {
      return;
    }

    Supplier<dagger.internal.codegen.model.BindingGraph> fullBindingGraphSupplier =
        Suppliers.memoize(
            () -> bindingGraphFactory.create(componentDescriptor, true).topLevelBindingGraph());
    if (bindingGraphValidator.shouldDoFullBindingGraphValidation(component)) {
      if (!bindingGraphValidator.isValid(fullBindingGraphSupplier.get())) {
        return;
      }
    }

    BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor, false);
    if (bindingGraphValidator.isValid(
        bindingGraph.topLevelBindingGraph(), fullBindingGraphSupplier)) {
      generateComponent(bindingGraph);
    }
  }

  private void processSubcomponent(XTypeElement subcomponent) {
    if (!isComponentValid(subcomponent)) {
      return;
    }
    // TODO(dpb): ComponentDescriptorValidator for subcomponents, as we do for root components.
    ComponentDescriptor subcomponentDescriptor =
        componentDescriptorFactory.subcomponentDescriptor(subcomponent);
    if (!bindingGraphValidator.shouldDoFullBindingGraphValidation(subcomponent)) {
      return;
    }
    BindingGraph fullBindingGraph = bindingGraphFactory.create(subcomponentDescriptor, true);
    // In this case, we don't actually care about the return value. The important part here is that
    // BindingGraphValidator#isValid() runs all of the SPI plugins and reports any errors.
    // TODO(bcorso): Add a separate API with no return value for this particular case.
    boolean unusedIsValid = bindingGraphValidator.isValid(fullBindingGraph.topLevelBindingGraph());
  }

  private void generateComponent(BindingGraph bindingGraph) {
    componentGenerator.generate(bindingGraph, messager);
  }

  private void processCreator(XTypeElement creator) {
    creatorValidator.validate(creator).printMessagesTo(messager);
  }

  private boolean isComponentValid(XTypeElement component) {
    ValidationReport report = componentValidator.validate(component);
    report.printMessagesTo(messager);
    return report.isClean();
  }

  private boolean isValid(ComponentDescriptor componentDescriptor) {
    ValidationReport componentDescriptorReport =
        componentDescriptorValidator.validate(componentDescriptor);
    componentDescriptorReport.printMessagesTo(messager);
    return componentDescriptorReport.isClean();
  }
}
