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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.isAbstract;
import static dagger.internal.codegen.xprocessing.XElements.isStatic;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Optional;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 */
@CheckReturnValue
public abstract class ContributionBinding extends Binding implements HasContributionType {

  /** Returns the nullability of this binding. */
  public abstract Nullability nullability();

  private static final ImmutableSet<BindingKind> KINDS_TO_CHECK_FOR_NULL =
      ImmutableSet.of(BindingKind.PROVISION, BindingKind.COMPONENT_PROVISION);

  public boolean shouldCheckForNull(CompilerOptions compilerOptions) {
    return KINDS_TO_CHECK_FOR_NULL.contains(kind())
        && contributedPrimitiveType().isEmpty()
        && !isNullable()
        && compilerOptions.doCheckForNulls();
  }

  /** Returns the map key if this is a {@code Map} multibinding contribution. */
  public Optional<XAnnotation> mapKey() {
    return bindingElement().flatMap(MapKeys::getMapKey);
  }

  /** If {@link #bindingElement()} is a method that returns a primitive type, returns that type. */
  public final Optional<XType> contributedPrimitiveType() {
    return bindingElement()
        .filter(XElementKt::isMethod)
        .map(bindingElement -> asMethod(bindingElement).getReturnType())
        .filter(XTypes::isPrimitive);
  }

  @Override
  public boolean requiresModuleInstance() {
    return contributingModule().isPresent()
        && bindingElement().isPresent()
        && !isAbstract(bindingElement().get())
        && !isStatic(bindingElement().get())
        && !isContributingModuleKotlinObject();
  }

  @Override
  public final boolean isNullable() {
    return nullability().isNullable();
  }

  /**
   * Returns {@code true} if the contributing module is a Kotlin object. Note that a companion
   * object is also considered a Kotlin object.
   */
  private boolean isContributingModuleKotlinObject() {
    return contributingModule().isPresent()
        && (contributingModule().get().isKotlinObject()
            || contributingModule().get().isCompanionObject());
  }

  /**
   * The {@link XType type} for the {@code Factory<T>} or {@code Producer<T>} which is created for
   * this binding. Uses the binding's key, V in the case of {@code Map<K, FrameworkClass<V>>>}, and
   * E {@code Set<E>} for {@link dagger.multibindings.IntoSet @IntoSet} methods.
   */
  public final XType contributedType() {
    switch (contributionType()) {
      case MAP:
        return MapType.from(key()).unwrappedFrameworkValueType();
      case SET:
        return SetType.from(key()).elementType();
      case SET_VALUES:
      case UNIQUE:
        return key().type().xprocessing();
    }
    throw new AssertionError();
  }

  public abstract Builder<?, ?> toBuilder();

  /** Returns a new {@link ContributionBinding} with the given {@link BindingType}. */
  final ContributionBinding withBindingType(BindingType bindingType) {
    checkState(optionalBindingType().isEmpty());
    switch (kind()) {
      case DELEGATE:
        return ((DelegateBinding) this).toBuilder()
            .optionalBindingType(Optional.of(bindingType))
            .build();
      case OPTIONAL:
        return ((OptionalBinding) this).toBuilder()
            .optionalBindingType(Optional.of(bindingType))
            .build();
      case MULTIBOUND_MAP:
        return ((MultiboundMapBinding) this).toBuilder()
            .optionalBindingType(Optional.of(bindingType))
            .build();
      case MULTIBOUND_SET:
        return ((MultiboundSetBinding) this).toBuilder()
            .optionalBindingType(Optional.of(bindingType))
            .build();
      default:
        throw new AssertionError("Unexpected binding kind: " + kind());
    }
  }

  /**
   * Base builder for {@link com.google.auto.value.AutoValue @AutoValue} subclasses of {@link
   * ContributionBinding}.
   */
  abstract static class Builder<C extends ContributionBinding, B extends Builder<C, B>> {
    @CanIgnoreReturnValue
    abstract B unresolved(Optional<? extends Binding> unresolved);

    @CanIgnoreReturnValue
    abstract B bindingElement(XElement bindingElement);

    @CanIgnoreReturnValue
    abstract B bindingElement(Optional<XElement> bindingElement);

    @CanIgnoreReturnValue
    final B clearBindingElement() {
      return bindingElement(Optional.empty());
    };

    @CanIgnoreReturnValue
    abstract B contributingModule(XTypeElement contributingModule);

    @CanIgnoreReturnValue
    abstract B key(Key key);

    @CanIgnoreReturnValue
    abstract B scope(Optional<Scope> scope);

    abstract C build();
  }
}
