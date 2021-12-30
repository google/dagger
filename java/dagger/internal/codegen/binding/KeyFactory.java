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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ProducerAnnotations.productionImplementationQualifier;
import static dagger.internal.codegen.base.ProducerAnnotations.productionQualifier;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;
import static dagger.internal.codegen.binding.MapKeys.mapKeyType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.Optionals.firstPresent;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static dagger.internal.codegen.langmodel.DaggerTypes.unwrapType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.Arrays.asList;
import static javax.lang.model.element.ElementKind.METHOD;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreTypes;
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
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.multibindings.Multibinds;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerType;
import dagger.spi.model.Key;
import dagger.spi.model.Key.MultibindingContributionIdentifier;
import dagger.spi.model.RequestKind;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(
      XProcessingEnv processingEnv,
      DaggerTypes types,
      DaggerElements elements,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.types = types;
    this.elements = elements;
    this.injectionAnnotations = injectionAnnotations;
  }

  private TypeMirror boxPrimitives(TypeMirror type) {
    return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  private DeclaredType setOf(TypeMirror elementType) {
    return types.getDeclaredType(
        elements.getTypeElement(TypeNames.SET), boxPrimitives(elementType));
  }

  private DeclaredType mapOf(XType keyType, XType valueType) {
    return mapOf(toJavac(keyType), toJavac(valueType));
  }

  private DeclaredType mapOf(TypeMirror keyType, TypeMirror valueType) {
    return types.getDeclaredType(
        elements.getTypeElement(TypeNames.MAP), boxPrimitives(keyType), boxPrimitives(valueType));
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private TypeMirror mapOfFrameworkType(
      XType keyType, ClassName frameworkClassName, XType valueType) {
    return mapOfFrameworkType(toJavac(keyType), frameworkClassName, toJavac(valueType));
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private TypeMirror mapOfFrameworkType(
      TypeMirror keyType, ClassName frameworkClassName, TypeMirror valueType) {
    return mapOf(
        keyType,
        types.getDeclaredType(
            elements.getTypeElement(frameworkClassName), boxPrimitives(valueType)));
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
    return forProvidesMethod(toJavac(method), toJavac(contributingModule));
  }

  public Key forProvidesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PROVIDER));
  }

  public Key forProducesMethod(XMethodElement method, XTypeElement contributingModule) {
    return forProducesMethod(toJavac(method), toJavac(contributingModule));
  }

  public Key forProducesMethod(ExecutableElement method, TypeElement contributingModule) {
    return forBindingMethod(method, contributingModule, Optional.of(TypeNames.PRODUCER));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    return forBindsMethod(toJavac(method), toJavac(contributingModule));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(ExecutableElement method, TypeElement contributingModule) {
    checkArgument(isAnnotationPresent(method, TypeNames.BINDS));
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
    return forBindingMethod(toJavac(method), toJavac(contributingModule), frameworkClassName);
  }

  private Key forBindingMethod(
      ExecutableElement method,
      TypeElement contributingModule,
      Optional<ClassName> frameworkClassName) {
    checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), method));
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    TypeMirror returnType = methodType.getReturnType();
    if (frameworkClassName.isPresent()
        && frameworkClassName.get().equals(TypeNames.PRODUCER)
        && isType(returnType)) {
      if (isFutureType(methodType.getReturnType())) {
        returnType = getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && SetType.isSet(returnType)) {
        SetType setType = SetType.from(toXProcessing(returnType, processingEnv));
        if (isFutureType(setType.elementType())) {
          returnType =
              types.getDeclaredType(
                  elements.getTypeElement(TypeNames.SET),
                  toJavac(unwrapType(setType.elementType())));
        }
      }
    }
    TypeMirror keyType =
        bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
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
  Key forMultibindsMethod(XMethodElement method, XMethodType methodType) {
    XType returnType = method.getReturnType();
    TypeMirror keyType =
        MapType.isMap(returnType)
            ? mapOfFrameworkType(
                MapType.from(returnType).keyType(),
                TypeNames.PROVIDER,
                MapType.from(returnType).valueType())
            : toJavac(returnType);
    return forMethod(toJavac(method), keyType);
  }

  private TypeMirror bindingMethodKeyType(
      TypeMirror returnType,
      ExecutableElement method,
      ContributionType contributionType,
      Optional<ClassName> frameworkClassName) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
      case SET:
        return setOf(returnType);
      case MAP:
        Optional<AnnotationMirror> mapKey = getMapKey(method);
        // TODO(bcorso): We've added a special checkState here since a number of people have run
        // into this particular case, but technically it shouldn't be necessary if we are properly
        // doing superficial validation and deferring on unresolvable types. We should revisit
        // whether this is necessary once we're able to properly defer this case.
        checkState(
            mapKey.isPresent(),
            "Missing map key annotation for method: %s#%s. That method was annotated with: %s. If a"
                + " map key annotation is included in that list, it means Dagger wasn't able to"
                + " detect that it was a map key because the dependency is missing from the"
                + " classpath of the current build. To fix, add a dependency for the map key to the"
                + " current build. For more details, see"
                + " https://github.com/google/dagger/issues/3133#issuecomment-1002790894.",
            method.getEnclosingElement(),
            method,
            method.getAnnotationMirrors());
        TypeMirror mapKeyType = mapKeyType(toXProcessing(mapKey.get(), processingEnv));
        return frameworkClassName.isPresent()
            ? mapOfFrameworkType(mapKeyType, frameworkClassName.get(), returnType)
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
  Key forDelegateBinding(DelegateDeclaration delegateDeclaration, ClassName frameworkType) {
    return delegateDeclaration.contributionType().equals(ContributionType.MAP)
        ? wrapMapValue(delegateDeclaration.key(), frameworkType)
        : delegateDeclaration.key();
  }

  private Key forMethod(XMethodElement method, XType keyType) {
    return forMethod(toJavac(method), toJavac(keyType));
  }

  private Key forMethod(ExecutableElement method, TypeMirror keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(XType type) {
    return forInjectConstructorWithResolvedType(toJavac(type));
  }

  public Key forInjectConstructorWithResolvedType(TypeMirror type) {
    return Key.builder(fromJava(type)).build();
  }

  // TODO(ronshapiro): Remove these conveniences which are simple wrappers around Key.Builder
  Key forType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  public Key forMembersInjectedType(TypeMirror type) {
    return forMembersInjectedType(toXProcessing(type, processingEnv));
  }

  public Key forMembersInjectedType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return forQualifiedType(
        qualifier.map(annotation -> toXProcessing(annotation, processingEnv)),
        toXProcessing(type, processingEnv));
  }

  Key forQualifiedType(Optional<XAnnotation> qualifier, XType type) {
    return Key.builder(DaggerType.from(type.boxed()))
        .qualifier(qualifier.map(DaggerAnnotation::from))
        .build();
  }

  public Key forProductionExecutor() {
    return Key.builder(fromJava(elements.getTypeElement(TypeNames.EXECUTOR).asType()))
        .qualifier(fromJava(toJavac(productionQualifier(processingEnv))))
        .build();
  }

  public Key forProductionImplementationExecutor() {
    return Key.builder(fromJava(elements.getTypeElement(TypeNames.EXECUTOR).asType()))
        .qualifier(fromJava(toJavac(productionImplementationQualifier(processingEnv))))
        .build();
  }

  public Key forProductionComponentMonitor() {
    return Key.builder(
            fromJava(elements.getTypeElement(TypeNames.PRODUCTION_COMPONENT_MONITOR).asType()))
        .build();
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
        rewrapMapKey(possibleMapKey, TypeNames.PRODUCED, TypeNames.PROVIDER),
        wrapMapKey(possibleMapKey, TypeNames.PROVIDER));
  }

  /**
   * Optionally extract a {@link Key} for the underlying production binding(s) if such a
   * valid key can be inferred from the given key.  Specifically, if the key represents a
   * {@link Map}{@code <K, V>} or {@code Map<K, Produced<V>>}, a key of
   * {@code Map<K, Producer<V>>} will be returned.
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
            return key.toBuilder()
                .type(
                    fromJava(mapOf(mapType.keyType(), mapType.unwrappedValueType(frameworkClass))))
                .build();
          }
        }
      }
    }
    return key;
  }

  /** Converts a {@link Key} of type {@code Map<K, V>} to {@code Map<K, Provider<V>>}. */
  private Key wrapMapValue(Key key, ClassName newWrappingClassName) {
    checkArgument(
        FrameworkTypes.isFrameworkType(elements.getTypeElement(newWrappingClassName).asType()));
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
        TypeElement wrappingElement = elements.getTypeElement(newWrappingClassName);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        DeclaredType wrappedValueType =
            types.getDeclaredType(
                wrappingElement, toJavac(mapType.unwrappedValueType(currentWrappingClassName)));
        return Optional.of(
            possibleMapKey.toBuilder()
                .type(fromJava(mapOf(toJavac(mapType.keyType()), wrappedValueType)))
                .build());
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
        TypeElement wrappingElement = elements.getTypeElement(wrappingClassName);
        if (wrappingElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return Optional.empty();
        }
        DeclaredType wrappedValueType =
            types.getDeclaredType(wrappingElement, toJavac(mapType.valueType()));
        return Optional.of(
            possibleMapKey.toBuilder()
                .type(fromJava(mapOf(toJavac(mapType.keyType()), wrappedValueType)))
                .build());
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
            key.toBuilder()
                .type(fromJava(setOf(toJavac(setType.unwrappedElementType(wrappingClassName)))))
                .build());
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

    XType optionalValueType = OptionalType.from(key).valueType();
    return Optional.of(
        key.toBuilder().type(DaggerType.from(extractKeyType(optionalValueType))).build());
  }

  private DaggerAnnotation fromJava(AnnotationMirror annotation) {
    return DaggerAnnotation.from(toXProcessing(annotation, processingEnv));
  }

  private DaggerType fromJava(TypeMirror typeMirror) {
    return DaggerType.from(toXProcessing(typeMirror, processingEnv));
  }
}
