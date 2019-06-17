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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MapKeys.mapKeyType;
import static dagger.internal.codegen.Optionals.firstPresent;
import static dagger.internal.codegen.RequestKinds.extractKeyType;
import static dagger.internal.codegen.RequestKinds.getRequestKind;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static java.util.Arrays.asList;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.model.Key.MultibindingContributionIdentifier;
import dagger.model.RequestKind;
import dagger.multibindings.Multibinds;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.Production;
import dagger.producers.internal.ProductionImplementation;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link Key}s. */
final class KeyFactory {
  private final DaggerTypes types;
  private final DaggerElements elements;

  @Inject
  KeyFactory(DaggerTypes types, DaggerElements elements) {
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
  }

  private TypeMirror boxPrimitives(TypeMirror type) {
    return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  private DeclaredType setOf(TypeMirror elementType) {
    return types.getDeclaredType(elements.getTypeElement(Set.class), boxPrimitives(elementType));
  }

  private DeclaredType mapOf(TypeMirror keyType, TypeMirror valueType) {
    return types.getDeclaredType(
        elements.getTypeElement(Map.class), boxPrimitives(keyType), boxPrimitives(valueType));
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private TypeMirror mapOfFrameworkType(
      TypeMirror keyType, TypeElement frameworkType, TypeMirror valueType) {
    return mapOf(keyType, types.getDeclaredType(frameworkType, boxPrimitives(valueType)));
  }

  Key forComponentMethod(ExecutableElement componentMethod) {
    checkArgument(componentMethod.getKind().equals(METHOD));
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forProductionComponentMethod(ExecutableElement componentMethod) {
    checkArgument(componentMethod.getKind().equals(METHOD));
    TypeMirror returnType = componentMethod.getReturnType();
    TypeMirror keyType =
        isFutureType(returnType)
            ? getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments())
            : returnType;
    return forMethod(componentMethod, keyType);
  }

  Key forSubcomponentCreatorMethod(
      ExecutableElement subcomponentCreatorMethod, DeclaredType declaredContainer) {
    checkArgument(subcomponentCreatorMethod.getKind().equals(METHOD));
    ExecutableType resolvedMethod =
        asExecutable(types.asMemberOf(declaredContainer, subcomponentCreatorMethod));
    return Key.builder(resolvedMethod.getReturnType()).build();
  }

  Key forSubcomponentCreator(TypeMirror creatorType) {
    return Key.builder(creatorType).build();
  }

  Key forProvidesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(
        method, contributingModule, Optional.of(elements.getTypeElement(Provider.class)));
  }

  Key forProducesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(
        method, contributingModule, Optional.of(elements.getTypeElement(Producer.class)));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(ExecutableElement method, TypeElement contributingModule) {
    checkArgument(isAnnotationPresent(method, Binds.class));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  /** Returns the base key bound by a {@link BindsOptionalOf} method. */
  Key forBindsOptionalOfMethod(ExecutableElement method, TypeElement contributingModule) {
    checkArgument(isAnnotationPresent(method, BindsOptionalOf.class));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  private Key forBindingMethod(
      ExecutableElement method,
      TypeElement contributingModule,
      Optional<TypeElement> frameworkType) {
    checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), method));
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    TypeMirror returnType = methodType.getReturnType();
    if (frameworkType.isPresent()
        && frameworkType.get().equals(elements.getTypeElement(Producer.class))
        && isType(returnType)) {
      if (isFutureType(methodType.getReturnType())) {
        returnType = getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && SetType.isSet(returnType)) {
        SetType setType = SetType.from(returnType);
        if (isFutureType(setType.elementType())) {
          returnType =
              types.getDeclaredType(
                  elements.getTypeElement(Set.class), types.unwrapType(setType.elementType()));
        }
      }
    }
    TypeMirror keyType = bindingMethodKeyType(returnType, method, contributionType, frameworkType);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.toBuilder()
            .multibindingContributionIdentifier(
                new MultibindingContributionIdentifier(method, contributingModule))
            .build();
  }

  /**
   * Returns the key for a {@link Multibinds @Multibinds} method.
   *
   * <p>The key's type is either {@code Set<T>} or {@code Map<K, Provider<V>>}. The latter works
   * even for maps used by {@code Producer}s.
   */
  Key forMultibindsMethod(ExecutableType executableType, ExecutableElement method) {
    checkArgument(method.getKind().equals(METHOD), "%s must be a method", method);
    TypeMirror returnType = executableType.getReturnType();
    TypeMirror keyType =
        MapType.isMap(returnType)
            ? mapOfFrameworkType(
                MapType.from(returnType).keyType(),
                elements.getTypeElement(Provider.class),
                MapType.from(returnType).valueType())
            : returnType;
    return forMethod(method, keyType);
  }

  private TypeMirror bindingMethodKeyType(
      TypeMirror returnType,
      ExecutableElement method,
      ContributionType contributionType,
      Optional<TypeElement> frameworkType) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
      case SET:
        return setOf(returnType);
      case MAP:
        TypeMirror mapKeyType = mapKeyType(getMapKey(method).get(), types);
        return frameworkType.isPresent()
            ? mapOfFrameworkType(mapKeyType, frameworkType.get(), returnType)
            : mapOf(mapKeyType, returnType);
      case SET_VALUES:
        // TODO(gak): do we want to allow people to use "covariant return" here?
        checkArgument(SetType.isSet(returnType));
        return returnType;
    }
    throw new AssertionError();
  }

  /**
   * Returns the key for a binding associated with a {@link DelegateDeclaration}.
   *
   * <p>If {@code delegateDeclaration} is {@code @IntoMap}, transforms the {@code Map<K, V>} key
   * from {@link DelegateDeclaration#key()} to {@code Map<K, FrameworkType<V>>}. If {@code
   * delegateDeclaration} is not a map contribution, its key is returned.
   */
  Key forDelegateBinding(DelegateDeclaration delegateDeclaration, Class<?> frameworkType) {
    return delegateDeclaration.contributionType().equals(ContributionType.MAP)
        ? wrapMapValue(delegateDeclaration.key(), frameworkType)
        : delegateDeclaration.key();
  }

  private Key forMethod(ExecutableElement method, TypeMirror keyType) {
    return forQualifiedType(getQualifier(method), keyType);
  }

  Key forInjectConstructorWithResolvedType(TypeMirror type) {
    return Key.builder(type).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(TypeMirror type) {
    return Key.builder(type).build();
  }

  Key forMembersInjectedType(TypeMirror type) {
    return Key.builder(type).build();
  }

  Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return Key.builder(boxPrimitives(type)).qualifier(qualifier).build();
  }

  Key forProductionExecutor() {
    return Key.builder(elements.getTypeElement(Executor.class).asType())
        .qualifier(SimpleAnnotationMirror.of(elements.getTypeElement(Production.class)))
        .build();
  }

  Key forProductionImplementationExecutor() {
    return Key.builder(elements.getTypeElement(Executor.class).asType())
        .qualifier(SimpleAnnotationMirror.of(elements.getTypeElement(ProductionImplementation.class)))
        .build();
  }

  Key forProductionComponentMonitor() {
    return Key.builder(elements.getTypeElement(ProductionComponentMonitor.class).asType()).build();
  }

  /**
   * If {@code requestKey} is for a {@code Map<K, V>} or {@code Map<K, Produced<V>>}, returns keys
   * for {@code Map<K, Provider<V>>} and {@code Map<K, Producer<V>>} (if Dagger-Producers is on
   * the classpath).
   */
  ImmutableSet<Key> implicitFrameworkMapKeys(Key requestKey) {
    return Stream.of(implicitMapProviderKeyFrom(requestKey), implicitMapProducerKeyFrom(requestKey))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /**
   * Optionally extract a {@link Key} for the underlying provision binding(s) if such a valid key
   * can be inferred from the given key. Specifically, if the key represents a {@link Map}{@code
   * <K, V>} or {@code Map<K, Producer<V>>}, a key of {@code Map<K, Provider<V>>} will be
   * returned.
   */
  Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
    return firstPresent(
        rewrapMapKey(possibleMapKey, Produced.class, Provider.class),
        wrapMapKey(possibleMapKey, Provider.class));
  }

  /**
   * Optionally extract a {@link Key} for the underlying production binding(s) if such a
   * valid key can be inferred from the given key.  Specifically, if the key represents a
   * {@link Map}{@code <K, V>} or {@code Map<K, Produced<V>>}, a key of
   * {@code Map<K, Producer<V>>} will be returned.
   */
  Optional<Key> implicitMapProducerKeyFrom(Key possibleMapKey) {
    return firstPresent(
        rewrapMapKey(possibleMapKey, Produced.class, Producer.class),
        wrapMapKey(possibleMapKey, Producer.class));
  }

  /**
   * If {@code key}'s type is {@code Map<K, Provider<V>>}, {@code Map<K, Producer<V>>}, or {@code
   * Map<K, Produced<V>>}, returns a key with the same qualifier and {@link
   * Key#multibindingContributionIdentifier()} whose type is simply {@code Map<K, V>}.
   *
   * <p>Otherwise, returns {@code key}.
   */
  Key unwrapMapValueType(Key key) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (!mapType.isRawType()) {
        for (Class<?> frameworkClass : asList(Provider.class, Producer.class, Produced.class)) {
          if (mapType.valuesAreTypeOf(frameworkClass)) {
            return key.toBuilder()
                .type(mapOf(mapType.keyType(), mapType.unwrappedValueType(frameworkClass)))
                .build();
          }
        }
      }
    }
    return key;
  }

  /**
   * Converts a {@link Key} of type {@code Map<K, V>} to {@code Map<K, Provider<V>>}.
   */
  private Key wrapMapValue(Key key, Class<?> newWrappingClass) {
    checkArgument(
        FrameworkTypes.isFrameworkType(elements.getTypeElement(newWrappingClass).asType()));
    return wrapMapKey(key, newWrappingClass).get();
  }

  /**
   * If {@code key}'s type is {@code Map<K, CurrentWrappingClass<Bar>>}, returns a key with type
   * {@code Map<K, NewWrappingClass<Bar>>} with the same qualifier. Otherwise returns {@link
   * Optional#empty()}.
   *
   * <p>Returns {@link Optional#empty()} if {@code newWrappingClass} is not in the classpath.
   *
   * @throws IllegalArgumentException if {@code newWrappingClass} is the same as {@code
   *     currentWrappingClass}
   */
  Optional<Key> rewrapMapKey(
      Key possibleMapKey, Class<?> currentWrappingClass, Class<?> newWrappingClass) {
    checkArgument(!currentWrappingClass.equals(newWrappingClass));
    if (MapType.isMap(possibleMapKey)) {
      MapType mapType = MapType.from(possibleMapKey);
      if (!mapType.isRawType() && mapType.valuesAreTypeOf(currentWrappingClass)) {
        TypeElement wrappingElement = elements.getTypeElement(newWrappingClass);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        DeclaredType wrappedValueType =
            types.getDeclaredType(
                wrappingElement, mapType.unwrappedValueType(currentWrappingClass));
        return Optional.of(
            possibleMapKey.toBuilder().type(mapOf(mapType.keyType(), wrappedValueType)).build());
      }
    }
    return Optional.empty();
  }

  /**
   * If {@code key}'s type is {@code Map<K, Foo>} and {@code Foo} is not {@code WrappingClass
   * <Bar>}, returns a key with type {@code Map<K, WrappingClass<Foo>>} with the same qualifier.
   * Otherwise returns {@link Optional#empty()}.
   *
   * <p>Returns {@link Optional#empty()} if {@code WrappingClass} is not in the classpath.
   */
  private Optional<Key> wrapMapKey(Key possibleMapKey, Class<?> wrappingClass) {
    if (MapType.isMap(possibleMapKey)) {
      MapType mapType = MapType.from(possibleMapKey);
      if (!mapType.isRawType() && !mapType.valuesAreTypeOf(wrappingClass)) {
        TypeElement wrappingElement = elements.getTypeElement(wrappingClass);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        DeclaredType wrappedValueType = types.getDeclaredType(wrappingElement, mapType.valueType());
        return Optional.of(
            possibleMapKey.toBuilder().type(mapOf(mapType.keyType(), wrappedValueType)).build());
      }
    }
    return Optional.empty();
  }

  /**
   * If {@code key}'s type is {@code Set<WrappingClass<Bar>>}, returns a key with type {@code Set
   * <Bar>} with the same qualifier. Otherwise returns {@link Optional#empty()}.
   */
  Optional<Key> unwrapSetKey(Key key, Class<?> wrappingClass) {
    if (SetType.isSet(key)) {
      SetType setType = SetType.from(key);
      if (!setType.isRawType() && setType.elementsAreTypeOf(wrappingClass)) {
        return Optional.of(
            key.toBuilder().type(setOf(setType.unwrappedElementType(wrappingClass))).build());
      }
    }
    return Optional.empty();
  }

  /**
   * If {@code key}'s type is {@code Optional<T>} for some {@code T}, returns a key with the same
   * qualifier whose type is {@linkplain RequestKinds#extractKeyType(RequestKind, TypeMirror)}
   * extracted} from {@code T}.
   */
  Optional<Key> unwrapOptional(Key key) {
    if (!OptionalType.isOptional(key)) {
      return Optional.empty();
    }

    TypeMirror optionalValueType = OptionalType.from(key).valueType();
    return Optional.of(
        key.toBuilder()
            .type(extractKeyType(getRequestKind(optionalValueType), optionalValueType))
            .build());
  }
}
