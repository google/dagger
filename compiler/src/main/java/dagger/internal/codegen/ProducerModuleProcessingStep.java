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
import com.google.auto.common.SuperficialValidation;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.element.ElementKind.METHOD;

/**
 * An annotation processor for generating Dagger implementation code based on the
 * {@link ProducerModule} (and {@link Produces}) annotation.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerModuleProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final ProducesMethodValidator producesMethodValidator;
  private final ProductionBinding.Factory productionBindingFactory;
  private final ProducerFactoryGenerator factoryGenerator;
  private final Set<Element> processedModuleElements = Sets.newLinkedHashSet();

  ProducerModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      ProducesMethodValidator producesMethodValidator,
      ProductionBinding.Factory productionBindingFactory,
      ProducerFactoryGenerator factoryGenerator) {
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.producesMethodValidator = producesMethodValidator;
    this.productionBindingFactory = productionBindingFactory;
    this.factoryGenerator = factoryGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Produces.class, ProducerModule.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    // first, check and collect all produces methods
    ImmutableSet.Builder<ExecutableElement> validProducesMethodsBuilder = ImmutableSet.builder();
    for (Element producesElement : elementsByAnnotation.get(Produces.class)) {
      if (producesElement.getKind().equals(METHOD)) {
        ExecutableElement producesMethodElement = (ExecutableElement) producesElement;
        ValidationReport<ExecutableElement> methodReport =
            producesMethodValidator.validate(producesMethodElement);
        methodReport.printMessagesTo(messager);
        if (methodReport.isClean()) {
          validProducesMethodsBuilder.add(producesMethodElement);
        }
      }
    }
    ImmutableSet<ExecutableElement> validProducesMethods = validProducesMethodsBuilder.build();

    // process each module
    for (Element moduleElement :
        Sets.difference(elementsByAnnotation.get(ProducerModule.class),
            processedModuleElements)) {
      if (SuperficialValidation.validateElement(moduleElement)) {
        ValidationReport<TypeElement> report =
            moduleValidator.validate(MoreElements.asType(moduleElement));
        report.printMessagesTo(messager);

        if (report.isClean()) {
          ImmutableSet.Builder<ExecutableElement> moduleProducesMethodsBuilder =
              ImmutableSet.builder();
          List<ExecutableElement> moduleMethods =
              ElementFilter.methodsIn(moduleElement.getEnclosedElements());
          for (ExecutableElement methodElement : moduleMethods) {
            if (isAnnotationPresent(methodElement, Produces.class)) {
              moduleProducesMethodsBuilder.add(methodElement);
            }
          }
          ImmutableSet<ExecutableElement> moduleProducesMethods =
              moduleProducesMethodsBuilder.build();

          if (Sets.difference(moduleProducesMethods, validProducesMethods).isEmpty()) {
            // all of the produces methods in this module are valid!
            // time to generate some factories!
            ImmutableSet<ProductionBinding> bindings = FluentIterable.from(moduleProducesMethods)
                .transform(new Function<ExecutableElement, ProductionBinding>() {
                  @Override
                  public ProductionBinding apply(ExecutableElement producesMethod) {
                    return productionBindingFactory.forProducesMethod(producesMethod,
                        producesMethod.getEnclosingElement().asType());
                  }
                })
                .toSet();

            try {
              for (ProductionBinding binding : bindings) {
                factoryGenerator.generate(binding);
              }
            } catch (SourceFileGenerationException e) {
              e.printMessageTo(messager);
            }
          }
        }

        processedModuleElements.add(moduleElement);
      }
    }
    return ImmutableSet.of();
  }
}
