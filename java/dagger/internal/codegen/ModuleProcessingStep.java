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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

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

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep implements ProcessingStep {

  /**
   * A {@link ProcessingStep} for {@code @Module} classes that generates factories for
   * {@code @Provides} methods.
   */
  static ModuleProcessingStep moduleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator) {
    return new ModuleProcessingStep(
        messager,
        Module.class,
        moduleValidator,
        ImmutableSet.of(
            new ProvisionModuleMethodFactoryGenerator(provisionBindingFactory, factoryGenerator)));
  }

  /**
   * A {@link ProcessingStep} for {@code @ProducerModule} classes that generates factories for
   * {@code @Provides} and {@code @Produces} methods.
   */
  static ModuleProcessingStep producerModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      FactoryGenerator factoryGenerator,
      ProductionBinding.Factory productionBindingFactory,
      ProducerFactoryGenerator producerFactoryGenerator) {
    return new ModuleProcessingStep(
        messager,
        ProducerModule.class,
        moduleValidator,
        ImmutableSet.of(
            new ProvisionModuleMethodFactoryGenerator(provisionBindingFactory, factoryGenerator),
            new ProductionModuleMethodFactoryGenerator(
                productionBindingFactory, producerFactoryGenerator)));
  }

  private final Messager messager;
  private final Class<? extends Annotation> moduleAnnotation;
  private final ModuleValidator moduleValidator;
  private final ImmutableSet<ModuleMethodFactoryGenerator> moduleMethodFactoryGenerators;
  private final Set<TypeElement> processedModuleElements = Sets.newLinkedHashSet();

  /**
   * Creates a new processing step.
   *
   * @param moduleAnnotation the annotation on the module class
   */
  ModuleProcessingStep(
      Messager messager,
      Class<? extends Annotation> moduleAnnotation,
      ModuleValidator moduleValidator,
      ImmutableSet<ModuleMethodFactoryGenerator> moduleMethodFactoryGenerators) {
    this.messager = messager;
    this.moduleAnnotation = moduleAnnotation;
    this.moduleValidator = moduleValidator;
    this.moduleMethodFactoryGenerators = moduleMethodFactoryGenerators;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(moduleAnnotation);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    List<TypeElement> modules = typesIn(elementsByAnnotation.values());
    moduleValidator.addKnownModules(modules);
    for (TypeElement module : modules) {
      if (processedModuleElements.add(module)) {
        processModule(module);
      }
    }
    return ImmutableSet.of();
  }

  private void processModule(TypeElement module) {
    ValidationReport<TypeElement> report = moduleValidator.validate(module);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      for (ExecutableElement method : methodsIn(module.getEnclosedElements())) {
        for (ModuleMethodFactoryGenerator generator : moduleMethodFactoryGenerators) {
          if (isAnnotationPresent(method, generator.factoryMethodAnnotation())) {
            generator.generate(method, module, messager);
          }
        }
      }
    }
  }

  interface ModuleMethodFactoryGenerator {
    /** Binding method annotation for which factories should be generated. */
    Class<? extends Annotation> factoryMethodAnnotation();

    /** Generates the factory source file for the given method and module. */
    void generate(ExecutableElement method, TypeElement moduleElement, Messager messager);
  }

  private static final class ProvisionModuleMethodFactoryGenerator
      implements ModuleMethodFactoryGenerator {

    private final ProvisionBinding.Factory provisionBindingFactory;
    private final FactoryGenerator factoryGenerator;

    ProvisionModuleMethodFactoryGenerator(
        ProvisionBinding.Factory provisionBindingFactory, FactoryGenerator factoryGenerator) {
      this.provisionBindingFactory = provisionBindingFactory;
      this.factoryGenerator = factoryGenerator;
    }

    @Override
    public Class<? extends Annotation> factoryMethodAnnotation() {
      return Provides.class;
    }

    @Override
    public void generate(ExecutableElement method, TypeElement moduleElement, Messager messager) {
      factoryGenerator.generate(
          provisionBindingFactory.forProvidesMethod(method, moduleElement), messager);
    }
  }

  private static final class ProductionModuleMethodFactoryGenerator
      implements ModuleMethodFactoryGenerator {

    private final ProductionBinding.Factory productionBindingFactory;
    private final ProducerFactoryGenerator producerFactoryGenerator;

    ProductionModuleMethodFactoryGenerator(
        ProductionBinding.Factory productionBindingFactory,
        ProducerFactoryGenerator productionFactoryGenerator) {
      this.productionBindingFactory = productionBindingFactory;
      this.producerFactoryGenerator = productionFactoryGenerator;
    }

    @Override
    public Class<? extends Annotation> factoryMethodAnnotation() {
      return Produces.class;
    }

    @Override
    public void generate(ExecutableElement method, TypeElement moduleElement, Messager messager) {
      producerFactoryGenerator.generate(
          productionBindingFactory.forProducesMethod(method, moduleElement), messager);
    }
  }
}
