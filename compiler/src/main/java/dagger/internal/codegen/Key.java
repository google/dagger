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
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MapKeys.getUnwrappedMapKeyType;
import static dagger.internal.codegen.MoreAnnotationMirrors.unwrapOptionalEquivalence;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static dagger.internal.codegen.Optionals.firstPresent;
import static dagger.internal.codegen.Util.toImmutableSet;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.multibindings.Multibinds;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.Production;
import dagger.producers.internal.ProductionImplementation;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Represents a unique combination of {@linkplain TypeMirror type} and
 * {@linkplain Qualifier qualifier} to which binding can occur.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {

  /** An object that is associated with a {@link Key}. */
  interface HasKey {
    /** The key associated with this object. */
    Key key();
  }

  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   *
   * Despite documentation in {@link AnnotationMirror}, equals and hashCode aren't implemented
   * to represent logical equality, so {@link AnnotationMirrors#equivalence()}
   * provides this facility.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier();

  /**
   * The type represented by this key.
   *
   * As documented in {@link TypeMirror}, equals and hashCode aren't implemented to represent
   * logical equality, so {@link MoreTypes#equivalence()} wraps this type.
   */
  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

  /**
   * Distinguishes keys for multibinding contributions that share a {@link #type()} and {@link
   * #qualifier()}.
   *
   * <p>Each multibound map and set has a {@linkplain
   * ProvisionBinding.Factory#syntheticMultibinding(Key, Iterable) synthetic multibinding} that
   * depends on the specific contributions to that map or set using keys that identify those
   * multibinding contributions.
   *
   * <p>Absent except for multibinding contributions.
   */
  abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();
  
  abstract Builder toBuilder();

  @Memoized
  @Override
  public abstract int hashCode();

  static Builder builder(TypeMirror type) {
    return new AutoValue_Key.Builder().type(type);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder wrappedType(Equivalence.Wrapper<TypeMirror> wrappedType);

    Builder type(TypeMirror type) {
      return wrappedType(MoreTypes.equivalence().wrap(checkNotNull(type)));
    }

    abstract Builder wrappedQualifier(
        Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier);

    abstract Builder wrappedQualifier(Equivalence.Wrapper<AnnotationMirror> wrappedQualifier);

    Builder qualifier(AnnotationMirror qualifier) {
      return wrappedQualifier(AnnotationMirrors.equivalence().wrap(checkNotNull(qualifier)));
    }

    Builder qualifier(Optional<AnnotationMirror> qualifier) {
      return wrappedQualifier(wrapOptionalInEquivalence(checkNotNull(qualifier)));
    }

    Builder qualifier(TypeElement annotationType) {
      return qualifier(SimpleAnnotationMirror.of(annotationType));
    }

    abstract Builder multibindingContributionIdentifier(
        Optional<MultibindingContributionIdentifier> identifier);

    abstract Builder multibindingContributionIdentifier(
        MultibindingContributionIdentifier identifier);

    abstract Key build();
  }
  
  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see Key#multibindingContributionIdentifier()
   */
  static final class MultibindingContributionIdentifier {
    private final String identifierString;

    MultibindingContributionIdentifier(
        ExecutableElement bindingMethod, TypeElement contributingModule) {
      this.identifierString =
          String.format(
              "%s#%s", contributingModule.getQualifiedName(), bindingMethod.getSimpleName());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned string is human-readable and distinguishes the keys in the same way as the
     * whole object.
     */
    @Override
    public String toString() {
      return identifierString;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MultibindingContributionIdentifier
          && ((MultibindingContributionIdentifier) obj)
              .identifierString.equals(this.identifierString);
    }

    @Override
    public int hashCode() {
      return identifierString.hashCode();
    }
  }

  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   */
  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(wrappedQualifier());
  }

  /**
   * The type represented by this key.
   */
  TypeMirror type() {
    return wrappedType().get();
  }

  /**
   * A key whose {@link #qualifier()} and {@link #type()} are equivalent to this one's, but without
   * a {@link #multibindingContributionIdentifier()}.
   */
  Key withoutMultibindingContributionIdentifier() {
    return toBuilder().multibindingContributionIdentifier(Optional.empty()).build();
  }

  boolean isValidMembersInjectionKey() {
    return !qualifier().isPresent() && !type().getKind().equals(TypeKind.WILDCARD);
  }

  /**
   * Returns {@code true} if this is valid as an implicit key (that is, if it's valid for a
   * just-in-time binding by discovering an {@code @Inject} constructor).
   */
  boolean isValidImplicitProvisionKey(Types types) {
    return isValidImplicitProvisionKey(qualifier(), type(), types);
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  static boolean isValidImplicitProvisionKey(
      Optional<? extends AnnotationMirror> qualifier, TypeMirror type, final Types types) {
    // Qualifiers disqualify implicit provisioning.
    if (qualifier.isPresent()) {
      return false;
    }

    return type.accept(
        new SimpleTypeVisitor6<Boolean, Void>(false) {
          @Override
          public Boolean visitDeclared(DeclaredType type, Void ignored) {
            // Non-classes or abstract classes aren't allowed.
            TypeElement element = MoreElements.asType(type.asElement());
            if (!element.getKind().equals(ElementKind.CLASS)
                || element.getModifiers().contains(Modifier.ABSTRACT)) {
              return false;
            }

            // If the key has type arguments, validate that each type argument is declared.
            // Otherwise the type argument may be a wildcard (or other type), and we can't
            // resolve that to actual types.
            for (TypeMirror arg : type.getTypeArguments()) {
              if (arg.getKind() != TypeKind.DECLARED) {
                return false;
              }
            }

            // Also validate that the key is not the erasure of a generic type.
            // If it is, that means the user referred to Foo<T> as just 'Foo',
            // which we don't allow.  (This is a judgement call -- we *could*
            // allow it and instantiate the type bounds... but we don't.)
            return MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
                || !types.isSameType(types.erasure(element.asType()), type);
          }
        },
        null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned string is equal to another key's if and only if this key is {@link
   * #equals(Object)} to it.
   */
  @Override
  public String toString() {
    return Joiner.on(' ')
        .skipNulls()
        .join(qualifier().orElse(null), type(), multibindingContributionIdentifier().orElse(null));
  }

  /**
   * Indexes {@code haveKeys} by {@link HasKey#key()}.
   */
  static <T extends HasKey> ImmutableSetMultimap<Key, T> indexByKey(Iterable<T> haveKeys) {
    return ImmutableSetMultimap.copyOf(Multimaps.index(haveKeys, HasKey::key));
  }

  static final class Factory {
    private final Types types;
    private final Elements elements;
    
    Factory(Types types, Elements elements) {
      this.types = checkNotNull(types);
      this.elements = checkNotNull(elements);
    }

    private TypeElement getClassElement(Class<?> cls) {
      return elements.getTypeElement(cls.getCanonicalName());
    }

    private TypeMirror boxPrimitives(TypeMirror type) {
      return type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
    }

    private DeclaredType setOf(TypeMirror elementType) {
      return types.getDeclaredType(getClassElement(Set.class), boxPrimitives(elementType));
    }

    private DeclaredType mapOf(TypeMirror keyType, TypeMirror valueType) {
      return types.getDeclaredType(
          getClassElement(Map.class), boxPrimitives(keyType), boxPrimitives(valueType));
    }

    /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
    private TypeMirror mapOfFrameworkType(
        TypeMirror keyType, TypeElement frameworkType, TypeMirror valueType) {
      return mapOf(keyType, types.getDeclaredType(frameworkType, boxPrimitives(valueType)));
    }

    private DeclaredType typedReleasableReferenceManagerOf(DeclaredType metadataType) {
      return types.getDeclaredType(
          getClassElement(TypedReleasableReferenceManager.class), metadataType);
    }

    Key forComponentMethod(ExecutableElement componentMethod) {
      checkArgument(componentMethod.getKind().equals(METHOD));
      return forMethod(componentMethod, componentMethod.getReturnType());
    }

    Key forProductionComponentMethod(ExecutableElement componentMethod) {
      checkArgument(componentMethod.getKind().equals(METHOD));
      TypeMirror returnType = componentMethod.getReturnType();
      TypeMirror keyType =
          isTypeOf(ListenableFuture.class, returnType)
              ? getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments())
              : returnType;
      return forMethod(componentMethod, keyType);
    }

    Key forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, DeclaredType declaredContainer) {
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      ExecutableType resolvedMethod =
          asExecutable(types.asMemberOf(declaredContainer, subcomponentBuilderMethod));
      return builder(resolvedMethod.getReturnType()).build();
    }

    Key forSubcomponentBuilder(TypeMirror builderType) {
      return builder(builderType).build();
    }

    Key forProvidesMethod(ExecutableElement method, TypeElement contributingModule) {
      return forBindingMethod(
          method, contributingModule, Optional.of(getClassElement(Provider.class)));
    }

    Key forProducesMethod(ExecutableElement method, TypeElement contributingModule) {
      return forBindingMethod(
          method, contributingModule, Optional.of(getClassElement(Producer.class)));
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
      ContributionType contributionType = ContributionType.fromBindingMethod(method);
      TypeMirror returnType = methodType.getReturnType();
      if (frameworkType.isPresent()
          && frameworkType.get().equals(getClassElement(Producer.class))
          && isType(returnType)) {
        if (isTypeOf(ListenableFuture.class, returnType)) {
          returnType = getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
        } else if (contributionType.equals(ContributionType.SET_VALUES)
            && SetType.isSet(returnType)) {
          SetType setType = SetType.from(returnType);
          if (setType.elementsAreTypeOf(ListenableFuture.class)) {
            returnType =
                types.getDeclaredType(
                    getClassElement(Set.class),
                    setType.unwrappedElementType(ListenableFuture.class));
          }
        }
      }
      TypeMirror keyType =
          bindingMethodKeyType(returnType, method, contributionType, frameworkType);
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
     * <p>The key's type is either {@code Set<T>} or {@code Map<K, F<V>>}, where {@code F} is either
     * {@link Provider} or {@link Producer}, depending on {@code bindingType}.
     */
    Key forMultibindsMethod(
        BindingType bindingType, ExecutableType executableType, ExecutableElement method) {
      checkArgument(method.getKind().equals(METHOD), "%s must be a method", method);
      TypeElement factoryType =
          elements.getTypeElement(bindingType.frameworkClass().getCanonicalName());
      TypeMirror returnType = executableType.getReturnType();
      TypeMirror keyType =
          MapType.isMap(returnType)
              ? mapOfFrameworkType(
                  MapType.from(returnType).keyType(),
                  factoryType,
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
          if (frameworkType.isPresent()) {
            return mapOfFrameworkType(mapKeyType(method), frameworkType.get(), returnType);
          } else {
            return mapOf(mapKeyType(method), returnType);
          }
        case SET_VALUES:
          // TODO(gak): do we want to allow people to use "covariant return" here?
          checkArgument(SetType.isSet(returnType));
          return returnType;
        default:
          throw new AssertionError();
      }
    }

    /**
     * Returns the key for a binding associated with a {@link DelegateDeclaration}.
     *
     * If {@code delegateDeclaration} is {@code @IntoMap}, transforms the {@code Map<K, V>} key
     * from {@link DelegateDeclaration#key()} to {@code Map<K, FrameworkType<V>>}. If {@code
     * delegateDeclaration} is not a map contribution, its key is returned.
     */
    Key forDelegateBinding(
        DelegateDeclaration delegateDeclaration, Class<?> frameworkType) {
      return delegateDeclaration.contributionType().equals(ContributionType.MAP)
          ? wrapMapValue(delegateDeclaration.key(), frameworkType)
          : delegateDeclaration.key();
    }

    private TypeMirror mapKeyType(ExecutableElement method) {
      AnnotationMirror mapKeyAnnotation = getMapKey(method).get();
      return MapKeys.unwrapValue(mapKeyAnnotation).isPresent()
          ? getUnwrappedMapKeyType(mapKeyAnnotation.getAnnotationType(), types)
          : mapKeyAnnotation.getAnnotationType();
    }

    private Key forMethod(ExecutableElement method, TypeMirror keyType) {
      return forQualifiedType(getQualifier(method), keyType);
    }

    Key forInjectConstructorWithResolvedType(TypeMirror type) {
      return builder(type).build();
    }

    Key forComponent(TypeMirror type) {
      return builder(type).build();
    }

    Key forMembersInjectedType(TypeMirror type) {
      return builder(type).build();
    }

    Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
      return builder(boxPrimitives(type)).qualifier(qualifier).build();
    }

    Key forProductionExecutor() {
      return builder(getClassElement(Executor.class).asType())
          .qualifier(getClassElement(Production.class))
          .build();
    }

    Key forProductionImplementationExecutor() {
      return builder(getClassElement(Executor.class).asType())
          .qualifier(getClassElement(ProductionImplementation.class))
          .build();
    }

    Key forProductionComponentMonitor() {
      return builder(getClassElement(ProductionComponentMonitor.class).asType()).build();
    }

    /**
     * If {@code requestKey} is for a {@code Map<K, V>} or {@code Map<K, Produced<V>>}, returns keys
     * for {@code Map<K, Provider<V>>} and {@code Map<K, Producer<V>>} (if Dagger-Producers is on
     * the classpath).
     */
    ImmutableSet<Key> implicitFrameworkMapKeys(Key requestKey) {
      return Stream.of(
              implicitMapProviderKeyFrom(requestKey), implicitMapProducerKeyFrom(requestKey))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(toImmutableSet());
    }

    /**
     * Optionally extract a {@link Key} for the underlying provision binding(s) if such a
     * valid key can be inferred from the given key.  Specifically, if the key represents a
     * {@link Map}{@code <K, V>}, a key of {@code Map<K, Provider<V>>} will be returned.
     */
    Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
      return wrapMapKey(possibleMapKey, Provider.class);
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
     * Keys for map contributions from {@link dagger.Provides} and {@link dagger.producers.Produces}
     * are in the form {@code Map<K, Framework<V>>}, but keys for {@link Binds} methods are just
     * {@code Map<K, V>} since the framework type is not known until graph resolution. This
     * translates from the {@code @Provides}/{@code @Produces} format into the {@code @Binds}
     * format. If {@link Key#type() possibleMapKey.type()} is not a {@code Map<K, Framework<V>>},
     * returns {@code possibleMapKey}.
     */
    Key convertToDelegateKey(Key possibleMapKey) {
      if (!MapType.isMap(possibleMapKey)) {
        return possibleMapKey;
      }
      MapType mapType = MapType.from(possibleMapKey);
      TypeMirror wrappedValueType;
      if (mapType.valuesAreTypeOf(Provider.class)) {
        wrappedValueType = mapType.unwrappedValueType(Provider.class);
      } else if (mapType.valuesAreTypeOf(Producer.class)) {
        wrappedValueType = mapType.unwrappedValueType(Producer.class);
      } else {
        return possibleMapKey;
      }
      return possibleMapKey.toBuilder().type(mapOf(mapType.keyType(), wrappedValueType)).build();
    }

    /**
     * Converts a {@link Key} of type {@code Map<K, V>} to {@code Map<K, Provider<V>>}.
     */
    private Key wrapMapValue(Key key, Class<?> newWrappingClass) {
      checkArgument(
          FrameworkTypes.isFrameworkType(
              elements.getTypeElement(newWrappingClass.getName()).asType()));
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
        if (mapType.valuesAreTypeOf(currentWrappingClass)) {
          TypeElement wrappingElement = getClassElement(newWrappingClass);
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
        if (!mapType.valuesAreTypeOf(wrappingClass)) {
          TypeElement wrappingElement = getClassElement(wrappingClass);
          if (wrappingElement == null) {
            // This target might not be compiled with Producers, so wrappingClass might not have an
            // associated element.
            return Optional.empty();
          }
          DeclaredType wrappedValueType =
              types.getDeclaredType(wrappingElement, mapType.valueType());
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
        if (setType.elementsAreTypeOf(wrappingClass)) {
          return Optional.of(
              key.toBuilder().type(setOf(setType.unwrappedElementType(wrappingClass))).build());
        }
      }
      return Optional.empty();
    }

    /**
     * If {@code key}'s type is {@code Optional<T>} for some {@code T}, returns a key with the same
     * qualifier whose type is {@linkplain DependencyRequest#extractKindAndType(TypeMirror)
     * extracted} from {@code T}.
     */
    Optional<Key> unwrapOptional(Key key) {
      if (!OptionalType.isOptional(key)) {
        return Optional.empty();
      }
      TypeMirror underlyingType =
          DependencyRequest.extractKindAndType(OptionalType.from(key).valueType()).type();
      return Optional.of(key.toBuilder().type(underlyingType).build());
    }

    /** Returns a key for a {@code @ForReleasableReferences(scope) ReleasableReferenceManager}. */
    Key forReleasableReferenceManager(Scope scope) {
      return forQualifiedType(
          Optional.of(forReleasableReferencesAnnotationMirror(scope)),
          getClassElement(ReleasableReferenceManager.class).asType());
    }

    /**
     * Returns a key for a {@code @ForReleasableReferences(scope)
     * TypedReleasableReferenceManager<metadataType>}
     */
    Key forTypedReleasableReferenceManager(Scope scope, DeclaredType metadataType) {
      return builder(typedReleasableReferenceManagerOf(metadataType))
          .qualifier(forReleasableReferencesAnnotationMirror(scope))
          .build();
    }

    /** Returns a key for a {@code Set<ReleasableReferenceManager>}. */
    Key forSetOfReleasableReferenceManagers() {
      return builder(setOf(getClassElement(ReleasableReferenceManager.class).asType())).build();
    }

    /** Returns a key for a {@code Set<TypedReleasableReferenceManager<metadataType>}. */
    Key forSetOfTypedReleasableReferenceManagers(DeclaredType metadataType) {
      return forQualifiedType(
          Optional.empty(), setOf(typedReleasableReferenceManagerOf(metadataType)));
    }

    private AnnotationMirror forReleasableReferencesAnnotationMirror(Scope scope) {
      return SimpleAnnotationMirror.of(
          getClassElement(ForReleasableReferences.class),
          ImmutableMap.of(
              "value", new SimpleTypeAnnotationValue(scope.scopeAnnotationElement().asType())));
    }
  }
}
