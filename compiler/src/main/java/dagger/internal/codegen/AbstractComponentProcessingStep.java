/*
 * Copyright (C) 2015 Google, Inc.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/**
 * A {@link ProcessingStep} that is responsible for dealing with a component or production component
 * as part of the {@link ComponentProcessor}.
 */
abstract class AbstractComponentProcessingStep implements ProcessingStep {

  private final Messager messager;
  private final BindingGraphValidator bindingGraphValidator;
  private final BindingGraph.Factory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;

  AbstractComponentProcessingStep(
      Messager messager,
      BindingGraphValidator bindingGraphValidator,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    this.messager = messager;
    this.bindingGraphValidator = bindingGraphValidator;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
  }

  @Override
  public final ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();
    for (ComponentDescriptor componentDescriptor : componentDescriptors(elementsByAnnotation)) {
      try {
        BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
        ValidationReport<BindingGraph> graphReport = bindingGraphValidator.validate(bindingGraph);
        graphReport.printMessagesTo(messager);
        if (graphReport.isClean()) {
          try {
            componentGenerator.generate(bindingGraph);
          } catch (SourceFileGenerationException e) {
            e.printMessageTo(messager);
          }
        }
      } catch (TypeNotPresentException e) {
        rejectedElements.add(componentDescriptor.componentDefinitionType());
      }
    }
    return rejectedElements.build();
  }

  /**
   * Returns a {@link ComponentDescriptor} for each valid component element for which an
   * implementation class should be generated.
   */
  protected abstract Set<ComponentDescriptor> componentDescriptors(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation);
}
