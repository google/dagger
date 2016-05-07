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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.Provides;
import dagger.internal.Factory;
import dagger.internal.MembersInjectors;
import dagger.internal.Preconditions;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.TypeSpec.enumBuilder;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ContributionType.SET;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD;
import static dagger.internal.codegen.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.TypeNames.factoryOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for
 * {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {

  private final CompilerOptions compilerOptions;

  FactoryGenerator(Filer filer, Elements elements, CompilerOptions compilerOptions) {
    super(filer, elements);
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(ProvisionBinding binding) {
    return generatedClassNameForBinding(binding);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProvisionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProvisionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkState(!binding.unresolved().isPresent());

    TypeMirror keyType =
        binding.contributionType().equals(ContributionType.MAP)
            ? MapType.from(binding.key().type()).unwrappedValueType(Provider.class)
            : binding.key().type();
    TypeName providedTypeName = TypeName.get(keyType);
    ParameterizedTypeName parameterizedFactoryName = factoryOf(providedTypeName);
    Optional<ParameterizedTypeName> factoryOfRawTypeName = Optional.absent();
    TypeSpec.Builder factoryBuilder;
    Optional<MethodSpec.Builder> constructorBuilder = Optional.absent();
    ImmutableList<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    ImmutableMap<BindingKey, FrameworkField> fields = generateBindingFieldsForDependencies(binding);
    boolean useRawType =
        binding.factoryCreationStrategy() == ENUM_INSTANCE
            && binding.bindingKind() == INJECTION
            && !typeParameters.isEmpty();
    switch (binding.factoryCreationStrategy()) {
      case ENUM_INSTANCE:
        factoryBuilder = enumBuilder(generatedTypeName.simpleName()).addEnumConstant("INSTANCE");
        // If we have type parameters, then remove the parameters from our providedTypeName,
        // since we'll be implementing an erased version of it.
        if (useRawType) {
          factoryBuilder.addAnnotation(SUPPRESS_WARNINGS_RAWTYPES);
          // TODO(ronshapiro): instead of reassigning, introduce an optional/second parameter
          providedTypeName = ((ParameterizedTypeName) providedTypeName).rawType;
          factoryOfRawTypeName = Optional.of(factoryOf(providedTypeName));
        }
        break;
      case CLASS_CONSTRUCTOR:
        factoryBuilder =
            classBuilder(generatedTypeName)
                .addTypeVariables(typeParameters)
                .addModifiers(FINAL);
        constructorBuilder = Optional.of(constructorBuilder().addModifiers(PUBLIC));
        if (binding.bindingKind().equals(PROVISION)
            && !binding.bindingElement().getModifiers().contains(STATIC)) {
          addConstructorParameterAndTypeField(
              TypeName.get(binding.bindingTypeElement().asType()),
              "module",
              factoryBuilder,
              constructorBuilder.get());
        }
        for (FrameworkField bindingField : fields.values()) {
          addConstructorParameterAndTypeField(
              bindingField.frameworkType(),
              bindingField.name(),
              factoryBuilder,
              constructorBuilder.get());
        }
        break;
      case DELEGATE:
        return Optional.absent();
      default:
        throw new AssertionError();
    }

    factoryBuilder
        .addModifiers(PUBLIC)
        .addSuperinterface(factoryOfRawTypeName.or(parameterizedFactoryName));

    // If constructing a factory for @Inject or @Provides bindings, we use a static create method
    // so that generated components can avoid having to refer to the generic types
    // of the factory.  (Otherwise they may have visibility problems referring to the types.)
    Optional<MethodSpec> createMethod;
    switch(binding.bindingKind()) {
      case INJECTION:
      case PROVISION:
        // The return type is usually the same as the implementing type, except in the case
        // of enums with type variables (where we cast).
        MethodSpec.Builder createMethodBuilder =
            methodBuilder("create")
                .addModifiers(PUBLIC, STATIC)
                .returns(parameterizedFactoryName);
        if (binding.factoryCreationStrategy() != ENUM_INSTANCE
            || binding.bindingKind() == INJECTION) {
          createMethodBuilder.addTypeVariables(typeParameters);
        }
        List<ParameterSpec> params =
            constructorBuilder.isPresent()
                ? constructorBuilder.get().build().parameters : ImmutableList.<ParameterSpec>of();
        createMethodBuilder.addParameters(params);
        switch (binding.factoryCreationStrategy()) {
          case ENUM_INSTANCE:
            if (!useRawType) {
              createMethodBuilder.addStatement("return INSTANCE");
            } else {
              // We use an unsafe cast here because the types are different.
              // It's safe because the type is never referenced anywhere.
              createMethodBuilder.addStatement("return ($T) INSTANCE", TypeNames.FACTORY);
              createMethodBuilder.addAnnotation(SUPPRESS_WARNINGS_UNCHECKED);
            }
            break;

          case CLASS_CONSTRUCTOR:
            createMethodBuilder.addStatement(
                "return new $T($L)",
                parameterizedGeneratedTypeNameForBinding(binding),
                makeParametersCodeBlock(
                    Lists.transform(params, CodeBlocks.PARAMETER_NAME)));
            break;
          default:
            throw new AssertionError();
        }
        createMethod = Optional.of(createMethodBuilder.build());
        break;
      default:
        createMethod = Optional.absent();
    }

    if (constructorBuilder.isPresent()) {
      factoryBuilder.addMethod(constructorBuilder.get().build());
    }

    List<CodeBlock> parameters = Lists.newArrayList();
    for (DependencyRequest dependency : binding.dependencies()) {
      parameters.add(
          frameworkTypeUsageStatement(
              CodeBlock.of("$L", fields.get(dependency.bindingKey()).name()),
              dependency.kind()));
    }
    CodeBlock parametersCodeBlock = makeParametersCodeBlock(parameters);

    MethodSpec.Builder getMethodBuilder =
        methodBuilder("get")
            .returns(providedTypeName)
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC);

    if (binding.bindingKind().equals(PROVISION)) {
      CodeBlock.Builder providesMethodInvocationBuilder = CodeBlock.builder();
      if (binding.bindingElement().getModifiers().contains(STATIC)) {
        providesMethodInvocationBuilder.add("$T", ClassName.get(binding.bindingTypeElement()));
      } else {
        providesMethodInvocationBuilder.add("module");
      }
      providesMethodInvocationBuilder.add(
          ".$L($L)", binding.bindingElement().getSimpleName(), parametersCodeBlock);
      CodeBlock providesMethodInvocation = providesMethodInvocationBuilder.build();

      if (binding.contributionType().equals(SET)) {
        TypeName paramTypeName = TypeName.get(
            MoreTypes.asDeclared(keyType).getTypeArguments().get(0));
        // TODO(cgruber): only be explicit with the parameter if paramType contains wildcards.
        getMethodBuilder.addStatement(
            "return $T.<$T>singleton($L)",
            Collections.class, paramTypeName, providesMethodInvocation);
      } else if (binding.nullableType().isPresent()
          || compilerOptions.nullableValidationKind().equals(Diagnostic.Kind.WARNING)) {
        if (binding.nullableType().isPresent()) {
          getMethodBuilder.addAnnotation((ClassName) TypeName.get(binding.nullableType().get()));
        }
        getMethodBuilder.addStatement("return $L", providesMethodInvocation);
      } else {
        getMethodBuilder.addStatement("return $T.checkNotNull($L, $S)",
            Preconditions.class,
            providesMethodInvocation,
            CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);
      }
    } else if (binding.membersInjectionRequest().isPresent()) {
      getMethodBuilder.addStatement("return $T.injectMembers($L, new $T($L))",
          MembersInjectors.class,
          fields.get(binding.membersInjectionRequest().get().bindingKey()).name(),
          providedTypeName,
          parametersCodeBlock);
    } else {
      getMethodBuilder.addStatement("return new $T($L)", providedTypeName, parametersCodeBlock);
    }

    factoryBuilder.addMethod(getMethodBuilder.build());
    if (createMethod.isPresent()) {
      factoryBuilder.addMethod(createMethod.get());
    }

    // TODO(gak): write a sensible toString
    return Optional.of(factoryBuilder);
  }

  private void addConstructorParameterAndTypeField(
      TypeName typeName,
      String variableName,
      TypeSpec.Builder factoryBuilder,
      MethodSpec.Builder constructorBuilder) {
    FieldSpec field = FieldSpec.builder(typeName, variableName, PRIVATE, FINAL).build();
    factoryBuilder.addField(field);
    ParameterSpec parameter = ParameterSpec.builder(typeName, variableName).build();
    constructorBuilder.addParameter(parameter);
    constructorBuilder.addCode("assert $1N != null; this.$2N = $1N;", parameter, field);
  }
}
