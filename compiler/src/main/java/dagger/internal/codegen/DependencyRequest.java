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

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.Optionals.firstPresent;
import static dagger.internal.codegen.TypeNames.lazyOf;
import static dagger.internal.codegen.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.TypeNames.producedOf;
import static dagger.internal.codegen.TypeNames.producerOf;
import static dagger.internal.codegen.TypeNames.providerOf;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.TypeName;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor7;

/**
 * Represents a request for a key at an injection point. Parameters to {@link Inject} constructors
 * or {@link Provides} methods are examples of key requests.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): Set bindings and the permutations thereof need to be addressed
@AutoValue
abstract class DependencyRequest {

  enum Kind {
    /** A default request for an instance.  E.g.: {@code Blah} */
    INSTANCE,

    /** A request for a {@link Provider}.  E.g.: {@code Provider<Blah>} */
    PROVIDER(Provider.class),

    /** A request for a {@link Lazy}.  E.g.: {@code Lazy<Blah>} */
    LAZY(Lazy.class),

    /** A request for a {@link Provider} of a {@link Lazy}. E.g.: {@code Provider<Lazy<Blah>>} */
    PROVIDER_OF_LAZY,

    /** A request for a {@link MembersInjector}.  E.g.: {@code MembersInjector<Blah>} */
    MEMBERS_INJECTOR(MembersInjector.class),

    /** A request for a {@link Producer}.  E.g.: {@code Producer<Blah>} */
    PRODUCER(Producer.class),

    /** A request for a {@link Produced}.  E.g.: {@code Produced<Blah>} */
    PRODUCED(Produced.class),

    /**
     * A request for a {@link ListenableFuture}.  E.g.: {@code ListenableFuture<Blah>}.
     * These can only be requested by component interfaces.
     */
    FUTURE,
    ;

    final Optional<Class<?>> frameworkClass;

    Kind(Class<?> frameworkClass) {
      this.frameworkClass = Optional.<Class<?>>of(frameworkClass);
    }

    Kind() {
      this.frameworkClass = Optional.empty();
    }

    /**
     * If {@code type}'s raw type is {@link #frameworkClass}, returns a {@link KindAndType} with
     * this kind that represents the dependency request.
     */
    Optional<KindAndType> from(TypeMirror type) {
      if (frameworkClass.isPresent() && isType(type) && isTypeOf(frameworkClass.get(), type)) {
        List<? extends TypeMirror> typeArguments = asDeclared(type).getTypeArguments();
        if (typeArguments.isEmpty()) {
          return Optional.empty();
        }
        return Optional.of(this.ofType(getOnlyElement(typeArguments)));
      }
      return Optional.empty();
    }

    /** Returns a {@link KindAndType} with this kind and {@code type} type. */
    KindAndType ofType(TypeMirror type) {
      return new AutoValue_DependencyRequest_KindAndType(this, type);
    }

    /** Returns the type of a request of this kind for a key with a given type. */
    TypeName typeName(TypeName keyType) {
      switch (this) {
        case INSTANCE:
          return keyType;

        case PROVIDER:
          return providerOf(keyType);

        case LAZY:
          return lazyOf(keyType);

        case PROVIDER_OF_LAZY:
          return providerOf(lazyOf(keyType));

        case PRODUCER:
          return producerOf(keyType);

        case PRODUCED:
          return producedOf(keyType);

        case FUTURE:
          return listenableFutureOf(keyType);

        default:
          throw new AssertionError(this);
      }
    }
  }

  abstract Kind kind();
  abstract Key key();
  
  BindingKey bindingKey() {
    switch (kind()) {
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PROVIDER_OF_LAZY:
      case PRODUCER:
      case PRODUCED:
      case FUTURE:
        return BindingKey.contribution(key());
      case MEMBERS_INJECTOR:
        return BindingKey.membersInjection(key());
      default:
        throw new AssertionError(this);
    }
  }

  /** The element that declares this dependency request. Absent for synthetic requests. */
  abstract Optional<Element> requestElement();

  /** Returns true if this request allows null objects. */
  abstract boolean isNullable();

  /**
   * An optional name for this request when it's referred to in generated code. If empty, it will
   * use a name derived from {@link #requestElement}.
   */
  abstract Optional<String> overriddenVariableName();

  private static DependencyRequest.Builder builder() {
    return new AutoValue_DependencyRequest.Builder().isNullable(false);
  }

  /**
   * Extracts the dependency request type and kind from the type of a dependency request element.
   * For example, if a user requests {@code Provider<Foo>}, this will return ({@link Kind#PROVIDER},
   * {@code Foo}).
   *
   * @throws TypeNotPresentException if {@code type}'s kind is {@link TypeKind#ERROR}, which may
   *     mean that the type will be generated in a later round of processing
   */
  static KindAndType extractKindAndType(TypeMirror type) {
    return type.accept(
        new SimpleTypeVisitor7<KindAndType, Void>() {
          @Override
          public KindAndType visitError(ErrorType errorType, Void p) {
            throw new TypeNotPresentException(errorType.toString(), null);
          }

          @Override
          public KindAndType visitExecutable(ExecutableType executableType, Void p) {
            return executableType.getReturnType().accept(this, null);
          }

          @Override
          public KindAndType visitDeclared(DeclaredType declaredType, Void p) {
            return KindAndType.from(declaredType).orElse(defaultAction(declaredType, p));
          }

          @Override
          protected KindAndType defaultAction(TypeMirror otherType, Void p) {
            return Kind.INSTANCE.ofType(otherType);
          }
        },
        null);
  }

  @AutoValue
  abstract static class KindAndType {
    abstract Kind kind();

    abstract TypeMirror type();

    static Optional<KindAndType> from(TypeMirror type) {
      for (Kind kind : Kind.values()) {
        Optional<KindAndType> kindAndType = kind.from(type);
        if (kindAndType.isPresent()) {
          return firstPresent(kindAndType.get().maybeProviderOfLazy(), kindAndType);
        }
      }
      return Optional.empty();
    }

    /**
     * If {@code kindAndType} represents a {@link Kind#PROVIDER} of a {@code Lazy<T>} for some type
     * {@code T}, then this method returns ({@link Kind#PROVIDER_OF_LAZY}, {@code T}).
     */
    private Optional<KindAndType> maybeProviderOfLazy() {
      if (kind().equals(Kind.PROVIDER)) {
        Optional<KindAndType> providedKindAndType = from(type());
        if (providedKindAndType.isPresent() && providedKindAndType.get().kind().equals(Kind.LAZY)) {
          return Optional.of(Kind.PROVIDER_OF_LAZY.ofType(providedKindAndType.get().type()));
        }
      }
      return Optional.empty();
    }
  }

  @CanIgnoreReturnValue
  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder kind(Kind kind);

    abstract Builder key(Key key);

    abstract Builder requestElement(Element element);

    abstract Builder isNullable(boolean isNullable);

    abstract Builder overriddenVariableName(Optional<String> overriddenVariableName);

    @CheckReturnValue
    abstract DependencyRequest build();
  }

  /**
   * Factory for {@link DependencyRequest}s.
   *
   * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available,
   * which may mean that the type will be generated in a later round of processing.
   */
  static final class Factory {
    private final Key.Factory keyFactory;

    Factory(Key.Factory keyFactory) {
      this.keyFactory = keyFactory;
    }

    ImmutableSet<DependencyRequest> forRequiredResolvedVariables(
        List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
      checkState(resolvedTypes.size() == variables.size());
      ImmutableSet.Builder<DependencyRequest> builder = ImmutableSet.builder();
      for (int i = 0; i < variables.size(); i++) {
        builder.add(forRequiredResolvedVariable(variables.get(i), resolvedTypes.get(i)));
      }
      return builder.build();
    }

    /**
     * Creates a implicit {@link DependencyRequest} for {@code mapOfFactoryKey}.
     *
     * @param mapOfFactoryKey a key equivalent to {@code mapOfValueRequest}'s key, whose type is
     *     {@code Map<K, Provider<V>>} or {@code Map<K, Producer<V>>}
     */
    DependencyRequest forImplicitMapBinding(Key mapOfFactoryKey) {
      return DependencyRequest.builder()
          .kind(Kind.PROVIDER)
          .key(mapOfFactoryKey)
          .build();
    }

    /**
     * Creates a synthetic dependency request for one individual {@code multibindingContribution}.
     */
    private DependencyRequest forMultibindingContribution(
        ContributionBinding multibindingContribution) {
      checkArgument(
          multibindingContribution.key().multibindingContributionIdentifier().isPresent(),
          "multibindingContribution's key must have a multibinding contribution identifier: %s",
          multibindingContribution);
      return DependencyRequest.builder()
          .kind(multibindingContributionRequestKind(multibindingContribution))
          .key(multibindingContribution.key())
          .build();
    }

    private Kind multibindingContributionRequestKind(ContributionBinding multibindingContribution) {
      switch (multibindingContribution.contributionType()) {
        case MAP:
          return multibindingContribution.bindingType().equals(BindingType.PRODUCTION)
              ? Kind.PRODUCER
              : Kind.PROVIDER;
        case SET:
        case SET_VALUES:
          return Kind.INSTANCE;
        case UNIQUE:
          throw new IllegalArgumentException(
              "multibindingContribution must be a multibinding: " + multibindingContribution);
        default:
          throw new AssertionError(multibindingContribution.toString());
      }
    }

    /**
     * Creates synthetic dependency requests for each individual multibinding contribution in {@code
     * multibindingContributions}.
     */
    ImmutableSet<DependencyRequest> forMultibindingContributions(
        Iterable<ContributionBinding> multibindingContributions) {
      ImmutableSet.Builder<DependencyRequest> requests = ImmutableSet.builder();
      for (ContributionBinding multibindingContribution : multibindingContributions) {
        requests.add(forMultibindingContribution(multibindingContribution));
      }
      return requests.build();
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      return forRequiredVariable(variableElement, Optional.empty());
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement, Optional<String> name) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, type, qualifier, name);
    }

    DependencyRequest forRequiredResolvedVariable(
        VariableElement variableElement, TypeMirror resolvedType) {
      checkNotNull(variableElement);
      checkNotNull(resolvedType);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, resolvedType, qualifier, Optional.empty());
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod,
        ExecutableType provisionMethodType) {
      checkNotNull(provisionMethod);
      checkNotNull(provisionMethodType);
      checkArgument(
          provisionMethod.getParameters().isEmpty(),
          "Component provision methods must be empty: %s",
          provisionMethod);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(
          provisionMethod, provisionMethodType.getReturnType(), qualifier, Optional.empty());
    }

    DependencyRequest forComponentProductionMethod(ExecutableElement productionMethod,
        ExecutableType productionMethodType) {
      checkNotNull(productionMethod);
      checkNotNull(productionMethodType);
      checkArgument(productionMethod.getParameters().isEmpty(),
          "Component production methods must be empty: %s", productionMethod);
      TypeMirror type = productionMethodType.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(productionMethod);
      // Only a component production method can be a request for a ListenableFuture, so we
      // special-case it here.
      if (isTypeOf(ListenableFuture.class, type)) {
        return DependencyRequest.builder()
            .kind(Kind.FUTURE)
            .key(keyFactory.forQualifiedType(
                qualifier, Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments())))
            .requestElement(productionMethod)
            .build();
      } else {
        return newDependencyRequest(productionMethod, type, qualifier, Optional.empty());
      }
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod,
        ExecutableType membersInjectionMethodType) {
      checkNotNull(membersInjectionMethod);
      checkNotNull(membersInjectionMethodType);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      TypeMirror returnType = membersInjectionMethodType.getReturnType();
      TypeMirror membersInjectedType =
          MoreTypes.isType(returnType) && MoreTypes.isTypeOf(MembersInjector.class, returnType)
              ? getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments())
              : getOnlyElement(membersInjectionMethodType.getParameterTypes());
      return DependencyRequest.builder()
          .kind(Kind.MEMBERS_INJECTOR)
          .key(keyFactory.forMembersInjectedType(membersInjectedType))
          .requestElement(membersInjectionMethod)
          .build();
    }

    DependencyRequest forMembersInjectedType(DeclaredType type) {
      return DependencyRequest.builder()
          .kind(Kind.MEMBERS_INJECTOR)
          .key(keyFactory.forMembersInjectedType(type))
          .requestElement(type.asElement())
          .build();
    }

    DependencyRequest forProductionImplementationExecutor() {
      Key key = keyFactory.forProductionImplementationExecutor();
      return DependencyRequest.builder()
          .kind(Kind.PROVIDER)
          .key(key)
          .requestElement(MoreTypes.asElement(key.type()))
          .build();
    }

    DependencyRequest forProductionComponentMonitor() {
      Key key = keyFactory.forProductionComponentMonitor();
      return DependencyRequest.builder()
          .kind(Kind.PROVIDER)
          .key(key)
          .requestElement(MoreTypes.asElement(key.type()))
          .overriddenVariableName(Optional.of("monitor"))
          .build();
    }

    /**
     * Returns a synthetic request for the present value of an optional binding generated from a
     * {@link dagger.BindsOptionalOf} declaration.
     */
    DependencyRequest forSyntheticPresentOptionalBinding(Key requestKey, Kind kind) {
      Optional<Key> key = keyFactory.unwrapOptional(requestKey);
      checkArgument(key.isPresent(), "not a request for optional: %s", requestKey);
      return builder()
          .kind(kind)
          .key(key.get())
          .isNullable(
              allowsNull(
                  extractKindAndType(OptionalType.from(requestKey).valueType()).kind(),
                  Optional.empty()))
          .build();
    }

    private DependencyRequest newDependencyRequest(
        Element requestElement,
        TypeMirror type,
        Optional<AnnotationMirror> qualifier,
        Optional<String> name) {
      KindAndType kindAndType = extractKindAndType(type);
      if (kindAndType.kind().equals(Kind.MEMBERS_INJECTOR)) {
        checkArgument(!qualifier.isPresent());
      }
      return DependencyRequest.builder()
          .kind(kindAndType.kind())
          .key(keyFactory.forQualifiedType(qualifier, kindAndType.type()))
          .requestElement(requestElement)
          .isNullable(allowsNull(kindAndType.kind(), getNullableType(requestElement)))
          .overriddenVariableName(name)
          .build();
    }

    /**
     * Returns {@code true} if a given request element allows null values. {@link Kind#INSTANCE}
     * requests must be annotated with {@code @Nullable} in order to allow null values. All other
     * request kinds implicitly allow null values because they are are wrapped inside {@link
     * Provider}, {@link Lazy}, etc.
     */
    // TODO(sameb): should Produced/Producer always require non-nullable?
    private boolean allowsNull(Kind kind, Optional<DeclaredType> nullableType) {
      return kind.equals(Kind.INSTANCE) ? nullableType.isPresent() : true;
    }
  }
}
