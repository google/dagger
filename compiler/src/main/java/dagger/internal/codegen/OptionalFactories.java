/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.TypeNames.PROVIDER;
import static dagger.internal.codegen.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.TypeNames.optionalOf;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.InstanceFactory;
import dagger.internal.Preconditions;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** The nested class and static methods required by the component to implement optional bindings. */
// TODO(dpb): Name classes correctly if a component uses both Guava and JDK Optional.
final class OptionalFactories {

  /**
   * A field specification for a {@link Provider<Optional<T>>} that always returns {@code
   * Optional.absent()}.
   */
  private static final FieldSpec ABSENT_OPTIONAL_PROVIDER_FIELD =
      FieldSpec.builder(PROVIDER, "ABSENT_OPTIONAL_PROVIDER", PRIVATE, STATIC, FINAL)
          .addAnnotation(SUPPRESS_WARNINGS_RAWTYPES)
          .initializer("$T.create($T.absent())", InstanceFactory.class, Optional.class)
          .addJavadoc(
              "A {@link $T} that returns {@code $T.absent()}.", Provider.class, Optional.class)
          .build();

  /**
   * A method specification for a {@link Provider<Optional<T>>} that always returns {@code
   * Optional.absent()}.
   */
  private static final MethodSpec ABSENT_OPTIONAL_PROVIDER_METHOD =
      methodBuilder("absentOptionalProvider")
          .addModifiers(PRIVATE, STATIC)
          .addTypeVariable(TypeVariableName.get("T"))
          .returns(providerOf(optionalOf(TypeVariableName.get("T"))))
          .addJavadoc(
              "Returns a {@link $T} that returns {@code $T.absent()}.",
              Provider.class,
              Optional.class)
          .addCode("$L // safe covariant cast\n", SUPPRESS_WARNINGS_UNCHECKED)
          .addCode(
              "$1T provider = ($1T) $2N;",
              providerOf(optionalOf(TypeVariableName.get("T"))),
              ABSENT_OPTIONAL_PROVIDER_FIELD)
          .addCode("return provider;")
          .build();

  /**
   * The factory classes that implement {@code Provider<Optional<T>>} or {@code
   * Producer<Optional<T>>} for present optional bindings for a given kind of dependency request
   * within the component.
   *
   * <p>The row key specifies whether the class implements {@link Provider} or {@link Producer}, and
   * the column key specifies the kind of dependency request represented by {@code T}.
   */
  private final Table<BindingType, DependencyRequest.Kind, TypeSpec> presentFactoryClasses =
      HashBasedTable.create();

  /**
   * If the component contains any absent optional bindings, this will be the member select for a
   * static method that returns a Provider<Optional<T>> that always returns {@link
   * Optional#absent()}.
   */
  private Optional<CodeBlock> absentOptionalProviderMethod = Optional.absent();

  /**
   * Returns an expression that calls a static method that returns a {@code Provider<Optional<T>>}
   * for absent optional bindings.
   */
  CodeBlock absentOptionalProvider() {
    if (!absentOptionalProviderMethod.isPresent()) {
      absentOptionalProviderMethod =
          Optional.of(CodeBlock.of("$N()", ABSENT_OPTIONAL_PROVIDER_METHOD));
    }
    return absentOptionalProviderMethod.get();
  }
  
  /**
   * Returns an expression for an instance of a nested class that implements {@code
   * Provider<Optional<T>>} or {@code Producer<Optional<T>>} for a present optional binding, where
   * {@code T} represents dependency requests of that kind.
   *
   * <ul>
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#INSTANCE}, the class
   *     implements {@code ProviderOrProducer<Optional<T>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PROVIDER}, the class
   *     implements {@code Provider<Optional<Provider<T>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#LAZY}, the class implements
   *     {@code Provider<Optional<Lazy<T>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PROVIDER_OF_LAZY}, the
   *     class implements {@code Provider<Optional<Provider<Lazy<T>>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PRODUCER}, the class
   *     implements {@code Producer<Optional<Producer<T>>>}.
   * <li>If {@code optionalRequestKind} is {@link DependencyRequest.Kind#PRODUCED}, the class
   *     implements {@code Producer<Optional<Produced<T>>>}.
   * </ul>
   *
   * @param delegateFactory an expression for a {@link Provider} or {@link Producer} of the
   *     underlying type
   */
  CodeBlock presentOptionalFactory(ContributionBinding binding, CodeBlock delegateFactory) {
    TypeMirror valueType = OptionalType.from(binding.key()).valueType();
    DependencyRequest.Kind valueKind = DependencyRequest.extractKindAndType(valueType).kind();
    if (!presentFactoryClasses.contains(binding.bindingType(), valueKind)) {
      presentFactoryClasses.put(
          binding.bindingType(),
          valueKind,
          createPresentFactoryClass(binding.bindingType(), valueKind));
    }
    return CodeBlock.of(
        "$N.of($L)", presentFactoryClasses.get(binding.bindingType(), valueKind), delegateFactory);
  }
  /**
   * Adds classes and methods required by previous calls to {@link #absentOptionalProvider()} and
   * {@link #presentOptionalProvider(DependencyRequest.Kind, CodeBlock)} to the top-level {@code
   * component}.
   */
  void addMembers(TypeSpec.Builder component) {
    if (absentOptionalProviderMethod.isPresent()) {
      component.addField(ABSENT_OPTIONAL_PROVIDER_FIELD).addMethod(ABSENT_OPTIONAL_PROVIDER_METHOD);
    }
    for (TypeSpec presentFactoryClass : presentFactoryClasses.values()) {
      component.addType(presentFactoryClass);
    }
  }

  private TypeSpec createPresentFactoryClass(
      BindingType bindingType, DependencyRequest.Kind valueKind) {
    TypeVariableName typeVariable = TypeVariableName.get("T");
    TypeName valueType = valueKind.typeName(typeVariable);
    ParameterizedTypeName factoryType = bindingType.frameworkClassOf(optionalOf(valueType));

    FieldSpec delegateField =
        FieldSpec.builder(bindingType.frameworkClassOf(typeVariable), "delegate", PRIVATE, FINAL)
            .build();
    ParameterSpec delegateParameter = ParameterSpec.builder(delegateField.type, "delegate").build();

    MethodSpec.Builder getMethodBuilder =
        methodBuilder("get").addAnnotation(Override.class).addModifiers(PUBLIC);
    switch (bindingType) {
      case PROVISION:
        getMethodBuilder
            .returns(optionalOf(valueType))
            .addCode(
                "return $T.of($L);",
                Optional.class,
                FrameworkType.PROVIDER.to(valueKind, CodeBlock.of("$N", delegateField)));
        break;

      case PRODUCTION:
        getMethodBuilder.returns(listenableFutureOf(optionalOf(valueType)));

        switch (valueKind) {
          case FUTURE: // return a ListenableFuture<Optional<ListenableFuture<T>>>
          case PRODUCER: // return a ListenableFuture<Optional<Producer<T>>>
            getMethodBuilder.addCode(
                "return $T.immediateFuture($T.of($L));",
                Futures.class,
                Optional.class,
                FrameworkType.PRODUCER.to(valueKind, CodeBlock.of("$N", delegateField)));
            break;

          case INSTANCE: // return a ListenableFuture<Optional<T>>
            getMethodBuilder.addCode(
                "return $L;",
                transformFutureToOptional(typeVariable, CodeBlock.of("$N.get()", delegateField)));
            break;

          case PRODUCED: // return a ListenableFuture<Optional<Produced<T>>>
            getMethodBuilder.addCode(
                "return $L;",
                transformFutureToOptional(
                    valueType,
                    CodeBlock.of(
                        "$T.createFutureProduced($N.get())", Producers.class, delegateField)));
            break;

          default:
            throw new UnsupportedOperationException(factoryType + " objects are not supported");
        }
        break;

      default:
        throw new AssertionError(bindingType);
    }
    MethodSpec getMethod = getMethodBuilder.build();

    String factoryClassName =
        String.format(
            "PresentOptional%s%s",
            UPPER_UNDERSCORE.to(UPPER_CAMEL, valueKind.toString()),
            bindingType.frameworkClass().getSimpleName());

    return classBuilder(factoryClassName)
        .addTypeVariable(typeVariable)
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addSuperinterface(factoryType)
        .addJavadoc(
            "A {@link $T} that uses a delegate {@code $T}.", factoryType, delegateField.type)
        .addField(delegateField)
        .addMethod(
            constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(delegateParameter)
                .addCode(
                    "this.$N = $T.checkNotNull($N);",
                    delegateField,
                    Preconditions.class,
                    delegateParameter)
                .build())
        .addMethod(getMethod)
        .addMethod(
            methodBuilder("of")
                .addModifiers(PRIVATE, STATIC)
                .addTypeVariable(typeVariable)
                .returns(factoryType)
                .addParameter(delegateParameter)
                .addCode(
                    "return new $L<$T>($N);", factoryClassName, typeVariable, delegateParameter)
                .build())
        .build();
  }

  /**
   * An expression that uses {@link Futures#transform(ListenableFuture, Function)} to transform a
   * {@code ListenableFuture<inputType>} into a {@code ListenableFuture<Optional<inputType>>}.
   *
   * @param inputFuture an expression of type {@code ListenableFuture<inputType>}
   */
  private static CodeBlock transformFutureToOptional(TypeName inputType, CodeBlock inputFuture) {
    return CodeBlock.of(
        "$T.transform($L, $L)",
        Futures.class,
        inputFuture,
        anonymousClassBuilder("")
            .addSuperinterface(
                ParameterizedTypeName.get(
                    ClassName.get(Function.class), inputType, optionalOf(inputType)))
            .addMethod(
                methodBuilder("apply")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(optionalOf(inputType))
                    .addParameter(inputType, "input")
                    .addCode("return $T.of(input);", Optional.class)
                    .build())
            .build());
  }
}
