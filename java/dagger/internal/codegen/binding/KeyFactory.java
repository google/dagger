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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ProducerAnnotations.productionImplementationQualifier;
import static dagger.internal.codegen.base.ProducerAnnotations.productionQualifier;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.Optionals.firstPresent;
import static dagger.internal.codegen.javapoet.TypeNames.isFutureType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;
import static java.util.Arrays.asList;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.FrameworkTypes;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.RequestKinds;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.multibindings.Multibinds;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerExecutableElement;
import dagger.spi.model.DaggerType;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** A factory for {@link Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(XProcessingEnv processingEnv, InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.injectionAnnotations = injectionAnnotations;
  }

  private XType setOf(XType elementType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(TypeNames.SET), elementType.boxed());
  }

  private XType mapOf(XType keyType, XType valueType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(TypeNames.MAP), keyType.boxed(), valueType.boxed());
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private XType mapOfFrameworkType(XType keyType, ClassName frameworkClassName, XType valueType) {
    return mapOf(
        keyType,
        processingEnv.getDeclaredType(
            processingEnv.requireTypeElement(frameworkClassName), valueType.boxed()));
  }

  Key forComponentMethod(XMethodElement componentMethod) {
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forProductionComponentMethod(XMethodElement componentMethod) {
    XType returnType = componentMethod.getReturnType();
    XType keyType =
        isFutureType(returnType) ? getOnlyElement(returnType.getTypeArguments()) : returnType;
    return forMethod(componentMethod, keyType);
  }

  Key forSubcomponentCreatorMethod(
      XMethodElement subcomponentCreatorMethod, XType declaredContainer) {
    checkArgument(isDeclared(declaredContainer));
    XMethodType resolvedMethod = subcomponentCreatorMethod.asMemberOf(declaredContainer);
    return Key.builder(DaggerType.from(resolvedMethod.getReturnType())).build();
  }

  public Key forSubcomponentCreator(XType creatorType) {
    return Key.builder(DaggerType.from(creatorType)).build();
  }

  public Key forProvidesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PROVIDER));
  }

  public Key forProducesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PRODUCER));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(TypeNames.BINDS));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  /** Returns the base key bound by a {@link BindsOptionalOf} method. */
  Key forBindsOptionalOfMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(TypeNames.BINDS_OPTIONAL_OF));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  private Key forBindingMethod(
      XMethodElement method,
      XTypeElement contributingModule,
      Optional<ClassName> frameworkClassName) {
    XMethodType methodType = method.asMemberOf(contributingModule.getType());
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    XType returnType = methodType.getReturnType();
    if (frameworkClassName.isPresent() && frameworkClassName.get().equals(TypeNames.PRODUCER)) {
      if (isFutureType(returnType)) {
        returnType = getOnlyElement(returnType.getTypeArguments());
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && SetType.isSet(returnType)) {
        SetType setType = SetType.from(returnType);
        if (isFutureType(setType.elementType())) {
          returnType = setOf(unwrapType(setType.elementType()));
        }
      }
    }
    XType keyType = bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.withMultibindingContributionIdentifier(
            DaggerTypeElement.from(contributingModule), DaggerExecutableElement.from(method));
  }

  /**
   * Returns the key for a {@link Multibinds @Multibinds} method.
   *
   * <p>The key's type is either {@code Set<T>} or {@code Map<K, Provider<V>>}. The latter works
   * even for maps used by {@code Producer}s.
   */
  Key forMultibindsMethod(XMethodElement method, XMethodType methodType) {
    XType returnType = method.getReturnType();
    XType keyType =
        MapType.isMap(returnType)
            ? mapOfFrameworkType(
                MapType.from(returnType).keyType(),
                TypeNames.PROVIDER,
                MapType.from(returnType).valueType())
            : returnType;
    return forMethod(method, keyType);
  }

  private XType bindingMethodKeyType(
      XType returnType,
      XMethodElement method,
      ContributionType contributionType,
      Optional<ClassName> frameworkClassName) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
      case SET:
        return setOf(returnType);
      case MAP:
        Optional<XType> mapKeyType = getMapKey(method).map(MapKeys::mapKeyType);
        // TODO(bcorso): We've added a special checkState here since a number of people have run
        // into this particular case, but technically it shouldn't be necessary if we are properly
        // doing superficial validation and deferring on unresolvable types. We should revisit
        // whether this is necessary once we're able to properly defer this case.
        checkState(
            mapKeyType.isPresent(),
            "Missing map key annotation for method: %s#%s. That method was annotated with: %s. If a"
                + " map key annotation is included in that list, it means Dagger wasn't able to"
                + " detect that it was a map key because the dependency is missing from the"
                + " classpath of the current build. To fix, add a dependency for the map key to the"
                + " current build. For more details, see"
                + " https://github.com/google/dagger/issues/3133#issuecomment-1002790894.",
            method.getEnclosingElement(),
            method,
            method.getAllAnnotations().stream()
                .map(XAnnotations::toString)
                .collect(toImmutableList()));
        return frameworkClassName.isPresent()
            ? mapOfFrameworkType(mapKeyType.get(), frameworkClassName.get(), returnType)
            : mapOf(mapKeyType.get(), returnType);
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
  Key forDelegateBinding(DelegateDeclaration delegateDeclaration, ClassName frameworkType) {
    return delegateDeclaration.contributionType().equals(ContributionType.MAP)
        ? wrapMapValue(delegateDeclaration.key(), frameworkType)
        : delegateDeclaration.key();
  }

  private Key forMethod(XMethodElement method, XType keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  public Key forMembersInjectedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  Key forQualifiedType(Optional<XAnnotation> qualifier, XType type) {
    return Key.builder(DaggerType.from(type.boxed()))
        .qualifier(qualifier.map(DaggerAnnotation::from))
        .build();
  }

  public Key forProductionExecutor() {
    return Key.builder(DaggerType.from(processingEnv.requireType(TypeNames.EXECUTOR)))
        .qualifier(DaggerAnnotation.from(productionQualifier(processingEnv)))
        .build();
  }

  public Key forProductionImplementationExecutor() {
    return Key.builder(DaggerType.from(processingEnv.requireType(TypeNames.EXECUTOR)))
        .qualifier(DaggerAnnotation.from(productionImplementationQualifier(processingEnv)))
        .build();
  }

  public Key forProductionComponentMonitor() {
    return Key.builder(
            DaggerType.from(processingEnv.requireType(TypeNames.PRODUCTION_COMPONENT_MONITOR)))
        .build();
  }

  /**
   * If {@code requestKey} is for a {@code Map<K, V>} or {@code Map<K, Produced<V>>}, returns keys
   * for {@code Map<K, Provider<V>>} and {@code Map<K, Producer<V>>} (if Dagger-Producers is on the
   * classpath).
   */
  ImmutableSet<Key> implicitFrameworkMapKeys(Key requestKey) {
    return Stream.of(implicitMapProviderKeyFrom(requestKey), implicitMapProducerKeyFrom(requestKey))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /**
   * Optionally extract a {@link Key} for the underlying provision binding(s) if such a valid key
   * can be inferred from the given key. Specifically, if the key represents a {@link Map}{@code <K,
   * V>} or {@code Map<K, Producer<V>>}, a key of {@code Map<K, Provider<V>>} will be returned.
   */
  Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
    return firstPresent(
        rewrapMapKey(possibleMapKey, TypeNames.PRODUCED, TypeNames.PROVIDER),
        wrapMapKey(possibleMapKey, TypeNames.PROVIDER));
  }

  /**
   * Optionally extract a {@link Key} for the underlying production binding(s) if such a valid key
   * can be inferred from the given key. Specifically, if the key represents a {@link Map}{@code <K,
   * V>} or {@code Map<K, Produced<V>>}, a key of {@code Map<K, Producer<V>>} will be returned.
   */
  Optional<Key> implicitMapProducerKeyFrom(Key possibleMapKey) {
    return firstPresent(
        rewrapMapKey(possibleMapKey, TypeNames.PRODUCED, TypeNames.PRODUCER),
        wrapMapKey(possibleMapKey, TypeNames.PRODUCER));
  }

  /**
   * If {@code key}'s type is {@code Map<K, Provider<V>>}, {@code Map<K, Producer<V>>}, or {@code
   * Map<K, Produced<V>>}, returns a key with the same qualifier and {@link
   * Key#multibindingContributionIdentifier()} whose type is simply {@code Map<K, V>}.
   *
   * <p>Otherwise, returns {@code key}.
   */
  public Key unwrapMapValueType(Key key) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (!mapType.isRawType()) {
        for (ClassName frameworkClass :
            asList(TypeNames.PROVIDER, TypeNames.PRODUCER, TypeNames.PRODUCED)) {
          if (mapType.valuesAreTypeOf(frameworkClass)) {
            return key.withType(
                DaggerType.from(
                    mapOf(mapType.keyType(), mapType.unwrappedValueType(frameworkClass))));
          }
        }
      }
    }
    return key;
  }

  /** Converts a {@link Key} of type {@code Map<K, V>} to {@code Map<K, Provider<V>>}. */
  private Key wrapMapValue(Key key, ClassName newWrappingClassName) {
    checkArgument(FrameworkTypes.isFrameworkType(processingEnv.requireType(newWrappingClassName)));
    return wrapMapKey(key, newWrappingClassName).get();
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
  public Optional<Key> rewrapMapKey(
      Key possibleMapKey, ClassName currentWrappingClassName, ClassName newWrappingClassName) {
    checkArgument(!currentWrappingClassName.equals(newWrappingClassName));
    if (MapType.isMap(possibleMapKey)) {
      MapType mapType = MapType.from(possibleMapKey);
      if (!mapType.isRawType() && mapType.valuesAreTypeOf(currentWrappingClassName)) {
        XTypeElement wrappingElement = processingEnv.findTypeElement(newWrappingClassName);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        XType wrappedValueType =
            processingEnv.getDeclaredType(
                wrappingElement, mapType.unwrappedValueType(currentWrappingClassName));
        return Optional.of(
            possibleMapKey.withType(DaggerType.from(mapOf(mapType.keyType(), wrappedValueType))));
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
  private Optional<Key> wrapMapKey(Key possibleMapKey, ClassName wrappingClassName) {
    if (MapType.isMap(possibleMapKey)) {
      MapType mapType = MapType.from(possibleMapKey);
      if (!mapType.isRawType() && !mapType.valuesAreTypeOf(wrappingClassName)) {
        XTypeElement wrappingElement = processingEnv.findTypeElement(wrappingClassName);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        XType wrappedValueType =
            processingEnv.getDeclaredType(wrappingElement, mapType.valueType());
        return Optional.of(
            possibleMapKey.withType(DaggerType.from(mapOf(mapType.keyType(), wrappedValueType))));
      }
    }
    return Optional.empty();
  }

  /**
   * If {@code key}'s type is {@code Set<WrappingClass<Bar>>}, returns a key with type {@code Set
   * <Bar>} with the same qualifier. Otherwise returns {@link Optional#empty()}.
   */
  Optional<Key> unwrapSetKey(Key key, ClassName wrappingClassName) {
    if (SetType.isSet(key)) {
      SetType setType = SetType.from(key);
      if (!setType.isRawType() && setType.elementsAreTypeOf(wrappingClassName)) {
        return Optional.of(
            key.withType(DaggerType.from(setOf(setType.unwrappedElementType(wrappingClassName)))));
      }
    }
    return Optional.empty();
  }

  /**
   * If {@code key}'s type is {@code Optional<T>} for some {@code T}, returns a key with the same
   * qualifier whose type is {@linkplain RequestKinds#extractKeyType(RequestKind, XType)}
   * extracted} from {@code T}.
   */
  Optional<Key> unwrapOptional(Key key) {
    if (!OptionalType.isOptional(key)) {
      return Optional.empty();
    }

    XType optionalValueType = OptionalType.from(key).valueType();
    return Optional.of(key.withType(DaggerType.from(extractKeyType(optionalValueType))));
  }
}
