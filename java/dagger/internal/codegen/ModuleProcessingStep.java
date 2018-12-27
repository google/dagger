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
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.DelegateDeclaration.Factory;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final BindingFactory bindingFactory;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<ProductionBinding> producerFactoryGenerator;
  private final SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator;
  private final InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator;
  private final DelegateDeclaration.Factory delegateDeclarationFactory;
  private final Set<TypeElement> processedModuleElements = Sets.newLinkedHashSet();

  @Inject
  ModuleProcessingStep(
      Messager messager,
      ModuleValidator moduleValidator,
      BindingFactory bindingFactory,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<ProductionBinding> producerFactoryGenerator,
      @ModuleGenerator SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator,
      InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator,
      Factory delegateDeclarationFactory) {
    super(MoreElements::asType);
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.bindingFactory = bindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.producerFactoryGenerator = producerFactoryGenerator;
    this.moduleConstructorProxyGenerator = moduleConstructorProxyGenerator;
    this.inaccessibleMapKeyProxyGenerator = inaccessibleMapKeyProxyGenerator;
    this.delegateDeclarationFactory = delegateDeclarationFactory;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Module.class, ProducerModule.class);
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    List<TypeElement> modules = typesIn(elementsByAnnotation.values());
    moduleValidator.addKnownModules(modules);
    return super.process(elementsByAnnotation);
  }

  @Override
  protected void process(
      TypeElement module, ImmutableSet<Class<? extends Annotation>> annotations) {
    if (processedModuleElements.contains(module)) {
      return;
    }
    ValidationReport<TypeElement> report = moduleValidator.validate(module);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      for (ExecutableElement method : methodsIn(module.getEnclosedElements())) {
        if (isAnnotationPresent(method, Provides.class)) {
          generate(factoryGenerator, bindingFactory.providesMethodBinding(method, module));
        } else if (isAnnotationPresent(method, Produces.class)) {
          generate(producerFactoryGenerator, bindingFactory.producesMethodBinding(method, module));
        } else if (isAnnotationPresent(method, Binds.class)) {
          inaccessibleMapKeyProxyGenerator.generate(bindsMethodBinding(module, method), messager);
        }
      }
      moduleConstructorProxyGenerator.generate(module, messager);
    }
    processedModuleElements.add(module);
  }

  private <B extends ContributionBinding> void generate(
      SourceFileGenerator<B> generator, B binding) {
    generator.generate(binding, messager);
    inaccessibleMapKeyProxyGenerator.generate(binding, messager);
  }

  private ContributionBinding bindsMethodBinding(TypeElement module, ExecutableElement method) {
    return bindingFactory.unresolvedDelegateBinding(
        delegateDeclarationFactory.create(method, module));
  }
}
