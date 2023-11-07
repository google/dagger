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
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedParameters;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.factoryOf;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.PROVISION;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Factory;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.writing.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for {@link
 * Inject} constructors.
 */
public final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
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
  public XElement originatingElement(ProvisionBinding binding) {
    // we only create factories for bindings that have a binding element
    return binding.bindingElement().get();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(ProvisionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());

    if (binding.kind() == BindingKind.DELEGATE) {
      return ImmutableList.of();
    }

    return ImmutableList.of(factoryBuilder(binding));
  }

  private TypeSpec.Builder factoryBuilder(ProvisionBinding binding) {
    TypeSpec.Builder factoryBuilder =
        classBuilder(generatedClassNameForBinding(binding))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(bindingTypeElementTypeVariableNames(binding));

    if (binding.kind() == BindingKind.INJECTION
        || binding.kind() == BindingKind.ASSISTED_INJECTION
        || binding.kind() == BindingKind.PROVISION) {
      factoryBuilder.addAnnotation(scopeMetadataAnnotation(binding));
      factoryBuilder.addAnnotation(qualifierMetadataAnnotation(binding));
    }

    factoryTypeName(binding).ifPresent(factoryBuilder::addSuperinterface);
    addConstructorAndFields(binding, factoryBuilder);
    factoryBuilder.addMethod(getMethod(binding));
    addCreateMethod(binding, factoryBuilder);

    factoryBuilder.addMethod(ProvisionMethod.create(binding, compilerOptions));
    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    return factoryBuilder;
  }

  private void addConstructorAndFields(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    if (FactoryCreationStrategy.of(binding) == FactoryCreationStrategy.SINGLETON_INSTANCE) {
      return;
    }
    // TODO(bcorso): Make the constructor private?
    MethodSpec.Builder constructor = constructorBuilder().addModifiers(PUBLIC);
    constructorParams(binding).forEach(
        param -> {
          constructor.addParameter(param).addStatement("this.$1N = $1N", param);
          factoryBuilder.addField(
              FieldSpec.builder(param.type, param.name, PRIVATE, FINAL).build());
        });
    factoryBuilder.addMethod(constructor.build());
  }

  private ImmutableList<ParameterSpec> constructorParams(ProvisionBinding binding) {
    ImmutableList.Builder<ParameterSpec> params = ImmutableList.builder();
    moduleParameter(binding).ifPresent(params::add);
    frameworkFields(binding).values().forEach(field -> params.add(toParameter(field)));
    return params.build();
  }

  private Optional<ParameterSpec> moduleParameter(ProvisionBinding binding) {
    if (binding.requiresModuleInstance()) {
      // TODO(bcorso, dpb): Should this use contributingModule()?
      TypeName type = binding.bindingTypeElement().get().getType().getTypeName();
      return Optional.of(ParameterSpec.builder(type, "module").build());
    }
    return Optional.empty();
  }

  private ImmutableMap<DependencyRequest, FieldSpec> frameworkFields(ProvisionBinding binding) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
    moduleParameter(binding).ifPresent(module -> uniqueFieldNames.claim(module.name));
    // We avoid Maps.transformValues here because it would implicitly depend on the order in which
    // the transform function is evaluated on each entry in the map.
    ImmutableMap.Builder<DependencyRequest, FieldSpec> builder = ImmutableMap.builder();
    generateBindingFieldsForDependencies(binding).forEach(
        (dependency, field) ->
            builder.put(dependency,
                FieldSpec.builder(
                        field.type(), uniqueFieldNames.getUniqueName(field.name()), PRIVATE, FINAL)
                    .build()));
    return builder.build();
  }

  private void addCreateMethod(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    // If constructing a factory for @Inject or @Provides bindings, we use a static create method
    // so that generated components can avoid having to refer to the generic types
    // of the factory.  (Otherwise they may have visibility problems referring to the types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(parameterizedGeneratedTypeNameForBinding(binding))
            .addTypeVariables(bindingTypeElementTypeVariableNames(binding));

    switch (FactoryCreationStrategy.of(binding)) {
      case SINGLETON_INSTANCE:
        FieldSpec.Builder instanceFieldBuilder =
            FieldSpec.builder(
                    generatedClassNameForBinding(binding), "INSTANCE", PRIVATE, STATIC, FINAL)
                .initializer("new $T()", generatedClassNameForBinding(binding));

        if (!bindingTypeElementTypeVariableNames(binding).isEmpty()) {
          // If the factory has type parameters, ignore them in the field declaration & initializer
          instanceFieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
          createMethodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
        }

        ClassName instanceHolderName =
            generatedClassNameForBinding(binding).nestedClass("InstanceHolder");
        createMethodBuilder.addStatement("return $T.INSTANCE", instanceHolderName);
        factoryBuilder.addType(
            TypeSpec.classBuilder(instanceHolderName)
                .addModifiers(PRIVATE, STATIC, FINAL)
                .addField(instanceFieldBuilder.build())
                .build());
        break;
      case CLASS_CONSTRUCTOR:
        List<ParameterSpec> params = constructorParams(binding);
        createMethodBuilder.addParameters(params);
        createMethodBuilder.addStatement(
            "return new $T($L)",
            parameterizedGeneratedTypeNameForBinding(binding),
            makeParametersCodeBlock(Lists.transform(params, input -> CodeBlock.of("$N", input))));
        break;
      default:
        throw new AssertionError();
    }
    factoryBuilder.addMethod(createMethodBuilder.build());
  }

  private MethodSpec getMethod(ProvisionBinding binding) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    ImmutableMap<DependencyRequest, FieldSpec> frameworkFields = frameworkFields(binding);
    frameworkFields.values().forEach(field -> uniqueFieldNames.claim(field.name));
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
    TypeName providedTypeName = providedTypeName(binding);
    MethodSpec.Builder getMethod =
        methodBuilder("get")
            .addModifiers(PUBLIC)
            .returns(providedTypeName)
            .addParameters(assistedParameters.values());

    if (factoryTypeName(binding).isPresent()) {
      getMethod.addAnnotation(Override.class);
    }
    CodeBlock invokeNewInstance =
        ProvisionMethod.invoke(
            binding,
            request ->
                sourceFiles.frameworkTypeUsageStatement(
                    CodeBlock.of("$N", frameworkFields.get(request)), request.kind()),
            param -> assistedParameters.get(param).name,
            generatedClassNameForBinding(binding),
            moduleParameter(binding).map(module -> CodeBlock.of("$N", module)),
            compilerOptions);

    if (binding.kind().equals(PROVISION)) {
      binding
          .nullability()
          .nullableAnnotations()
          .forEach(getMethod::addAnnotation);
      getMethod.addStatement("return $L", invokeNewInstance);
    } else if (!binding.injectionSites().isEmpty()) {
      CodeBlock instance = CodeBlock.of("instance");
      getMethod
          .addStatement("$T $L = $L", providedTypeName, instance, invokeNewInstance)
          .addCode(
              InjectionSiteMethod.invokeAll(
                  binding.injectionSites(),
                  generatedClassNameForBinding(binding),
                  instance,
                  binding.key().type().xprocessing(),
                  sourceFiles.frameworkFieldUsages(binding.dependencies(), frameworkFields)::get))
          .addStatement("return $L", instance);
    } else {
      getMethod.addStatement("return $L", invokeNewInstance);
    }
    return getMethod.build();
  }

  private AnnotationSpec scopeMetadataAnnotation(ProvisionBinding binding) {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(TypeNames.SCOPE_METADATA);
    binding.scope()
        .map(Scope::scopeAnnotation)
        .map(DaggerAnnotation::className)
        .map(ClassName::canonicalName)
        .ifPresent(scopeCanonicalName -> builder.addMember("value", "$S", scopeCanonicalName));
    return builder.build();
  }

  private AnnotationSpec qualifierMetadataAnnotation(ProvisionBinding binding) {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(TypeNames.QUALIFIER_METADATA);
    // Collect all qualifiers on the binding itself or its dependencies
    Stream.concat(
            Stream.of(binding.key()),
            binding.provisionDependencies().stream().map(DependencyRequest::key))
        .map(Key::qualifier)
        .flatMap(presentValues())
        .map(DaggerAnnotation::className)
        .map(ClassName::canonicalName)
        .distinct()
        .forEach(qualifier -> builder.addMember("value", "$S", qualifier));
    return builder.build();
  }

  private static TypeName providedTypeName(ProvisionBinding binding) {
    return binding.contributedType().getTypeName();
  }

  private static Optional<TypeName> factoryTypeName(ProvisionBinding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? Optional.empty()
        : Optional.of(factoryOf(providedTypeName(binding)));
  }

  private static ParameterSpec toParameter(FieldSpec field) {
    return ParameterSpec.builder(field.type, field.name).build();
  }

  /** The strategy for getting an instance of a factory for a {@link Binding}. */
  private enum FactoryCreationStrategy {
    /** The factory class is a single instance. */
    SINGLETON_INSTANCE,
    /** The factory must be created by calling the constructor. */
    CLASS_CONSTRUCTOR;

    static FactoryCreationStrategy of(Binding binding) {
      switch (binding.kind()) {
        case DELEGATE:
          throw new AssertionError("Delegate bindings don't have a factory.");
        case PROVISION:
          return binding.dependencies().isEmpty() && !binding.requiresModuleInstance()
              ? SINGLETON_INSTANCE
              : CLASS_CONSTRUCTOR;
        case INJECTION:
        case MULTIBOUND_SET:
        case MULTIBOUND_MAP:
          return binding.dependencies().isEmpty()
              ? SINGLETON_INSTANCE
              : CLASS_CONSTRUCTOR;
        default:
          return CLASS_CONSTRUCTOR;
      }
    }
  }
}
