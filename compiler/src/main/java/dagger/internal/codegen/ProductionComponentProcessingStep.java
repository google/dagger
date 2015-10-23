/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that is responsible for dealing with the {@link ProductionComponent}
 * annotation as part of the {@link ComponentProcessor}.
 *
 * @author Jesse Beder
 */
final class ProductionComponentProcessingStep extends AbstractComponentProcessingStep {
  private final Messager messager;
  private final ProductionComponentValidator componentValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;

  ProductionComponentProcessingStep(
      Messager messager,
      ProductionComponentValidator componentValidator,
      BindingGraphValidator bindingGraphValidator,
      ComponentDescriptor.Factory componentDescriptorFactory,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    super(
        messager,
        bindingGraphValidator,
        bindingGraphFactory,
        componentGenerator);
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(ProductionComponent.class);
  }

  @Override
  protected ImmutableSet<ComponentDescriptor> componentDescriptors(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<ComponentDescriptor> componentDescriptors = ImmutableSet.builder();
    Set<Element> componentElements = elementsByAnnotation.get(ProductionComponent.class);
    for (Element element : componentElements) {
      TypeElement componentTypeElement = MoreElements.asType(element);
      ValidationReport<TypeElement> componentReport =
          componentValidator.validate(componentTypeElement);
      componentReport.printMessagesTo(messager);
      if (componentReport.isClean()) {
        componentDescriptors.add(
            componentDescriptorFactory.forProductionComponent(componentTypeElement));
      }
    }
    return componentDescriptors.build();
  }
}
