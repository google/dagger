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

import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.DelegateDeclaration;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.ModuleValidator;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.writing.InaccessibleMapKeyProxyGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep extends TypeCheckingProcessingStep<XTypeElement> {
  private final XMessager messager;
  private final ModuleValidator moduleValidator;
  private final BindingFactory bindingFactory;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<ProductionBinding> producerFactoryGenerator;
  private final SourceFileGenerator<XTypeElement> moduleConstructorProxyGenerator;
  private final InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator;
  private final DelegateDeclaration.Factory delegateDeclarationFactory;
  private final Set<XTypeElement> processedModuleElements = Sets.newLinkedHashSet();

  @Inject
  ModuleProcessingStep(
      XMessager messager,
      ModuleValidator moduleValidator,
      BindingFactory bindingFactory,
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<ProductionBinding> producerFactoryGenerator,
      @ModuleGenerator SourceFileGenerator<XTypeElement> moduleConstructorProxyGenerator,
      InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator,
      DelegateDeclaration.Factory delegateDeclarationFactory) {
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
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.MODULE, TypeNames.PRODUCER_MODULE);
  }

  @Override
  public ImmutableSet<XElement> process(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    moduleValidator.addKnownModules(
        elementsByAnnotation.values().stream()
            .flatMap(Set::stream)
            // This cast is safe because @Module has @Target(ElementType.TYPE)
            .map(XTypeElement.class::cast)
            .collect(toImmutableSet()));
    return super.process(env, elementsByAnnotation);
  }

  @Override
  protected void process(XTypeElement module, ImmutableSet<ClassName> annotations) {
    if (processedModuleElements.contains(module)) {
      return;
    }
    // For backwards compatibility, we allow a companion object to be annotated with @Module even
    // though it's no longer required. However, we skip processing the companion object itself
    // because it will now be processed when processing the companion object's enclosing class.
    if (module.isCompanionObject()) {
      // TODO(danysantiago): Be strict about annotating companion objects with @Module,
      //  i.e. tell user to annotate parent instead.
      return;
    }
    ValidationReport report = moduleValidator.validate(module);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      generateForMethodsIn(module);
      module.getEnclosedTypeElements().stream()
          .filter(XTypeElement::isCompanionObject)
          .collect(toOptional())
          .ifPresent(this::generateForMethodsIn);
    }
    processedModuleElements.add(module);
  }

  private void generateForMethodsIn(XTypeElement module) {
    for (XMethodElement method : module.getDeclaredMethods()) {
      if (method.hasAnnotation(TypeNames.PROVIDES)) {
        generate(factoryGenerator, bindingFactory.providesMethodBinding(method, module));
      } else if (method.hasAnnotation(TypeNames.PRODUCES)) {
        generate(producerFactoryGenerator, bindingFactory.producesMethodBinding(method, module));
      } else if (method.hasAnnotation(TypeNames.BINDS)) {
        inaccessibleMapKeyProxyGenerator.generate(bindsMethodBinding(module, method), messager);
      }
    }
    moduleConstructorProxyGenerator.generate(module, messager);
  }

  private <B extends ContributionBinding> void generate(
      SourceFileGenerator<B> generator, B binding) {
    generator.generate(binding, messager);
    inaccessibleMapKeyProxyGenerator.generate(binding, messager);
  }

  private ContributionBinding bindsMethodBinding(XTypeElement module, XMethodElement method) {
    return bindingFactory.unresolvedDelegateBinding(
        delegateDeclarationFactory.create(method, module));
  }
}
