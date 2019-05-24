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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.CLASS_CONSTRUCTOR;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.DELEGATE;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.MapKeys.unwrapValue;
import static dagger.internal.codegen.MoreAnnotationMirrors.unwrapOptionalEquivalence;
import static java.util.Arrays.asList;

import com.google.auto.common.MoreElements;
import com.google.common.base.Equivalence;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 */
abstract class ContributionBinding extends Binding implements HasContributionType {

  /** Returns the type that specifies this' nullability, absent if not nullable. */
  abstract Optional<DeclaredType> nullableType();

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation();

  final Optional<AnnotationMirror> mapKeyAnnotation() {
    return unwrapOptionalEquivalence(wrappedMapKeyAnnotation());
  }

  /**
   * If this is a map contribution, returns the key of its map entry.
   *
   * @throws IllegalStateException if {@link #mapKeyAnnotation()} returns an empty value.
   */
  final Object mapKey() {
    checkState(mapKeyAnnotation().isPresent());
    AnnotationMirror mapKeyAnnotation = mapKeyAnnotation().get();
    return unwrapValue(mapKeyAnnotation).map(AnnotationValue::getValue).orElse(mapKeyAnnotation);
  }

  /** If {@link #bindingElement()} is a method that returns a primitive type, returns that type. */
  final Optional<TypeMirror> contributedPrimitiveType() {
    return bindingElement()
        .filter(bindingElement -> bindingElement instanceof ExecutableElement)
        .map(bindingElement -> MoreElements.asExecutable(bindingElement).getReturnType())
        .filter(type -> type.getKind().isPrimitive());
  }

  @Override
  public final boolean isNullable() {
    return nullableType().isPresent();
  }

  /**
   * The strategy for getting an instance of a factory for a {@link ContributionBinding}.
   */
  enum FactoryCreationStrategy {
    /** The factory class is a single instance. */
    SINGLETON_INSTANCE,
    /** The factory must be created by calling the constructor. */
    CLASS_CONSTRUCTOR,
    /** The factory is simply delegated to another. */
    DELEGATE,
  }

  /**
   * Returns the {@link FactoryCreationStrategy} appropriate for a binding.
   *
   * <p>Delegate bindings use the {@link FactoryCreationStrategy#DELEGATE} strategy.
   *
   * <p>Bindings without dependencies that don't require a module instance use the {@link
   * FactoryCreationStrategy#SINGLETON_INSTANCE} strategy.
   *
   * <p>All other bindings use the {@link FactoryCreationStrategy#CLASS_CONSTRUCTOR} strategy.
   */
  final FactoryCreationStrategy factoryCreationStrategy() {
    switch (kind()) {
      case DELEGATE:
        return DELEGATE;
      case PROVISION:
        return dependencies().isEmpty() && !requiresModuleInstance()
            ? SINGLETON_INSTANCE
            : CLASS_CONSTRUCTOR;
      case INJECTION:
      case MULTIBOUND_SET:
      case MULTIBOUND_MAP:
        return dependencies().isEmpty() ? SINGLETON_INSTANCE : CLASS_CONSTRUCTOR;
      default:
        return CLASS_CONSTRUCTOR;
    }
  }

  /**
   * The {@link TypeMirror type} for the {@code Factory<T>} or {@code Producer<T>} which is created
   * for this binding. Uses the binding's key, V in the case of {@code Map<K, FrameworkClass<V>>>},
   * and E {@code Set<E>} for {@link dagger.multibindings.IntoSet @IntoSet} methods.
   */
  final TypeMirror contributedType() {
    switch (contributionType()) {
      case MAP:
        return MapType.from(key()).unwrappedFrameworkValueType();
      case SET:
        return SetType.from(key()).elementType();
      case SET_VALUES:
      case UNIQUE:
        return key().type();
    }
    throw new AssertionError();
  }

  final boolean isSyntheticMultibinding() {
    switch (kind()) {
      case MULTIBOUND_SET:
      case MULTIBOUND_MAP:
        return true;
      default:
        return false;
    }
  }

  /** Whether the bound type has a generated implementation. */
  final boolean requiresGeneratedInstance() {
    switch (kind()) {
      case COMPONENT:
      case SUBCOMPONENT_CREATOR:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns {@link BindingKind#MULTIBOUND_SET} or {@link
   * BindingKind#MULTIBOUND_MAP} if the key is a set or map.
   *
   * @throws IllegalArgumentException if {@code key} is neither a set nor a map
   */
  static BindingKind bindingKindForMultibindingKey(Key key) {
    if (SetType.isSet(key)) {
      return BindingKind.MULTIBOUND_SET;
    } else if (MapType.isMap(key)) {
      return BindingKind.MULTIBOUND_MAP;
    } else {
      throw new IllegalArgumentException(String.format("key is not for a set or map: %s", key));
    }
  }

  /**
   * Base builder for {@link com.google.auto.value.AutoValue @AutoValue} subclasses of {@link
   * ContributionBinding}.
   */
  @CanIgnoreReturnValue
  abstract static class Builder<C extends ContributionBinding, B extends Builder<C, B>> {
    abstract B dependencies(Iterable<DependencyRequest> dependencies);

    B dependencies(DependencyRequest... dependencies) {
      return dependencies(asList(dependencies));
    }

    abstract B unresolved(C unresolved);

    abstract B contributionType(ContributionType contributionType);

    abstract B bindingElement(Element bindingElement);

    abstract B contributingModule(TypeElement contributingModule);

    abstract B key(Key key);

    abstract B nullableType(Optional<DeclaredType> nullableType);

    abstract B wrappedMapKeyAnnotation(
        Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKeyAnnotation);

    abstract B kind(BindingKind kind);

    @CheckReturnValue
    abstract C build();
  }
}
