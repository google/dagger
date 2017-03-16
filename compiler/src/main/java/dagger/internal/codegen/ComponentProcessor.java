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

import static dagger.internal.codegen.ModuleProcessingStep.moduleProcessingStep;
import static dagger.internal.codegen.ModuleProcessingStep.producerModuleProcessingStep;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The annotation processor responsible for generating the classes that drive the Dagger 2.0
 * implementation.
 *
 * TODO(gak): give this some better documentation
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoService(Processor.class)
public final class ComponentProcessor extends BasicAnnotationProcessor {
  private InjectBindingRegistry injectBindingRegistry;
  private FactoryGenerator factoryGenerator;
  private MembersInjectorGenerator membersInjectorGenerator;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return CompilerOptions.SUPPORTED_OPTIONS;
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    CompilerOptions compilerOptions = CompilerOptions.create(processingEnv, elements);
    Filer filer =  new FormattingFiler(processingEnv.getFiler());

    KeyFormatter keyFormatter = new KeyFormatter();
    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    BindingDeclarationFormatter bindingDeclarationFormatter =
        new BindingDeclarationFormatter(methodSignatureFormatter, keyFormatter);
    DependencyRequestFormatter dependencyRequestFormatter =
        new DependencyRequestFormatter(types, elements);

    Key.Factory keyFactory = new Key.Factory(types, elements);

    InjectValidator injectValidator = new InjectValidator(types, elements, compilerOptions);
    InjectValidator injectValidatorWhenGeneratingCode = injectValidator.whenGeneratingCode();
    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements, types);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements, types);
    BindsMethodValidator bindsMethodValidator = new BindsMethodValidator(elements, types);
    MultibindsMethodValidator multibindsMethodValidator =
        new MultibindsMethodValidator(elements, types);
    BindsOptionalOfMethodValidator bindsOptionalOfMethodValidator =
        new BindsOptionalOfMethodValidator(elements, types);
    AnyBindingMethodValidator anyBindingMethodValidator =
        new AnyBindingMethodValidator(
            providesMethodValidator,
            producesMethodValidator,
            bindsMethodValidator,
            multibindsMethodValidator,
            bindsOptionalOfMethodValidator);
    ModuleValidator moduleValidator =
        new ModuleValidator(
            types,
            elements,
            anyBindingMethodValidator,
            methodSignatureFormatter);
    BuilderValidator builderValidator = new BuilderValidator(elements, types);
    ComponentValidator subcomponentValidator =
        ComponentValidator.createForSubcomponent(
            elements, types, moduleValidator, builderValidator);
    ComponentValidator componentValidator =
        ComponentValidator.createForComponent(
            elements, types, moduleValidator, subcomponentValidator, builderValidator);
    MapKeyValidator mapKeyValidator = new MapKeyValidator();

    this.factoryGenerator =
        new FactoryGenerator(filer, elements, compilerOptions, injectValidatorWhenGeneratingCode);
    this.membersInjectorGenerator =
        new MembersInjectorGenerator(filer, elements, injectValidatorWhenGeneratingCode);
    ComponentGenerator componentGenerator =
        new ComponentGenerator(filer, elements, types, keyFactory, compilerOptions);
    ProducerFactoryGenerator producerFactoryGenerator =
        new ProducerFactoryGenerator(filer, elements, compilerOptions);
    MonitoringModuleGenerator monitoringModuleGenerator =
        new MonitoringModuleGenerator(filer, elements);
    ProductionExecutorModuleGenerator productionExecutorModuleGenerator =
        new ProductionExecutorModuleGenerator(filer, elements);

    DependencyRequest.Factory dependencyRequestFactory =
        new DependencyRequest.Factory(keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(elements, types, keyFactory);
    SubcomponentDeclaration.Factory subcomponentDeclarationFactory =
        new SubcomponentDeclaration.Factory(keyFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    DelegateDeclaration.Factory bindingDelegateDeclarationFactory =
        new DelegateDeclaration.Factory(types, keyFactory, dependencyRequestFactory);
    OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory =
        new OptionalBindingDeclaration.Factory(keyFactory);

    this.injectBindingRegistry =
        new InjectBindingRegistry(
            elements,
            types,
            messager,
            injectValidator,
            keyFactory,
            provisionBindingFactory,
            membersInjectionBindingFactory,
            compilerOptions);

    ModuleDescriptor.Factory moduleDescriptorFactory =
        new ModuleDescriptor.Factory(
            elements,
            provisionBindingFactory,
            productionBindingFactory,
            multibindingDeclarationFactory,
            bindingDelegateDeclarationFactory,
            subcomponentDeclarationFactory,
            optionalBindingDeclarationFactory);

    ComponentDescriptor.Factory componentDescriptorFactory = new ComponentDescriptor.Factory(
        elements, types, dependencyRequestFactory, moduleDescriptorFactory);

    BindingGraph.Factory bindingGraphFactory =
        new BindingGraph.Factory(
            elements,
            injectBindingRegistry,
            keyFactory,
            provisionBindingFactory,
            productionBindingFactory);

    AnnotationCreatorGenerator annotationCreatorGenerator =
        new AnnotationCreatorGenerator(filer, elements);
    UnwrappedMapKeyGenerator unwrappedMapKeyGenerator =
        new UnwrappedMapKeyGenerator(filer, elements);
    CanReleaseReferencesValidator canReleaseReferencesValidator =
        new CanReleaseReferencesValidator();
    ComponentHierarchyValidator componentHierarchyValidator =
        new ComponentHierarchyValidator(compilerOptions, elements);
    BindingGraphValidator bindingGraphValidator =
        new BindingGraphValidator(
            elements,
            types,
            compilerOptions,
            injectValidatorWhenGeneratingCode,
            injectBindingRegistry,
            bindingDeclarationFormatter,
            methodSignatureFormatter,
            dependencyRequestFormatter,
            keyFormatter,
            keyFactory);

    return ImmutableList.of(
        new MapKeyProcessingStep(
            messager, types, mapKeyValidator, annotationCreatorGenerator, unwrappedMapKeyGenerator),
        new ForReleasableReferencesValidator(messager),
        new CanReleaseReferencesProcessingStep(
            messager, canReleaseReferencesValidator, annotationCreatorGenerator),
        new InjectProcessingStep(injectBindingRegistry),
        new MonitoringModuleProcessingStep(messager, monitoringModuleGenerator),
        new ProductionExecutorModuleProcessingStep(messager, productionExecutorModuleGenerator),
        new MultibindingAnnotationsProcessingStep(messager),
        new BindsInstanceProcessingStep(messager),
        moduleProcessingStep(messager, moduleValidator, provisionBindingFactory, factoryGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator),
        producerModuleProcessingStep(
            messager,
            moduleValidator,
            provisionBindingFactory,
            factoryGenerator,
            productionBindingFactory,
            producerFactoryGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.PRODUCTION_COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator),
        new BindingMethodProcessingStep(messager, anyBindingMethodValidator));
  }

  @Override
  protected void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      try {
        injectBindingRegistry.generateSourcesForRequiredBindings(
            factoryGenerator, membersInjectorGenerator);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(processingEnv.getMessager());
      }
    }
  }
}
