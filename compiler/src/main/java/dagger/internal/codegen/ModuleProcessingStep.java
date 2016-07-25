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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.Util.elementsWithAnnotation;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 *
 * @param <B> the type of binding created from methods
 */
final class ModuleProcessingStep<B extends Binding> implements ProcessingStep {

  /**
   * A {@link ProcessingStep} for {@link Module @Module} classes that generates factories for
   * {@link Provides @Provides} methods.
   */
  static ModuleProcessingStep<ProvisionBinding> moduleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      final ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      ProvidesMethodValidator providesMethodValidator,
      BindsMethodValidator bindsMethodValidator,
      MultibindsMethodValidator multibindsMethodValidator) {
    return new ModuleProcessingStep<>(
        messager,
        Module.class,
        moduleValidator,
        Provides.class,
        new ModuleMethodBindingFactory<ProvisionBinding>() {
          @Override
          public ProvisionBinding bindingForModuleMethod(
              ExecutableElement method, TypeElement module) {
            return provisionBindingFactory.forProvidesMethod(method, module);
          }
        },
        factoryGenerator,
        ImmutableSet.of(providesMethodValidator, bindsMethodValidator, multibindsMethodValidator));
  }

  /**
   * A {@link ProcessingStep} for {@link ProducerModule @ProducerModule} classes that generates
   * factories for {@link Produces @Produces} methods.
   */
  static ModuleProcessingStep<ProductionBinding> producerModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      final ProductionBinding.Factory productionBindingFactory,
      ProducerFactoryGenerator producerFactoryGenerator,
      ProducesMethodValidator producesMethodValidator,
      BindsMethodValidator bindsMethodValidator,
      MultibindsMethodValidator multibindsMethodValidator) {
    return new ModuleProcessingStep<>(
        messager,
        ProducerModule.class,
        moduleValidator,
        Produces.class,
        new ModuleMethodBindingFactory<ProductionBinding>() {
          @Override
          public ProductionBinding bindingForModuleMethod(
              ExecutableElement method, TypeElement module) {
            return productionBindingFactory.forProducesMethod(method, module);
          }
        },
        producerFactoryGenerator,
        ImmutableSet.of(producesMethodValidator, bindsMethodValidator, multibindsMethodValidator));
  }

  private final Messager messager;
  private final Class<? extends Annotation> moduleAnnotation;
  private final ModuleValidator moduleValidator;
  private final Class<? extends Annotation> factoryMethodAnnotation;
  private final ModuleMethodBindingFactory<B> moduleMethodBindingFactory;
  private final SourceFileGenerator<B> factoryGenerator;
  private final ImmutableSet<? extends BindingMethodValidator> methodValidators;
  private final Set<TypeElement> processedModuleElements = Sets.newLinkedHashSet();

  /**
   * Creates a new processing step.
   *
   * @param moduleAnnotation the annotation on the module class
   * @param factoryMethodAnnotation the annotation on methods that need factories
   * @param methodValidators validators for binding methods
   */
  ModuleProcessingStep(
      Messager messager,
      Class<? extends Annotation> moduleAnnotation,
      ModuleValidator moduleValidator,
      Class<? extends Annotation> factoryMethodAnnotation,
      ModuleMethodBindingFactory<B> moduleMethodBindingFactory,
      SourceFileGenerator<B> factoryGenerator,
      Iterable<? extends BindingMethodValidator> methodValidators) {
    this.messager = messager;
    this.moduleAnnotation = moduleAnnotation;
    this.moduleValidator = moduleValidator;
    this.factoryMethodAnnotation = factoryMethodAnnotation;
    this.moduleMethodBindingFactory = moduleMethodBindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.methodValidators = ImmutableSet.copyOf(methodValidators);
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    ImmutableSet.Builder<Class<? extends Annotation>> annotations = ImmutableSet.builder();
    annotations.add(moduleAnnotation);
    for (BindingMethodValidator validator : methodValidators) {
      annotations.add(validator.methodAnnotation());
    }
    return annotations.build();
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet<ExecutableElement> validMethods = validMethods(elementsByAnnotation);

    // process each module
    for (TypeElement moduleElement :
        Sets.difference(
            typesIn(elementsByAnnotation.get(moduleAnnotation)), processedModuleElements)) {
      ValidationReport<TypeElement> report = moduleValidator.validate(moduleElement);
      report.printMessagesTo(messager);

      if (report.isClean()) {
        List<ExecutableElement> moduleMethods = methodsIn(moduleElement.getEnclosedElements());
        if (moduleMethodsAreValid(validMethods, moduleMethods)) {
          for (ExecutableElement method :
              elementsWithAnnotation(moduleMethods, factoryMethodAnnotation)) {
            generateFactory(
                moduleMethodBindingFactory.bindingForModuleMethod(method, moduleElement));
          }
        }
      }
      processedModuleElements.add(moduleElement);
    }
    return ImmutableSet.of();
  }

  /** The binding methods that are valid according to their validator. */
  private ImmutableSet<ExecutableElement> validMethods(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<ExecutableElement> validMethods = ImmutableSet.builder();
    for (BindingMethodValidator validator : methodValidators) {
      validMethods.addAll(
          validator.validate(
              messager, methodsIn(elementsByAnnotation.get(validator.methodAnnotation()))));
    }
    return validMethods.build();
  }

  /**
   * {@code true} if all {@code moduleMethods} that are annotated with a binding method annotation
   * are in {@code validMethods}.
   */
  private boolean moduleMethodsAreValid(
      ImmutableSet<ExecutableElement> validMethods, Iterable<ExecutableElement> moduleMethods) {
    for (ExecutableElement methodElement : moduleMethods) {
      if (!validMethods.contains(methodElement)) {
        for (BindingMethodValidator validator : methodValidators) {
          if (isAnnotationPresent(methodElement, validator.methodAnnotation())) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private void generateFactory(B binding) {
    try {
      factoryGenerator.generate(binding);
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(messager);
    }
  }

  private interface ModuleMethodBindingFactory<B extends Binding> {
    B bindingForModuleMethod(ExecutableElement method, TypeElement module);
  }
}
