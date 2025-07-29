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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameters;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.generatedProxyMethodName;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.PROVISION;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.writing.InjectionMethods.copyFrameworkParameter;
import static dagger.internal.codegen.writing.InjectionMethods.copyParameter;
import static dagger.internal.codegen.writing.InjectionMethods.copyParameters;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFromPublicApi;
import static dagger.internal.codegen.xprocessing.NullableTypeNames.asNullableTypeName;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.internal.codegen.xprocessing.XTypeNames.factoryOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XAnnotationSpec;
import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import dagger.internal.codegen.xprocessing.XFunSpecs;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
  public ImmutableList<XTypeSpec> topLevelTypes(ContributionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());
    checkArgument(VALID_BINDING_KINDS.contains(binding.kind()));

    return ImmutableList.of(factoryBuilder(binding));
  }

  private XTypeSpec factoryBuilder(ContributionBinding binding) {
    XTypeSpecs.Builder factoryBuilder =
        XTypeSpecs.classBuilder(generatedClassNameForBinding(binding))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariableNames(bindingTypeElementTypeVariableNames(binding))
            .addAnnotation(scopeMetadataAnnotation(binding))
            .addAnnotation(qualifierMetadataAnnotation(binding));

    factoryTypeName(binding).ifPresent(factoryBuilder::addSuperinterface);
    FactoryFields factoryFields = FactoryFields.create(binding, compilerOptions);
    // If the factory has no input fields we can use a static instance holder to create a
    // singleton instance of the factory. Otherwise, we create a new instance via the constructor.
    if (factoryFields.isEmpty()) {
      factoryBuilder.addType(staticInstanceHolderType(binding));
    } else {
      factoryBuilder
          .addProperties(factoryFields.getAll())
          .addFunction(constructorMethod(factoryFields));
    }
    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    return factoryBuilder
        .addFunction(getMethod(binding, factoryFields))
        .addFunction(staticCreateMethod(binding, factoryFields))
        .addFunction(staticProxyMethod(binding))
        .build();
  }

  // private static final class InstanceHolder {
  //   static final FooModule_ProvidesFooFactory INSTANCE =
  //       new FooModule_ProvidesFooFactory();
  // }
  private XTypeSpec staticInstanceHolderType(ContributionBinding binding) {
    XClassName generatedClassName = generatedClassNameForBinding(binding);
    XPropertySpecs.Builder instanceHolderFieldBuilder =
        XPropertySpecs.builder("INSTANCE", generatedClassName, STATIC, FINAL)
            .initializer(XCodeBlock.ofNewInstance(generatedClassName, ""));
    if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
      // If the factory has type parameters, ignore them in the field declaration & initializer
      instanceHolderFieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
    }
    return XTypeSpecs.classBuilder(instanceHolderClassName(binding))
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addProperty(instanceHolderFieldBuilder.build())
        .build();
  }

  private static XClassName instanceHolderClassName(ContributionBinding binding) {
    return generatedClassNameForBinding(binding).nestedClass("InstanceHolder");
  }

  // private FooModule_ProvidesFooFactory(
  //     FooModule module,
  //     Provider<Bar> barProvider,
  //     Provider<Baz> bazProvider) {
  //   this.module = module;
  //   this.barProvider = barProvider;
  //   this.bazProvider = bazProvider;
  // }
  private XFunSpec constructorMethod(FactoryFields factoryFields) {
    XFunSpecs.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
    factoryFields
        .getAll()
        .forEach(
            field ->
                constructor
                    .addParameter(field.getName(), field.getType()) // SUPPRESS_GET_NAME_CHECK
                    .addStatement("this.%1N = %1N", field));
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
  private XFunSpec staticCreateMethod(ContributionBinding binding, FactoryFields factoryFields) {
    // We use a static create method so that generated components can avoid having to refer to the
    // generic types of the factory.  (Otherwise they may have visibility problems referring to the
    // types.)
    XFunSpecs.Builder createMethodBuilder =
        methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(parameterizedGeneratedTypeNameForBinding(binding))
            .addTypeVariableNames(bindingTypeElementTypeVariableNames(binding));

    if (factoryFields.isEmpty()) {
      if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
        createMethodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
      }
      createMethodBuilder.addStatement("return %T.INSTANCE", instanceHolderClassName(binding));
    } else {
      ImmutableList.Builder<XCodeBlock> arguments = ImmutableList.builder();
      factoryFields.moduleField.ifPresent(
          module -> {
            String moduleName = module.getName(); // SUPPRESS_GET_NAME_CHECK
            XType moduleType = binding.bindingTypeElement().get().getType();
            arguments.add(
                copyParameter(
                    createMethodBuilder,
                    moduleName,
                    moduleType.asTypeName(),
                    Nullability.NOT_NULLABLE,
                    /* isTypeNameAccessible= */
                    isTypeAccessibleFromPublicApi(moduleType, compilerOptions),
                    compilerOptions));
          });
      factoryFields.frameworkFields
          .forEach(
              (dependencyRequest, field) -> {
                String parameterName = field.getName(); // SUPPRESS_GET_NAME_CHECK
                XType dependencyType = dependencyRequest.key().type().xprocessing();
                arguments.add(
                    copyFrameworkParameter(
                        createMethodBuilder,
                        parameterName,
                        field.getType(),
                        Nullability.NOT_NULLABLE,
                        /* isTypeNameAccessible= */
                        isTypeAccessibleFromPublicApi(dependencyType, compilerOptions),
                        compilerOptions));
              });
      createMethodBuilder
          .addStatement(
              "return %L",
              XCodeBlock.ofNewInstance(
                  parameterizedGeneratedTypeNameForBinding(binding),
                  "%L",
                  makeParametersCodeBlock(arguments.build())));
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
  private XFunSpec getMethod(ContributionBinding binding, FactoryFields factoryFields) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    factoryFields
        .getAll()
        .forEach(field -> uniqueFieldNames.claim(field.getName())); // SUPPRESS_GET_NAME_CHECK
    XFunSpecs.Builder getMethod =
        methodBuilder("get")
            .addModifiers(PUBLIC)
            .isOverride(factoryTypeName(binding).isPresent())
            .returns(providedTypeName(binding));
    ImmutableMap<XExecutableParameterElement, XCodeBlock> assistedParameterUsages =
        assistedParameters(binding).stream()
            .collect(
                toImmutableMap(
                    parameter -> parameter,
                    parameter ->
                        copyParameter(
                            getMethod,
                            uniqueFieldNames.getUniqueName(parameter.getJvmName()),
                            parameter.getType().asTypeName(),
                            Nullability.of(parameter),
                            /* isTypeNameAccessible= */
                            isTypeAccessibleFromPublicApi(parameter.getType(), compilerOptions),
                            compilerOptions)));

    XCodeBlock invokeNewInstance =
        ProvisionMethod.invoke(
            binding,
            request ->
                sourceFiles.frameworkTypeUsageStatement(
                    XCodeBlock.of("%N", factoryFields.get(request)), request.kind()),
            assistedParameterUsages::get,
            generatedClassNameForBinding(binding),
            factoryFields.moduleField.map(module -> XCodeBlock.of("%N", module)),
            compilerOptions);

    if (binding.kind().equals(PROVISION)) {
      getMethod
          .addAnnotationNames(binding.nullability().nonTypeUseNullableAnnotations())
          .addStatement("return %L", invokeNewInstance);
    } else if (!injectionSites(binding).isEmpty()) {
      XCodeBlock instance = XCodeBlock.of("instance");
      XCodeBlock invokeInjectionSites =
          InjectionSiteMethod.invokeAll(
              injectionSites(binding),
              generatedClassNameForBinding(binding),
              instance,
              binding.key().type().xprocessing(),
              sourceFiles.frameworkFieldUsages(
                      binding.dependencies(), factoryFields.frameworkFields)
                  ::get);
      getMethod
          .addStatement("%T %L = %L", providedTypeName(binding), instance, invokeNewInstance)
          .addCode(invokeInjectionSites)
          .addStatement("return %L", instance);

    } else {
      getMethod.addStatement("return %L", invokeNewInstance);
    }
    return getMethod.build();
  }

  private XFunSpec staticProxyMethod(ContributionBinding binding) {
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
  private XFunSpec staticProxyMethodForInjection(ContributionBinding binding) {
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XTypeElement enclosingType = constructor.getEnclosingElement();
    XFunSpecs.Builder builder =
        methodBuilder(generatedProxyMethodName(binding))
            .addModifiers(PUBLIC, STATIC)
            .varargs(constructor.isVarArgs())
            .returns(providedTypeName(binding))
            .addTypeVariableNames(typeVariableNames(enclosingType))
            .addExceptions(constructor.getThrownTypes());
    XCodeBlock arguments =
        copyParameters(builder, new UniqueNameSet(), constructor.getParameters(), compilerOptions);
    return builder
        .addStatement(
            "return %L",
            XCodeBlock.ofNewInstance(enclosingType.getType().asTypeName(), "%L", arguments))
        .build();
  }

  // Example:
  //
  // public static Foo provideFoo(FooModule module, Bar bar, Baz baz) {
  //   return Preconditions.checkNotNullFromProvides(module.provideFoo(bar, baz));
  // }
  private XFunSpec staticProxyMethodForProvision(ProvisionBinding binding) {
    XMethodElement method = asMethod(binding.bindingElement().get());
    XFunSpecs.Builder builder =
        methodBuilder(generatedProxyMethodName(binding))
            .addModifiers(PUBLIC, STATIC)
            .varargs(method.isVarArgs())
            .addExceptions(method.getThrownTypes());

    XTypeElement enclosingType = asTypeElement(method.getEnclosingElement());
    UniqueNameSet parameterNameSet = new UniqueNameSet();
    XCodeBlock module;
    if (method.isStatic() || enclosingType.isCompanionObject()) {
      module = XCodeBlock.of("%T", enclosingType.asClassName());
    } else if (enclosingType.isKotlinObject()) {
      // Call through the singleton instance.
      // See: https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#static-methods
      module = XCodeBlock.of("%T.INSTANCE", enclosingType.asClassName());
    } else {
      builder.addTypeVariableNames(typeVariableNames(enclosingType));
      module = copyInstance(builder, parameterNameSet, enclosingType.getType());
    }
    XCodeBlock arguments =
        copyParameters(builder, parameterNameSet, method.getParameters(), compilerOptions);
    XCodeBlock invocation = XCodeBlock.of("%L.%L(%L)", module, method.getJvmName(), arguments);

    Nullability nullability = Nullability.of(method);
    return builder
        .addAnnotationNames(nullability.nonTypeUseNullableAnnotations())
        .returns(contributedTypeName(binding))
        .addStatement("return %L", maybeWrapInCheckForNull(binding, invocation))
        .build();
  }

  private XCodeBlock maybeWrapInCheckForNull(ProvisionBinding binding, XCodeBlock codeBlock) {
    return binding.shouldCheckForNull(compilerOptions)
        ? XCodeBlock.of(
            "%T.checkNotNullFromProvides(%L)", XTypeNames.DAGGER_PRECONDITIONS, codeBlock)
        : codeBlock;
  }

  private XCodeBlock copyInstance(
      XFunSpecs.Builder methodBuilder, UniqueNameSet parameterNameSet, XType type) {
    boolean isTypeNameAccessible = isTypeAccessibleFromPublicApi(type, compilerOptions);
    XCodeBlock instance =
        copyParameter(
            methodBuilder,
            parameterNameSet.getUniqueName("instance"),
            type.asTypeName(),
            Nullability.NOT_NULLABLE,
            isTypeNameAccessible,
            compilerOptions);
    // If we had to cast the module add an extra parenthesis since we're calling a method on it.
    return isTypeNameAccessible ? instance : XCodeBlock.of("(%L)", instance);
  }

  private XAnnotationSpec scopeMetadataAnnotation(ContributionBinding binding) {
    XAnnotationSpec.Builder builder = XAnnotationSpec.builder(XTypeNames.SCOPE_METADATA);
    binding
        .scope()
        .map(Scope::scopeAnnotation)
        .map(DaggerAnnotation::xprocessing)
        .map(XAnnotation::getQualifiedName)
        .ifPresent(scopeCanonicalName -> builder.addMember("value", "%S", scopeCanonicalName));
    return builder.build();
  }

  private XAnnotationSpec qualifierMetadataAnnotation(ContributionBinding binding) {
    XAnnotationSpec.Builder builder = XAnnotationSpec.builder(XTypeNames.QUALIFIER_METADATA);
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
        .forEach(qualifier -> builder.addMember("value", "%S", qualifier));
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

  private XTypeName providedTypeName(ContributionBinding binding) {
    XTypeName typeName =
        isTypeAccessibleFromPublicApi(binding.contributedType(), compilerOptions)
            ? binding.contributedType().asTypeName()
            : XTypeName.ANY_OBJECT;
    return asNullableTypeName(typeName, binding.nullability(), compilerOptions);
  }

  /**
   * Returns the type contributed by the given {@code binding}.
   *
   * <p>This is the same as {@link #providedTypeName} except when the contributed type is a
   * primitive type, this method will return the primitive type instead of the boxed type.
   */
  private XTypeName contributedTypeName(ContributionBinding binding) {
    return binding.contributedPrimitiveType().isPresent()
        ? asNullableTypeName(
            binding.contributedPrimitiveType().get().asTypeName(),
            binding.nullability(),
            compilerOptions)
        : providedTypeName(binding);
  }

  private Optional<XTypeName> factoryTypeName(ContributionBinding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? Optional.empty()
        : Optional.of(factoryOf(providedTypeName(binding)));
  }

  /** Represents the available fields in the generated factory class. */
  private static final class FactoryFields {
    static FactoryFields create(ContributionBinding binding, CompilerOptions compilerOptions) {
      UniqueNameSet nameSet = new UniqueNameSet();
      // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
      Optional<XPropertySpec> moduleField =
          binding.requiresModuleInstance()
              ? Optional.of(
                  createField(
                      binding.bindingTypeElement().get().getType().asTypeName(),
                      nameSet.getUniqueName("module")))
              : Optional.empty();

      ImmutableMap.Builder<DependencyRequest, XPropertySpec> frameworkFields =
          ImmutableMap.builder();
      generateBindingFieldsForDependencies(binding, compilerOptions)
          .forEach(
              (dependency, field) ->
                  frameworkFields.put(
                      dependency, createField(field.type(), nameSet.getUniqueName(field.name()))));

      return new FactoryFields(moduleField, frameworkFields.buildOrThrow());
    }

    private static XPropertySpec createField(XTypeName typeName, String name) {
      return XPropertySpecs.of(name, typeName, PRIVATE, FINAL);
    }

    private final Optional<XPropertySpec> moduleField;
    private final ImmutableMap<DependencyRequest, XPropertySpec> frameworkFields;

    private FactoryFields(
        Optional<XPropertySpec> moduleField,
        ImmutableMap<DependencyRequest, XPropertySpec> frameworkFields) {
      this.moduleField = moduleField;
      this.frameworkFields = frameworkFields;
    }

    XPropertySpec get(DependencyRequest request) {
      return frameworkFields.get(request);
    }

    ImmutableList<XPropertySpec> getAll() {
      return moduleField.isPresent()
          ? ImmutableList.<XPropertySpec>builder()
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
