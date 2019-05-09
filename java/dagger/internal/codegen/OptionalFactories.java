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
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.ABSENT_OPTIONAL_FIELD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.ABSENT_OPTIONAL_METHOD;
import static dagger.internal.codegen.ComponentImplementation.TypeSpecKind.PRESENT_FACTORY;
import static dagger.internal.codegen.RequestKinds.requestTypeName;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.TypeNames.PROVIDER;
import static dagger.internal.codegen.javapoet.TypeNames.abstractProducerOf;
import static dagger.internal.codegen.javapoet.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
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
import dagger.internal.codegen.OptionalType.OptionalKind;
import dagger.internal.codegen.javapoet.AnnotationSpecs;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Provider;

/** The nested class and static methods required by the component to implement optional bindings. */
// TODO(dpb): Name members simply if a component uses only one of Guava or JDK Optional.
@PerGeneratedFile
final class OptionalFactories {
  private final ComponentImplementation componentImplementation;

  @Inject OptionalFactories(@TopLevel ComponentImplementation componentImplementation) {
    this.componentImplementation = componentImplementation;
  }

  /**
   * The factory classes that implement {@code Provider<Optional<T>>} or {@code
   * Producer<Optional<T>>} for present optional bindings for a given kind of dependency request
   * within the component.
   *
   * <p>The key is the {@code Provider<Optional<T>>} type.
   */
  private final Map<PresentFactorySpec, TypeSpec> presentFactoryClasses =
      new TreeMap<>(
          Comparator.comparing(PresentFactorySpec::valueKind)
              .thenComparing(PresentFactorySpec::frameworkType)
              .thenComparing(PresentFactorySpec::optionalKind));

  /**
   * The static methods that return a {@code Provider<Optional<T>>} that always returns an absent
   * value.
   */
  private final Map<OptionalKind, MethodSpec> absentOptionalProviderMethods = new TreeMap<>();

  /**
   * The static fields for {@code Provider<Optional<T>>} objects that always return an absent value.
   */
  private final Map<OptionalKind, FieldSpec> absentOptionalProviderFields = new TreeMap<>();

  /**
   * Returns an expression that calls a static method that returns a {@code Provider<Optional<T>>}
   * for absent optional bindings.
   */
  CodeBlock absentOptionalProvider(ContributionBinding binding) {
    verify(
        binding.bindingType().equals(BindingType.PROVISION),
        "Absent optional bindings should be provisions: %s",
        binding);
    OptionalKind optionalKind = OptionalType.from(binding.key()).kind();
    return CodeBlock.of(
        "$N()",
        absentOptionalProviderMethods.computeIfAbsent(
            optionalKind,
            kind -> {
              MethodSpec method = absentOptionalProviderMethod(kind);
              componentImplementation.addMethod(ABSENT_OPTIONAL_METHOD, method);
              return method;
            }));
  }

  /**
   * Creates a method specification for a {@code Provider<Optional<T>>} that always returns an
   * absent value.
   */
  private MethodSpec absentOptionalProviderMethod(OptionalKind optionalKind) {
    TypeVariableName typeVariable = TypeVariableName.get("T");
    return methodBuilder(
            String.format(
                "absent%sProvider", UPPER_UNDERSCORE.to(UPPER_CAMEL, optionalKind.name())))
        .addModifiers(PRIVATE, STATIC)
        .addTypeVariable(typeVariable)
        .returns(providerOf(optionalKind.of(typeVariable)))
        .addJavadoc(
            "Returns a {@link $T} that returns {@code $L}.",
            Provider.class,
            optionalKind.absentValueExpression())
        .addCode("$L // safe covariant cast\n", AnnotationSpecs.suppressWarnings(UNCHECKED))
        .addCode(
            "$1T provider = ($1T) $2N;",
            providerOf(optionalKind.of(typeVariable)),
            absentOptionalProviderFields.computeIfAbsent(
                optionalKind,
                kind -> {
                  FieldSpec field = absentOptionalProviderField(kind);
                  componentImplementation.addField(ABSENT_OPTIONAL_FIELD, field);
                  return field;
                }))
        .addCode("return provider;")
        .build();
  }

  /**
   * Creates a field specification for a {@code Provider<Optional<T>>} that always returns an absent
   * value.
   */
  private FieldSpec absentOptionalProviderField(OptionalKind optionalKind) {
    return FieldSpec.builder(
            PROVIDER,
            String.format("ABSENT_%s_PROVIDER", optionalKind.name()),
            PRIVATE,
            STATIC,
            FINAL)
        .addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES))
        .initializer("$T.create($L)", InstanceFactory.class, optionalKind.absentValueExpression())
        .addJavadoc(
            "A {@link $T} that returns {@code $L}.",
            Provider.class,
            optionalKind.absentValueExpression())
        .build();
  }

  /** Information about the type of a factory for present bindings. */
  @AutoValue
  abstract static class PresentFactorySpec {
    /** Whether the factory is a {@link Provider} or a {@link Producer}. */
    abstract FrameworkType frameworkType();

    /** What kind of {@code Optional} is returned. */
    abstract OptionalKind optionalKind();

    /** The kind of request satisfied by the value of the {@code Optional}. */
    abstract RequestKind valueKind();

    /** The type variable for the factory class. */
    TypeVariableName typeVariable() {
      return TypeVariableName.get("T");
    }

    /** The type contained by the {@code Optional}. */
    TypeName valueType() {
      return requestTypeName(valueKind(), typeVariable());
    }

    /** The type provided or produced by the factory. */
    ParameterizedTypeName optionalType() {
      return optionalKind().of(valueType());
    }

    /** The type of the factory. */
    ParameterizedTypeName factoryType() {
      return frameworkType().frameworkClassOf(optionalType());
    }

    /** The type of the delegate provider or producer. */
    ParameterizedTypeName delegateType() {
      return frameworkType().frameworkClassOf(typeVariable());
    }

    /** Returns the superclass the generated factory should have, if any. */
    Optional<ParameterizedTypeName> superclass() {
      switch (frameworkType()) {
        case PRODUCER_NODE:
          // TODO(cgdecker): This probably isn't a big issue for now, but it's possible this
          // shouldn't be an AbstractProducer:
          // - As AbstractProducer, it'll only call the delegate's get() method once and then cache
          //   that result (essentially) rather than calling the delegate's get() method each time
          //   its get() method is called (which was what it did before the cancellation change).
          // - It's not 100% clear to me whether the view-creation methods should return a view of
          //   the same view created by the delegate or if they should just return their own views.
          return Optional.of(abstractProducerOf(optionalType()));
        default:
          return Optional.empty();
      }
    }

    /** Returns the superinterface the generated factory should have, if any. */
    Optional<ParameterizedTypeName> superinterface() {
      switch (frameworkType()) {
        case PROVIDER:
          return Optional.of(factoryType());
        default:
          return Optional.empty();
      }
    }

    /** Returns the name of the factory method to generate. */
    String factoryMethodName() {
      switch (frameworkType()) {
        case PROVIDER:
          return "get";
        case PRODUCER_NODE:
          return "compute";
      }
      throw new AssertionError(frameworkType());
    }

    /** The name of the factory class. */
    String factoryClassName() {
      return new StringBuilder("Present")
          .append(UPPER_UNDERSCORE.to(UPPER_CAMEL, optionalKind().name()))
          .append(UPPER_UNDERSCORE.to(UPPER_CAMEL, valueKind().toString()))
          .append(frameworkType().frameworkClass().getSimpleName())
          .toString();
    }

    private static PresentFactorySpec of(ContributionBinding binding) {
      return new AutoValue_OptionalFactories_PresentFactorySpec(
          FrameworkType.forBindingType(binding.bindingType()),
          OptionalType.from(binding.key()).kind(),
          getOnlyElement(binding.dependencies()).kind());
    }
  }

  /**
   * Returns an expression for an instance of a nested class that implements {@code
   * Provider<Optional<T>>} or {@code Producer<Optional<T>>} for a present optional binding, where
   * {@code T} represents dependency requests of that kind.
   *
   * <ul>
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#INSTANCE}, the class implements
   *       {@code ProviderOrProducer<Optional<T>>}.
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#PROVIDER}, the class implements
   *       {@code Provider<Optional<Provider<T>>>}.
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#LAZY}, the class implements {@code
   *       Provider<Optional<Lazy<T>>>}.
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#PROVIDER_OF_LAZY}, the class
   *       implements {@code Provider<Optional<Provider<Lazy<T>>>>}.
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#PRODUCER}, the class implements
   *       {@code Producer<Optional<Producer<T>>>}.
   *   <li>If {@code optionalRequestKind} is {@link RequestKind#PRODUCED}, the class implements
   *       {@code Producer<Optional<Produced<T>>>}.
   * </ul>
   *
   * @param delegateFactory an expression for a {@link Provider} or {@link Producer} of the
   *     underlying type
   */
  CodeBlock presentOptionalFactory(ContributionBinding binding, CodeBlock delegateFactory) {
    return CodeBlock.of(
        "$N.of($L)",
        presentFactoryClasses.computeIfAbsent(
            PresentFactorySpec.of(binding),
            spec -> {
              TypeSpec type = presentOptionalFactoryClass(spec);
              componentImplementation.addType(PRESENT_FACTORY, type);
              return type;
            }),
        delegateFactory);
  }

  private TypeSpec presentOptionalFactoryClass(PresentFactorySpec spec) {
    FieldSpec delegateField =
        FieldSpec.builder(spec.delegateType(), "delegate", PRIVATE, FINAL).build();
    ParameterSpec delegateParameter = ParameterSpec.builder(delegateField.type, "delegate").build();
    TypeSpec.Builder factoryClassBuilder =
        classBuilder(spec.factoryClassName())
            .addTypeVariable(spec.typeVariable())
            .addModifiers(PRIVATE, STATIC, FINAL)
            .addJavadoc(
                "A {@code $T} that uses a delegate {@code $T}.",
                spec.factoryType(),
                delegateField.type);

    spec.superclass().ifPresent(factoryClassBuilder::superclass);
    spec.superinterface().ifPresent(factoryClassBuilder::addSuperinterface);

    return factoryClassBuilder
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
        .addMethod(presentOptionalFactoryGetMethod(spec, delegateField))
        .addMethod(
            methodBuilder("of")
                .addModifiers(PRIVATE, STATIC)
                .addTypeVariable(spec.typeVariable())
                .returns(spec.factoryType())
                .addParameter(delegateParameter)
                .addCode(
                    "return new $L<$T>($N);",
                    spec.factoryClassName(),
                    spec.typeVariable(),
                    delegateParameter)
                .build())
        .build();
  }

  private MethodSpec presentOptionalFactoryGetMethod(
      PresentFactorySpec spec, FieldSpec delegateField) {
    MethodSpec.Builder getMethodBuilder =
        methodBuilder(spec.factoryMethodName()).addAnnotation(Override.class).addModifiers(PUBLIC);

    switch (spec.frameworkType()) {
      case PROVIDER:
        return getMethodBuilder
            .returns(spec.optionalType())
            .addCode(
                "return $L;",
                spec.optionalKind()
                    .presentExpression(
                        FrameworkType.PROVIDER.to(
                            spec.valueKind(), CodeBlock.of("$N", delegateField))))
            .build();

      case PRODUCER_NODE:
        getMethodBuilder.returns(listenableFutureOf(spec.optionalType()));

        switch (spec.valueKind()) {
          case FUTURE: // return a ListenableFuture<Optional<ListenableFuture<T>>>
          case PRODUCER: // return a ListenableFuture<Optional<Producer<T>>>
            return getMethodBuilder
                .addCode(
                    "return $T.immediateFuture($L);",
                    Futures.class,
                    spec.optionalKind()
                        .presentExpression(
                            FrameworkType.PRODUCER_NODE.to(
                                spec.valueKind(), CodeBlock.of("$N", delegateField))))
                .build();

          case INSTANCE: // return a ListenableFuture<Optional<T>>
            return getMethodBuilder
                .addCode(
                    "return $L;",
                    transformFutureToOptional(
                        spec.optionalKind(),
                        spec.typeVariable(),
                        CodeBlock.of("$N.get()", delegateField)))
                .build();

          case PRODUCED: // return a ListenableFuture<Optional<Produced<T>>>
            return getMethodBuilder
                .addCode(
                    "return $L;",
                    transformFutureToOptional(
                        spec.optionalKind(),
                        spec.valueType(),
                        CodeBlock.of(
                            "$T.createFutureProduced($N.get())", Producers.class, delegateField)))
                .build();

          default:
            throw new UnsupportedOperationException(
                spec.factoryType() + " objects are not supported");
        }
    }
    throw new AssertionError(spec.frameworkType());
  }

  /**
   * An expression that uses {@link Futures#transform(ListenableFuture, Function, Executor)} to
   * transform a {@code ListenableFuture<inputType>} into a {@code
   * ListenableFuture<Optional<inputType>>}.
   *
   * @param inputFuture an expression of type {@code ListenableFuture<inputType>}
   */
  private static CodeBlock transformFutureToOptional(
      OptionalKind optionalKind, TypeName inputType, CodeBlock inputFuture) {
    return CodeBlock.of(
        "$T.transform($L, $L, $T.directExecutor())",
        Futures.class,
        inputFuture,
        anonymousClassBuilder("")
            .addSuperinterface(
                ParameterizedTypeName.get(
                    ClassName.get(Function.class), inputType, optionalKind.of(inputType)))
            .addMethod(
                methodBuilder("apply")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(optionalKind.of(inputType))
                    .addParameter(inputType, "input")
                    .addCode("return $L;", optionalKind.presentExpression(CodeBlock.of("input")))
                    .build())
            .build(),
        MoreExecutors.class);
  }
}
