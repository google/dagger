/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An abstract class for a value object representing the mechanism by which a {@link Key} can be
 * contributed to a dependency graph.
 *
 * @author Jesse Beder
 * @since 2.0
 */
abstract class ContributionBinding extends Binding {
  static enum BindingType {
    /** Represents map bindings. */
    MAP,
    /** Represents set bindings. */
    SET,
    /** Represents a valid non-collection binding. */
    UNIQUE;

    boolean isMultibinding() {
      return !this.equals(UNIQUE);
    }
  }

  abstract BindingType bindingType();

  /** Returns the type that specifies this' nullability, absent if not nullable. */
  abstract Optional<DeclaredType> nullableType();

  /**
   * If this is a provision request from an {@code @Provides} or {@code @Produces} method, this will
   * be the element that contributed it. In the case of subclassed modules, this may differ than the
   * binding's enclosed element, as this will return the subclass whereas the enclosed element will
   * be the superclass.
   */
  abstract Optional<TypeElement> contributedBy();

  /**
   * Returns whether this binding is synthetic, i.e., not explicitly tied to code, but generated
   * implicitly by the framework.
   */
  // TODO(user): Remove the SYNTHETIC enums from ProvisionBinding and ProductionBinding and make
  // this field the source of truth for synthetic bindings.
  abstract boolean isSyntheticBinding();

  /**
   * Returns the framework class associated with this binding, e.g., {@link Provider} for a
   * ProvisionBinding.
   */
  abstract Class<?> frameworkClass();

  /**
   * Returns the set of {@link BindingType} enum values implied by a given
   * {@link ContributionBinding} collection.
   */
  static <B extends ContributionBinding> ImmutableListMultimap<BindingType, B> bindingTypesFor(
      Iterable<? extends B> bindings) {
    ImmutableListMultimap.Builder<BindingType, B> builder =
        ImmutableListMultimap.builder();
    builder.orderKeysBy(Ordering.<BindingType>natural());
    for (B binding : bindings) {
      builder.put(binding.bindingType(), binding);
    }
    return builder.build();
  }

  /**
   * Returns a single {@code BindingsType} represented by a given collection of
   * {@code ContributionBindings} or throws an IllegalArgumentException if the given bindings
   * are not all of one type.
   */
  static BindingType bindingTypeFor(Iterable<? extends ContributionBinding> bindings) {
    checkNotNull(bindings);
    checkArgument(!Iterables.isEmpty(bindings), "no bindings");
    Set<BindingType> types = EnumSet.noneOf(BindingType.class);
    for (ContributionBinding binding : bindings) {
      types.add(binding.bindingType());
    }
    if (types.size() > 1) {
      throw new IllegalArgumentException(
          String.format(ErrorMessages.MULTIPLE_BINDING_TYPES_FORMAT, types));
    }
    return Iterables.getOnlyElement(types);
  }
}
