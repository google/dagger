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

package dagger.internal.codegen.writing;

import static androidx.room.compiler.codegen.XTypeNameKt.toJavaPoet;
import static androidx.room.compiler.codegen.XTypeNameKt.toKotlinPoet;
import static androidx.room.compiler.codegen.compat.XConverters.toXPoet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameters;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.generatedProxyMethodName;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.PROVISION;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.writing.InjectionMethods.copyParameter;
import static dagger.internal.codegen.writing.InjectionMethods.copyParameters;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.internal.codegen.xprocessing.XTypeNames.factoryOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.Preconditions;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.writing.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Generates factory implementation for injection, assisted injection, and provision bindings. */
public final class FactoryGenerator extends SourceFileGenerator<ContributionBinding> {
  private static final ImmutableSet<BindingKind> VALID_BINDING_KINDS =
      ImmutableSet.of(BindingKind.INJECTION, BindingKind.ASSISTED_INJECTION, BindingKind.PROVISION);

  private final CompilerOptions compilerOptions;
  private final SourceFiles sourceFiles;

  @Inject
  FactoryGenerator(
      XFiler filer,
      CompilerOptions compilerOptions,
      SourceFiles sourceFiles,
      XProcessingEnv processingEnv) {
    super(filer, processingEnv);
    this.compilerOptions = compilerOptions;
    this.sourceFiles = sourceFiles;
  }

  @Override
  public XElement originatingElement(ContributionBinding binding) {
    // we only create factories for bindings that have a binding element
    return binding.bindingElement().get();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(ContributionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());
    checkArgument(VALID_BINDING_KINDS.contains(binding.kind()));

    return ImmutableList.of(factoryBuilder(binding));
  }

  private TypeSpec.Builder factoryBuilder(ContributionBinding binding) {
    TypeSpec.Builder factoryBuilder =
        classBuilder(toJavaPoet(generatedClassNameForBinding(binding)))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(
               bindingTypeElementTypeVariableNames(binding).stream()
                    .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
                    .collect(toImmutableList()))
            .addAnnotation(scopeMetadataAnnotation(binding))
            .addAnnotation(qualifierMetadataAnnotation(binding));

    factoryTypeName(binding)
        .map(XConverters::toJavaPoet)
        .ifPresent(factoryBuilder::addSuperinterface);
    FactoryFields factoryFields = FactoryFields.create(binding);
    // If the factory has no input fields we can use a static instance holder to create a
    // singleton instance of the factory. Otherwise, we create a new instance via the constructor.
    if (factoryFields.isEmpty()) {
      factoryBuilder.addType(staticInstanceHolderType(binding));
    } else {
      factoryBuilder
          .addFields(factoryFields.getAll())
          .addMethod(constructorMethod(factoryFields));
    }
    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    return factoryBuilder
        .addMethod(getMethod(binding, factoryFields))
        .addMethod(staticCreateMethod(binding, factoryFields))
        .addMethod(staticProxyMethod(binding));
  }

  // private static final class InstanceHolder {
  //   static final FooModule_ProvidesFooFactory INSTANCE =
  //       new FooModule_ProvidesFooFactory();
  // }
  private TypeSpec staticInstanceHolderType(ContributionBinding binding) {
    ClassName generatedClassName = toJavaPoet(generatedClassNameForBinding(binding));
    FieldSpec.Builder instanceHolderFieldBuilder =
        FieldSpec.builder(generatedClassName, "INSTANCE", STATIC, FINAL)
            .initializer("new $T()", generatedClassName);
    if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
      // If the factory has type parameters, ignore them in the field declaration & initializer
      instanceHolderFieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
    }
    return TypeSpec.classBuilder(instanceHolderClassName(binding))
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addField(instanceHolderFieldBuilder.build())
        .build();
  }

  private static ClassName instanceHolderClassName(ContributionBinding binding) {
    return toJavaPoet(generatedClassNameForBinding(binding).nestedClass("InstanceHolder"));
  }

  // public FooModule_ProvidesFooFactory(
  //     FooModule module,
  //     Provider<Bar> barProvider,
  //     Provider<Baz> bazProvider) {
  //   this.module = module;
  //   this.barProvider = barProvider;
  //   this.bazProvider = bazProvider;
  // }
  private MethodSpec constructorMethod(FactoryFields factoryFields) {
    // TODO(bcorso): Make the constructor private?
    MethodSpec.Builder constructor = constructorBuilder().addModifiers(PUBLIC);
    factoryFields.getAll().forEach(
        field ->
            constructor
                .addParameter(field.type, field.name)
                .addStatement("this.$1N = $1N", field));
    return constructor.build();
  }

  // Example 1: no dependencies.
  // public static FooModule_ProvidesFooFactory create() {
  //   return InstanceHolder.INSTANCE;
  // }
  //
  // Example 2: with dependencies.
  // public static FooModule_ProvidesFooFactory create(
  //     FooModule module,
  //     Provider<Bar> barProvider,
  //     Provider<Baz> bazProvider) {
  //   return new FooModule_ProvidesFooFactory(module, barProvider, bazProvider);
  // }
  private MethodSpec staticCreateMethod(
      ContributionBinding binding, FactoryFields factoryFields) {
    // We use a static create method so that generated components can avoid having to refer to the
    // generic types of the factory.  (Otherwise they may have visibility problems referring to the
    // types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(toJavaPoet(parameterizedGeneratedTypeNameForBinding(binding)))
            .addTypeVariables(
                bindingTypeElementTypeVariableNames(binding).stream()
                    .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
                    .collect(toImmutableList()));

    if (factoryFields.isEmpty()) {
      if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
        createMethodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
      }
      createMethodBuilder.addStatement("return $T.INSTANCE", instanceHolderClassName(binding));
    } else {
      ImmutableList<ParameterSpec> parameters =
          factoryFields.getAll().stream()
              .map(field -> ParameterSpec.builder(field.type, field.name).build())
              .collect(toImmutableList());
      createMethodBuilder
          .addParameters(parameters)
          .addStatement(
              "return new $T($L)",
              toJavaPoet(parameterizedGeneratedTypeNameForBinding(binding)),
              parameterNames(parameters));
    }
    return createMethodBuilder.build();
  }

  // Example 1: Provision binding.
  // @Override
  // public Foo get() {
  //   return provideFoo(module, barProvider.get(), bazProvider.get());
  // }
  //
  // Example 2: Injection binding with some inject field.
  // @Override
  // public Foo get() {
  //   Foo instance = newInstance(barProvider.get(), bazProvider.get());
  //   Foo_MembersInjector.injectSomeField(instance, someFieldProvider.get());
  //   return instance;
  // }
  private MethodSpec getMethod(ContributionBinding binding, FactoryFields factoryFields) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    factoryFields.getAll().forEach(field -> uniqueFieldNames.claim(field.name));
    ImmutableMap<XExecutableParameterElement, ParameterSpec> assistedParameters =
        assistedParameters(binding).stream()
            .collect(
                toImmutableMap(
                    parameter -> parameter,
                    parameter ->
                        ParameterSpec.builder(
                                parameter.getType().getTypeName(),
                                uniqueFieldNames.getUniqueName(parameter.getJvmName()))
                            .build()));
    XTypeName providedTypeName = providedTypeName(binding);
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addModifiers(PUBLIC)
            .addParameters(assistedParameters.values());

    if (factoryTypeName(binding).isPresent()) {
      getMethod.addAnnotation(Override.class);
    }
    CodeBlock invokeNewInstance =
        ProvisionMethod.invoke(
            binding,
            request ->
                sourceFiles.frameworkTypeUsageStatement(
                    CodeBlock.of("$N", factoryFields.get(request)), request.kind()),
            param -> assistedParameters.get(param).name,
            generatedClassNameForBinding(binding),
            factoryFields.moduleField.map(module -> CodeBlock.of("$N", module)),
            compilerOptions);

    if (binding.kind().equals(PROVISION)) {
      binding.nullability().nonTypeUseNullableAnnotations().stream()
          .map(XConverters::toJavaPoet)
          .forEach(getMethod::addAnnotation);
      getMethod
          .addStatement("return $L", invokeNewInstance)
          .returns(toJavaPoet(providedTypeName));
    } else if (!injectionSites(binding).isEmpty()) {
      CodeBlock instance = CodeBlock.of("instance");
      getMethod
          .returns(toJavaPoet(providedTypeName))
          .addStatement(
              "$T $L = $L",
              toJavaPoet(providedTypeName),
              instance,
              invokeNewInstance)
          .addCode(
              InjectionSiteMethod.invokeAll(
                  injectionSites(binding),
                  generatedClassNameForBinding(binding),
                  instance,
                  binding.key().type().xprocessing(),
                  sourceFiles.frameworkFieldUsages(
                      binding.dependencies(), factoryFields.frameworkFields)::get))
          .addStatement("return $L", instance);

    } else {
      getMethod
          .returns(toJavaPoet(providedTypeName))
          .addStatement("return $L", invokeNewInstance);
    }
    return getMethod.build();
  }

  private MethodSpec staticProxyMethod(ContributionBinding binding) {
    switch (binding.kind()) {
      case INJECTION:
      case ASSISTED_INJECTION:
        return staticProxyMethodForInjection(binding);
      case PROVISION:
        return staticProxyMethodForProvision((ProvisionBinding) binding);
      default:
        throw new AssertionError("Unexpected binding kind: " + binding);
    }
  }

  // Example:
  //
  // public static Foo newInstance(Bar bar, Baz baz) {
  //   return new Foo(bar, baz);
  // }
  private static MethodSpec staticProxyMethodForInjection(ContributionBinding binding) {
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XTypeElement enclosingType = constructor.getEnclosingElement();
    MethodSpec.Builder builder =
        methodBuilder(generatedProxyMethodName(binding))
            .addModifiers(PUBLIC, STATIC)
            .varargs(constructor.isVarArgs())
            .returns(enclosingType.getType().getTypeName())
            .addTypeVariables(
                typeVariableNames(enclosingType).stream()
                    .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
                    .collect(toImmutableList()))
            .addExceptions(
                getThrownTypes(constructor).stream()
                    .map(XConverters::toJavaPoet)
                    .collect(toImmutableList()));
    CodeBlock arguments = copyParameters(builder, new UniqueNameSet(), constructor.getParameters());
    return builder
        .addStatement("return new $T($L)", enclosingType.getType().getTypeName(), arguments)
        .build();
  }

  // Example:
  //
  // public static Foo provideFoo(FooModule module, Bar bar, Baz baz) {
  //   return Preconditions.checkNotNullFromProvides(module.provideFoo(bar, baz));
  // }
  private MethodSpec staticProxyMethodForProvision(ProvisionBinding binding) {
    XMethodElement method = asMethod(binding.bindingElement().get());
    MethodSpec.Builder builder =
        methodBuilder(generatedProxyMethodName(binding))
            .addModifiers(PUBLIC, STATIC)
            .varargs(method.isVarArgs())
            .addExceptions(
                getThrownTypes(method).stream()
                    .map(XConverters::toJavaPoet)
                    .collect(toImmutableList()));

    XTypeElement enclosingType = asTypeElement(method.getEnclosingElement());
    UniqueNameSet parameterNameSet = new UniqueNameSet();
    CodeBlock module;
    if (method.isStatic() || enclosingType.isCompanionObject()) {
      module = CodeBlock.of("$T", enclosingType.getClassName());
    } else if (enclosingType.isKotlinObject()) {
      // Call through the singleton instance.
      // See: https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#static-methods
      module = CodeBlock.of("$T.INSTANCE", enclosingType.getClassName());
    } else {
      builder.addTypeVariables(
          typeVariableNames(enclosingType).stream()
              .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
              .collect(toImmutableList()));
      module = copyInstance(builder, parameterNameSet, enclosingType.getType());
    }
    CodeBlock arguments = copyParameters(builder, parameterNameSet, method.getParameters());
    CodeBlock invocation = CodeBlock.of("$L.$L($L)", module, method.getJvmName(), arguments);

    Nullability nullability = Nullability.of(method);
    nullability.nonTypeUseNullableAnnotations().stream()
        .map(XConverters::toJavaPoet)
        .forEach(builder::addAnnotation);
    return builder
        .returns(
            method.getReturnType().getTypeName()
                .annotated(
                    nullability.typeUseNullableAnnotations().stream()
                        .map(XConverters::toJavaPoet)
                        .map(annotation -> AnnotationSpec.builder(annotation).build())
                        .collect(toImmutableList())))
        .addStatement("return $L", maybeWrapInCheckForNull(binding, invocation))
        .build();
  }

  private CodeBlock maybeWrapInCheckForNull(ProvisionBinding binding, CodeBlock codeBlock) {
    return binding.shouldCheckForNull(compilerOptions)
        ? CodeBlock.of("$T.checkNotNullFromProvides($L)", Preconditions.class, codeBlock)
        : codeBlock;
  }

  private static CodeBlock copyInstance(
      MethodSpec.Builder methodBuilder, UniqueNameSet parameterNameSet, XType type) {
    return copyParameter(
        methodBuilder,
        type,
        parameterNameSet.getUniqueName("instance"),
        /* useObject= */ false,
        Nullability.NOT_NULLABLE);
  }

  private static ImmutableList<XTypeName> getThrownTypes(XExecutableElement executable) {
    return executable.getThrownTypes().stream().map(XType::asTypeName).collect(toImmutableList());
  }

  private AnnotationSpec scopeMetadataAnnotation(ContributionBinding binding) {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(toJavaPoet(XTypeNames.SCOPE_METADATA));
    binding.scope()
        .map(Scope::scopeAnnotation)
        .map(DaggerAnnotation::xprocessing)
        .map(XAnnotation::getQualifiedName)
        .ifPresent(scopeCanonicalName -> builder.addMember("value", "$S", scopeCanonicalName));
    return builder.build();
  }

  private AnnotationSpec qualifierMetadataAnnotation(ContributionBinding binding) {
    AnnotationSpec.Builder builder =
        AnnotationSpec.builder(toJavaPoet(XTypeNames.QUALIFIER_METADATA));
    // Collect all qualifiers on the binding itself or its dependencies. For injection bindings, we
    // don't include the injection sites, as that is handled by MembersInjectorFactory.
    Stream.concat(
            Stream.of(binding.key()),
            provisionDependencies(binding).stream().map(DependencyRequest::key))
        .map(Key::qualifier)
        .flatMap(presentValues())
        .map(DaggerAnnotation::xprocessing)
        .map(XAnnotation::getQualifiedName)
        .distinct()
        .forEach(qualifier -> builder.addMember("value", "$S", qualifier));
    return builder.build();
  }

  private ImmutableSet<DependencyRequest> provisionDependencies(ContributionBinding binding) {
    switch (binding.kind()) {
      case INJECTION:
        return ((InjectionBinding) binding).constructorDependencies();
      case ASSISTED_INJECTION:
        return ((AssistedInjectionBinding) binding).constructorDependencies();
      case PROVISION:
        return ((ProvisionBinding) binding).dependencies();
      default:
        throw new AssertionError("Unexpected binding kind: " + binding.kind());
    }
  }

  private ImmutableSet<InjectionSite> injectionSites(ContributionBinding binding) {
    switch (binding.kind()) {
      case INJECTION:
        return ((InjectionBinding) binding).injectionSites();
      case ASSISTED_INJECTION:
        return ((AssistedInjectionBinding) binding).injectionSites();
      case PROVISION:
        return ImmutableSet.of();
      default:
        throw new AssertionError("Unexpected binding kind: " + binding.kind());
    }
  }

  private static XTypeName providedTypeName(ContributionBinding binding) {
    XTypeName typeName = binding.contributedType().asTypeName();
    return toXPoet(
        toJavaPoet(typeName)
            .annotated(
                binding.nullability().typeUseNullableAnnotations().stream()
                    .map(XConverters::toJavaPoet)
                    .map(annotation -> AnnotationSpec.builder(annotation).build())
                    .collect(toImmutableList())),
        toKotlinPoet(typeName));
  }

  private static Optional<XTypeName> factoryTypeName(ContributionBinding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? Optional.empty()
        : Optional.of(factoryOf(providedTypeName(binding)));
  }

  /** Represents the available fields in the generated factory class. */
  private static final class FactoryFields {
    static FactoryFields create(ContributionBinding binding) {
      UniqueNameSet nameSet = new UniqueNameSet();
      // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
      Optional<FieldSpec> moduleField =
          binding.requiresModuleInstance()
              ? Optional.of(
                  createField(
                      binding.bindingTypeElement().get().getType().asTypeName(),
                      nameSet.getUniqueName("module")))
              : Optional.empty();

      ImmutableMap.Builder<DependencyRequest, FieldSpec> frameworkFields = ImmutableMap.builder();
      generateBindingFieldsForDependencies(binding).forEach(
          (dependency, field) ->
              frameworkFields.put(
                  dependency,
                  createField(field.type(), nameSet.getUniqueName(field.name()))));

      return new FactoryFields(moduleField, frameworkFields.buildOrThrow());
    }

    private static FieldSpec createField(XTypeName type, String name) {
      return FieldSpec.builder(toJavaPoet(type), name, PRIVATE, FINAL).build();
    }

    private final Optional<FieldSpec> moduleField;
    private final ImmutableMap<DependencyRequest, FieldSpec> frameworkFields;

    private FactoryFields(
        Optional<FieldSpec> moduleField,
        ImmutableMap<DependencyRequest, FieldSpec> frameworkFields) {
      this.moduleField = moduleField;
      this.frameworkFields = frameworkFields;
    }

    FieldSpec get(DependencyRequest request) {
      return frameworkFields.get(request);
    }

    ImmutableList<FieldSpec> getAll() {
      return moduleField.isPresent()
          ? ImmutableList.<FieldSpec>builder()
              .add(moduleField.get())
              .addAll(frameworkFields.values())
              .build()
          : frameworkFields.values().asList();
    }

    boolean isEmpty() {
      return getAll().isEmpty();
    }
  }
}
