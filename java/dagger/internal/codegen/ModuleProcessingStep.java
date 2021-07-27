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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.methodsIn;

import androidx.room.shaded.compiler.processing.XElement;
import androidx.room.shaded.compiler.processing.XProcessingEnv;
import androidx.room.shaded.compiler.processing.XTypeElement;
import androidx.room.shaded.compiler.processing.compat.XConverters;
import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.DelegateDeclaration;
import dagger.internal.codegen.binding.DelegateDeclaration.Factory;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.validation.ModuleValidator;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.XTypeCheckingProcessingStep;
import dagger.internal.codegen.writing.InaccessibleMapKeyProxyGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * A {@link ProcessingStep} that validates module classes and generates factories for binding
 * methods.
 */
final class ModuleProcessingStep extends XTypeCheckingProcessingStep<XTypeElement> {
  private final Messager messager;
  private final ModuleValidator moduleValidator;
  private final BindingFactory bindingFactory;
  private final SourceFileGenerator<ProvisionBinding> factoryGenerator;
  private final SourceFileGenerator<ProductionBinding> producerFactoryGenerator;
  private final SourceFileGenerator<TypeElement> moduleConstructorProxyGenerator;
  private final InaccessibleMapKeyProxyGenerator inaccessibleMapKeyProxyGenerator;
  private final DelegateDeclaration.Factory delegateDeclarationFactory;
  private final KotlinMetadataUtil metadataUtil;
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
      Factory delegateDeclarationFactory,
      KotlinMetadataUtil metadataUtil) {
    this.messager = messager;
    this.moduleValidator = moduleValidator;
    this.bindingFactory = bindingFactory;
    this.factoryGenerator = factoryGenerator;
    this.producerFactoryGenerator = producerFactoryGenerator;
    this.moduleConstructorProxyGenerator = moduleConstructorProxyGenerator;
    this.inaccessibleMapKeyProxyGenerator = inaccessibleMapKeyProxyGenerator;
    this.delegateDeclarationFactory = delegateDeclarationFactory;
    this.metadataUtil = metadataUtil;
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
            .map(XConverters::toJavac)
            .map(MoreElements::asType)
            .collect(toImmutableSet()));
    return super.process(env, elementsByAnnotation);
  }

  @Override
  protected void process(XTypeElement xElement, ImmutableSet<ClassName> annotations) {
    // TODO(bcorso): Remove conversion to javac type and use XProcessing throughout.
    TypeElement module = XConverters.toJavac(xElement);
    if (processedModuleElements.contains(module)) {
      return;
    }
    // For backwards compatibility, we allow a companion object to be annotated with @Module even
    // though it's no longer required. However, we skip processing the companion object itself
    // because it will now be processed when processing the companion object's enclosing class.
    if (metadataUtil.isCompanionObjectClass(module)) {
      // TODO(danysantiago): Be strict about annotating companion objects with @Module,
      //  i.e. tell user to annotate parent instead.
      return;
    }
    ValidationReport<TypeElement> report = moduleValidator.validate(module);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      generateForMethodsIn(module);
      if (metadataUtil.hasEnclosedCompanionObject(module)) {
        generateForMethodsIn(metadataUtil.getEnclosedCompanionObject(module));
      }
    }
    processedModuleElements.add(module);
  }

  private void generateForMethodsIn(TypeElement module) {
    for (ExecutableElement method : methodsIn(module.getEnclosedElements())) {
      if (isAnnotationPresent(method, TypeNames.PROVIDES)) {
        generate(factoryGenerator, bindingFactory.providesMethodBinding(method, module));
      } else if (isAnnotationPresent(method, TypeNames.PRODUCES)) {
        generate(producerFactoryGenerator, bindingFactory.producesMethodBinding(method, module));
      } else if (isAnnotationPresent(method, TypeNames.BINDS)) {
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

  private ContributionBinding bindsMethodBinding(TypeElement module, ExecutableElement method) {
    return bindingFactory.unresolvedDelegateBinding(
        delegateDeclarationFactory.create(method, module));
  }
}
