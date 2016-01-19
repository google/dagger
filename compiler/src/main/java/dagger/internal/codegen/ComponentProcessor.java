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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static javax.tools.Diagnostic.Kind.ERROR;

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
    return ImmutableSet.of(
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        NULLABLE_VALIDATION_KEY,
        PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
        STATIC_MEMBER_VALIDATION_TYPE_KEY
    );
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    Filer filer = processingEnv.getFiler();

    Diagnostic.Kind nullableDiagnosticType =
        nullableValidationType(processingEnv).diagnosticKind().get();

    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    HasSourceElementFormatter hasSourceElementFormatter =
        new HasSourceElementFormatter(methodSignatureFormatter);
    DependencyRequestFormatter dependencyRequestFormatter = new DependencyRequestFormatter(types);
    KeyFormatter keyFormatter = new KeyFormatter();

    InjectConstructorValidator injectConstructorValidator = new InjectConstructorValidator();
    InjectFieldValidator injectFieldValidator = new InjectFieldValidator(
        privateMemberValidationType(processingEnv).diagnosticKind().get(),
        staticMemberValidationType(processingEnv).diagnosticKind().get());
    InjectMethodValidator injectMethodValidator = new InjectMethodValidator(
        privateMemberValidationType(processingEnv).diagnosticKind().get(),
        staticMemberValidationType(processingEnv).diagnosticKind().get());
    MembersInjectedTypeValidator membersInjectedTypeValidator =
        new MembersInjectedTypeValidator(injectFieldValidator, injectMethodValidator);
    ModuleValidator moduleValidator =
        new ModuleValidator(types, elements, methodSignatureFormatter);
    BuilderValidator builderValidator = new BuilderValidator(elements, types);
    ComponentValidator subcomponentValidator =
        ComponentValidator.createForSubcomponent(
            elements, types, moduleValidator, builderValidator);
    ComponentValidator componentValidator =
        ComponentValidator.createForComponent(
            elements, types, moduleValidator, subcomponentValidator, builderValidator);
    MapKeyValidator mapKeyValidator = new MapKeyValidator();
    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements, types);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements, types);

    Key.Factory keyFactory = new Key.Factory(types, elements);

    MultibindingsValidator multibindingsValidator =
        new MultibindingsValidator(elements, keyFactory, keyFormatter, methodSignatureFormatter);

    this.factoryGenerator =
        new FactoryGenerator(
            filer, elements, DependencyRequestMapper.FOR_PROVIDER, nullableDiagnosticType);
    this.membersInjectorGenerator =
        new MembersInjectorGenerator(filer, elements, DependencyRequestMapper.FOR_PROVIDER);
    ComponentGenerator componentGenerator =
        new ComponentGenerator(filer, elements, types, keyFactory, nullableDiagnosticType);
    ProducerFactoryGenerator producerFactoryGenerator =
        new ProducerFactoryGenerator(filer, elements, DependencyRequestMapper.FOR_PRODUCER);
    MonitoringModuleGenerator monitoringModuleGenerator =
        new MonitoringModuleGenerator(filer, elements);

    DependencyRequest.Factory dependencyRequestFactory =
        new DependencyRequest.Factory(elements, keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(elements, types, keyFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    this.injectBindingRegistry =
        new InjectBindingRegistry(
            elements,
            types,
            messager,
            injectConstructorValidator,
            membersInjectedTypeValidator,
            keyFactory,
            provisionBindingFactory,
            membersInjectionBindingFactory);

    ModuleDescriptor.Factory moduleDescriptorFactory =
        new ModuleDescriptor.Factory(
            elements,
            provisionBindingFactory,
            productionBindingFactory,
            multibindingDeclarationFactory);

    ComponentDescriptor.Factory componentDescriptorFactory = new ComponentDescriptor.Factory(
        elements, types, dependencyRequestFactory, moduleDescriptorFactory);

    BindingGraph.Factory bindingGraphFactory =
        new BindingGraph.Factory(
            elements,
            injectBindingRegistry,
            keyFactory,
            provisionBindingFactory,
            productionBindingFactory);

    MapKeyGenerator mapKeyGenerator = new MapKeyGenerator(filer, elements);
    ComponentHierarchyValidator componentHierarchyValidator = new ComponentHierarchyValidator();
    BindingGraphValidator bindingGraphValidator =
        new BindingGraphValidator(
            types,
            injectBindingRegistry,
            scopeValidationType(processingEnv),
            nullableDiagnosticType,
            hasSourceElementFormatter,
            methodSignatureFormatter,
            dependencyRequestFormatter,
            keyFormatter,
            keyFactory);

    return ImmutableList.of(
        new MapKeyProcessingStep(messager, types, mapKeyValidator, mapKeyGenerator),
        new InjectProcessingStep(injectBindingRegistry),
        new MonitoringModuleProcessingStep(messager, monitoringModuleGenerator),
        new MultibindingsProcessingStep(messager, multibindingsValidator),
        new ModuleProcessingStep(
            messager,
            moduleValidator,
            providesMethodValidator,
            provisionBindingFactory,
            factoryGenerator),
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
        new ProducerModuleProcessingStep(
            messager,
            moduleValidator,
            producesMethodValidator,
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
            componentGenerator));
  }

  @Override
  protected void postProcess() {
    try {
      injectBindingRegistry.generateSourcesForRequiredBindings(
          factoryGenerator, membersInjectorGenerator);
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(processingEnv.getMessager());
    }
  }

  private static final String DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY =
      "dagger.disableInterComponentScopeValidation";

  private static final String NULLABLE_VALIDATION_KEY = "dagger.nullableValidation";

  private static final String PRIVATE_MEMBER_VALIDATION_TYPE_KEY =
      "dagger.privateMemberValidation";

  private static final String STATIC_MEMBER_VALIDATION_TYPE_KEY =
      "dagger.staticMemberValidation";

  private static ValidationType scopeValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(processingEnv,
        DISABLE_INTER_COMPONENT_SCOPE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.allOf(ValidationType.class));
  }

  private static ValidationType nullableValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(processingEnv,
        NULLABLE_VALIDATION_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType privateMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(processingEnv,
        PRIVATE_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static ValidationType staticMemberValidationType(ProcessingEnvironment processingEnv) {
    return valueOf(processingEnv,
        STATIC_MEMBER_VALIDATION_TYPE_KEY,
        ValidationType.ERROR,
        EnumSet.of(ValidationType.ERROR, ValidationType.WARNING));
  }

  private static <T extends Enum<T>> T valueOf(ProcessingEnvironment processingEnv, String key,
      T defaultValue, Set<T> validValues) {
    Map<String, String> options = processingEnv.getOptions();
    if (options.containsKey(key)) {
      try {
        T type = Enum.valueOf(
            defaultValue.getDeclaringClass(),
            Ascii.toUpperCase(options.get(key)));
        if (!validValues.contains(type)) {
          throw new IllegalArgumentException(); // let handler below print out good msg.
        }
        return type;
      } catch (IllegalArgumentException e) {
        processingEnv.getMessager().printMessage(ERROR, "Processor option -A"
            + key + " may only have the values " + validValues
            + " (case insensitive), found: " + options.get(key));
      }
    }
    return defaultValue;
  }
}
