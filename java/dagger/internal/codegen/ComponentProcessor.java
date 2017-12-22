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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;

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
  private ImmutableList<BindingGraphPlugin> bindingGraphPlugins;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    ImmutableSet.Builder<String> options = ImmutableSet.builder();
    options.addAll(CompilerOptions.SUPPORTED_OPTIONS);
    for (BindingGraphPlugin plugin : bindingGraphPlugins) {
      options.addAll(plugin.getSupportedOptions());
    }
    return options.build();
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    DaggerTypes types = new DaggerTypes(processingEnv);
    DaggerElements elements = new DaggerElements(processingEnv);
    CompilerOptions compilerOptions = CompilerOptions.create(processingEnv, elements);

    Filer filer;
    if (compilerOptions.headerCompilation()) {
      filer = processingEnv.getFiler();
    } else {
      filer = new FormattingFiler(processingEnv.getFiler());
    }

    this.bindingGraphPlugins =
        ImmutableList.copyOf(
            ServiceLoader.load(BindingGraphPlugin.class, getClass().getClassLoader()));
    for (BindingGraphPlugin plugin : bindingGraphPlugins) {
      plugin.setFiler(filer);
      Set<String> supportedOptions = plugin.getSupportedOptions();
      if (!supportedOptions.isEmpty()) {
        plugin.setOptions(Maps.filterKeys(processingEnv.getOptions(), supportedOptions::contains));
      }
    }

    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    BindingDeclarationFormatter bindingDeclarationFormatter =
        new BindingDeclarationFormatter(methodSignatureFormatter);
    DependencyRequestFormatter dependencyRequestFormatter = new DependencyRequestFormatter(types);

    KeyFactory keyFactory = new KeyFactory(types, elements);

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
    MapKeyValidator mapKeyValidator = new MapKeyValidator(elements);

    DependencyRequestFactory dependencyRequestFactory =
        new DependencyRequestFactory(keyFactory, types);
    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(
            types, keyFactory, dependencyRequestFactory, membersInjectionBindingFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(types, keyFactory);
    SubcomponentDeclaration.Factory subcomponentDeclarationFactory =
        new SubcomponentDeclaration.Factory(keyFactory);

    this.factoryGenerator = new FactoryGenerator(filer, elements, types, compilerOptions);
    this.membersInjectorGenerator = new MembersInjectorGenerator(filer, elements, types);
    ComponentGenerator componentGenerator =
        new ComponentGenerator(filer, elements, types, keyFactory, compilerOptions);
    ProducerFactoryGenerator producerFactoryGenerator =
        new ProducerFactoryGenerator(filer, elements, types, compilerOptions);
    MonitoringModuleGenerator monitoringModuleGenerator =
        new MonitoringModuleGenerator(filer, elements);
    ProductionExecutorModuleGenerator productionExecutorModuleGenerator =
        new ProductionExecutorModuleGenerator(filer, elements);

    DelegateDeclaration.Factory bindingDelegateDeclarationFactory =
        new DelegateDeclaration.Factory(types, keyFactory, dependencyRequestFactory);
    OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory =
        new OptionalBindingDeclaration.Factory(keyFactory);

    this.injectBindingRegistry =
        new InjectBindingRegistryImpl(
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
        new ComponentHierarchyValidator(compilerOptions);
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
            keyFactory);

    ProcessingStep componentProcessingStep =
        compilerOptions.headerCompilation()
            ? new ComponentHjarProcessingStep(
                elements, types, filer, messager, componentValidator, componentDescriptorFactory)
            : new ComponentProcessingStep(
                messager,
                componentValidator,
                subcomponentValidator,
                builderValidator,
                componentHierarchyValidator,
                bindingGraphValidator,
                componentDescriptorFactory,
                bindingGraphFactory,
                componentGenerator,
                bindingGraphPlugins);
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
        producerModuleProcessingStep(
            messager,
            moduleValidator,
            provisionBindingFactory,
            factoryGenerator,
            productionBindingFactory,
            producerFactoryGenerator),
        componentProcessingStep,
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
