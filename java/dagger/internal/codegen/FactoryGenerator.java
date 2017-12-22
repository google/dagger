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

import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.DELEGATE;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD;
import static dagger.internal.codegen.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.MapKeys.mapKeyFactoryMethod;
import static dagger.internal.codegen.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.SourceFiles.frameworkFieldUsages;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.TypeNames.factoryOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.codegen.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.InjectionMethods.ProvisionMethod;
import dagger.model.Key;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for
 * {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
  private final Types types;
  private final CompilerOptions compilerOptions;

  FactoryGenerator(
      Filer filer,
      Elements elements,
      Types types,
      CompilerOptions compilerOptions) {
    super(filer, elements);
    this.types = types;
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(ProvisionBinding binding) {
    return generatedClassNameForBinding(binding);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProvisionBinding binding) {
    return binding.bindingElement();
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProvisionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());

    return binding.factoryCreationStrategy().equals(DELEGATE)
        ? Optional.empty()
        : Optional.of(factoryBuilder(binding));
  }

  private TypeSpec.Builder factoryBuilder(ProvisionBinding binding) {
    TypeSpec.Builder factoryBuilder =
        classBuilder(nameGeneratedType(binding))
            .addModifiers(PUBLIC, FINAL)
            .addSuperinterface(factoryTypeName(binding))
            .addTypeVariables(typeParameters(binding));

    addConstructorAndFields(binding, factoryBuilder);
    factoryBuilder.addMethod(getMethod(binding));
    addCreateMethod(binding, factoryBuilder);

    ProvisionMethod.create(binding, compilerOptions).ifPresent(factoryBuilder::addMethod);
    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);
    mapKeyFactoryMethod(binding, types).ifPresent(factoryBuilder::addMethod);

    return factoryBuilder;
  }

  private void addConstructorAndFields(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    if (binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE)) {
      return;
    }
    // TODO(user): Make the constructor private?
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
      // TODO(user, dpb): Should this use contributingModule()?
      TypeName type = TypeName.get(binding.bindingTypeElement().get().asType());
      return Optional.of(ParameterSpec.builder(type, "module").build());
    }
    return Optional.empty();
  }

  private ImmutableMap<Key, FieldSpec> frameworkFields(ProvisionBinding binding) {
    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    // TODO(user, dpb): Add a test for the case when a Factory parameter is named "module".
    if (binding.requiresModuleInstance()) {
      uniqueFieldNames.claim("module");
    }
    ImmutableMap.Builder<Key, FieldSpec> fields = ImmutableMap.builder();
    generateBindingFieldsForDependencies(binding)
        .forEach(
            (key, frameworkField) -> {
              TypeName type = frameworkField.type();
              String name = uniqueFieldNames.getUniqueName(frameworkField.name());
              fields.put(key, FieldSpec.builder(type, name, PRIVATE, FINAL).build());
            });
    return fields.build();
  }

  private void addCreateMethod(ProvisionBinding binding, TypeSpec.Builder factoryBuilder) {
    // If constructing a factory for @Inject or @Provides bindings, we use a static create method
    // so that generated components can avoid having to refer to the generic types
    // of the factory.  (Otherwise they may have visibility problems referring to the types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(parameterizedGeneratedTypeNameForBinding(binding))
            .addTypeVariables(typeParameters(binding));

    switch (binding.factoryCreationStrategy()) {
      case SINGLETON_INSTANCE:
        FieldSpec.Builder instanceFieldBuilder =
            FieldSpec.builder(nameGeneratedType(binding), "INSTANCE", PRIVATE, STATIC, FINAL)
                .initializer("new $T()", nameGeneratedType(binding));

        if (!typeParameters(binding).isEmpty()) {
          // If the factory has type parameters, ignore them in the field declaration & initializer
          instanceFieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));

          createMethodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
        }
        createMethodBuilder.addStatement("return INSTANCE");
        factoryBuilder.addField(instanceFieldBuilder.build());
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
    TypeName providedTypeName = providedTypeName(binding);
    MethodSpec.Builder getMethodBuilder =
        methodBuilder("get")
            .returns(providedTypeName)
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC);

    ImmutableMap<Key, FieldSpec> frameworkFields = frameworkFields(binding);
    CodeBlock parametersCodeBlock =
        makeParametersCodeBlock(
            frameworkFieldUsages(binding.provisionDependencies(), frameworkFields).values());

    if (binding.bindingKind().equals(PROVISION)) {
      // TODO(dpb): take advantage of the code in InjectionMethods so this doesn't have to be
      // duplicated
      binding
          .nullableType()
          .ifPresent(nullableType -> CodeBlocks.addAnnotation(getMethodBuilder, nullableType));
      CodeBlock methodCall =
          CodeBlock.of(
              "$L.$L($L)",
              binding.requiresModuleInstance()
                  ? "module"
                  : CodeBlock.of("$T", ClassName.get(binding.bindingTypeElement().get())),
              binding.bindingElement().get().getSimpleName(),
              parametersCodeBlock);
      getMethodBuilder.addStatement(
          "return $L",
          binding.shouldCheckForNull(compilerOptions)
              ? checkNotNullProvidesMethod(methodCall)
              : methodCall);
    } else if (!binding.injectionSites().isEmpty()) {
      CodeBlock instance = CodeBlock.of("instance");
      getMethodBuilder
          .addStatement("$1T $2L = new $1T($3L)", providedTypeName, instance, parametersCodeBlock)
          .addCode(
              InjectionSiteMethod.invokeAll(
                  binding.injectionSites(),
                  nameGeneratedType(binding),
                  instance,
                  binding.key().type(),
                  types,
                  frameworkFieldUsages(binding.dependencies(), frameworkFields)::get))
          .addStatement("return $L", instance);
    } else {
      getMethodBuilder.addStatement("return new $T($L)", providedTypeName, parametersCodeBlock);
    }
    return getMethodBuilder.build();
  }

  private static TypeName providedTypeName(ProvisionBinding binding) {
    return TypeName.get(binding.contributedType());
  }

  private static TypeName factoryTypeName(ProvisionBinding binding) {
    return factoryOf(providedTypeName(binding));
  }

  private static ParameterSpec toParameter(FieldSpec field) {
    return ParameterSpec.builder(field.type, field.name).build();
  }

  private static ImmutableList<TypeVariableName> typeParameters(ProvisionBinding binding) {
    // Use type parameters from the injected type or the module instance *only* if we require it.
    if (binding.bindingKind().equals(INJECTION) || binding.requiresModuleInstance()) {
      return bindingTypeElementTypeVariableNames(binding);
    }
    return ImmutableList.of();
  }

  /**
   * Returns {@code Preconditions.checkNotNull(providesMethodInvocation)} with a message suitable
   * for {@code @Provides} methods.
   */
  static CodeBlock checkNotNullProvidesMethod(CodeBlock providesMethodInvocation) {
    return CodeBlock.of(
        "$T.checkNotNull($L, $S)",
        Preconditions.class,
        providesMethodInvocation,
        CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);
  }
}
