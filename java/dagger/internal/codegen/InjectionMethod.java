/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypePubliclyAccessible;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * A static method that implements provision and/or injection in one step:
 *
 * <ul>
 *   <li>methods that invoke {@code @Inject} constructors and do members injection if necessary
 *   <li>methods that call {@code @Provides} module methods
 *   <li>methods that perform members injection
 * </ul>
 *
 * <p>Note that although this type uses {@code @AutoValue}, it uses instance equality. It uses
 * {@code @AutoValue} to avoid the boilerplate of writing a correct builder, but is not intended to
 * actually be a value type.
 */
@AutoValue
abstract class InjectionMethod {
  abstract String name();

  abstract boolean varargs();

  abstract ImmutableList<TypeVariableName> typeVariables();

  abstract ImmutableMap<ParameterSpec, TypeMirror> parameters();

  abstract Optional<TypeMirror> returnType();

  abstract Optional<DeclaredType> nullableAnnotation();

  abstract ImmutableList<TypeMirror> exceptions();

  abstract CodeBlock methodBody();

  abstract ClassName enclosingClass();

  MethodSpec toMethodSpec() {
    MethodSpec.Builder builder =
        methodBuilder(name())
            .addModifiers(PUBLIC, STATIC)
            .varargs(varargs())
            .addTypeVariables(typeVariables())
            .addParameters(parameters().keySet())
            .addCode(methodBody());
    returnType().map(TypeName::get).ifPresent(builder::returns);
    nullableAnnotation()
        .ifPresent(nullableType -> CodeBlocks.addAnnotation(builder, nullableType));
    exceptions().stream().map(TypeName::get).forEach(builder::addException);
    return builder.build();
  }

  CodeBlock invoke(List<CodeBlock> arguments, ClassName requestingClass) {
    checkArgument(arguments.size() == parameters().size());
    CodeBlock.Builder invocation = CodeBlock.builder();
    if (!enclosingClass().equals(requestingClass)) {
      invocation.add("$T.", enclosingClass());
    }
    return invocation.add("$L($L)", name(), makeParametersCodeBlock(arguments)).build();
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  static Builder builder(DaggerElements elements) {
    Builder builder = new AutoValue_InjectionMethod.Builder();
    builder.elements = elements;
    builder.varargs(false).exceptions(ImmutableList.of()).nullableAnnotation(Optional.empty());
    return builder;
  }

  @CanIgnoreReturnValue
  @AutoValue.Builder
  abstract static class Builder {
    private final UniqueNameSet parameterNames = new UniqueNameSet();
    private final CodeBlock.Builder methodBody = CodeBlock.builder();
    private DaggerElements elements;

    abstract ImmutableMap.Builder<ParameterSpec, TypeMirror> parametersBuilder();
    abstract ImmutableList.Builder<TypeVariableName> typeVariablesBuilder();
    abstract Builder name(String name);
    abstract Builder varargs(boolean varargs);
    abstract Builder returnType(TypeMirror returnType);
    abstract Builder exceptions(Iterable<? extends TypeMirror> exceptions);
    abstract Builder nullableAnnotation(Optional<DeclaredType> nullableAnnotation);
    abstract Builder methodBody(CodeBlock methodBody);

    final CodeBlock.Builder methodBodyBuilder() {
      return methodBody;
    }

    abstract Builder enclosingClass(ClassName enclosingClass);

    /**
     * Adds a parameter for the given name and type. If another parameter has already been added
     * with the same name, the name is disambiguated.
     */
    ParameterSpec addParameter(String name, TypeMirror type) {
      ParameterSpec parameter =
          ParameterSpec.builder(TypeName.get(type), parameterNames.getUniqueName(name)).build();
      parametersBuilder().put(parameter, type);
      return parameter;
    }

    /**
     * Calls {@link #copyParameter(VariableElement)} for each parameter of of {@code method}, and
     * concatenates the results of each call, {@link CodeBlocks#makeParametersCodeBlock(Iterable)
     * separated with commas}.
     */
    CodeBlock copyParameters(ExecutableElement method) {
      ImmutableList.Builder<CodeBlock> argumentsBuilder = ImmutableList.builder();
      for (VariableElement parameter : method.getParameters()) {
        argumentsBuilder.add(copyParameter(parameter));
      }
      varargs(method.isVarArgs());
      return makeParametersCodeBlock(argumentsBuilder.build());
    }

    /**
     * Adds {@code parameter} as a parameter of this method, using a publicly accessible version of
     * the parameter's type. Returns a {@link CodeBlock} of the usage of this parameter within the
     * injection method's {@link #methodBody()}.
     */
    CodeBlock copyParameter(VariableElement parameter) {
      TypeMirror elementType = parameter.asType();
      boolean useObject = !isRawTypePubliclyAccessible(elementType);
      TypeMirror publicType =  useObject ? objectType() : elementType;
      ParameterSpec parameterSpec = addParameter(parameter.getSimpleName().toString(), publicType);
      return useObject
          ? CodeBlock.of("($T) $N", elementType, parameterSpec)
          : CodeBlock.of("$N", parameterSpec);
    }

    private TypeMirror objectType() {
      return elements.getTypeElement(Object.class).asType();
    }

    /**
     * Adds each type parameter of {@code parameterizable} as a type parameter of this injection
     * method.
     */
    Builder copyTypeParameters(Parameterizable parameterizable) {
      parameterizable.getTypeParameters().stream()
          .map(TypeVariableName::get)
          .forEach(typeVariablesBuilder()::add);
      return this;
    }

    Builder copyThrows(ExecutableElement element) {
      exceptions(element.getThrownTypes());
      return this;
    }

    @CheckReturnValue
    final InjectionMethod build() {
      return methodBody(methodBody.build()).buildInternal();
    }

    abstract InjectionMethod buildInternal();
  }
}
