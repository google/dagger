/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static com.google.common.collect.Iterables.getOnlyElement;

import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import dagger.spi.model.Key;

/**
 * The collection of bindings that have been resolved for a key. For valid graphs, contains exactly
 * one binding.
 *
 * <p>Separate {@link ResolvedBindings} instances should be used if a {@link
 * MembersInjectionBinding} and a {@link ProvisionBinding} for the same key exist in the same
 * component. (This will only happen if a type has an {@code @Inject} constructor and members, the
 * component has a members injection method, and the type is also requested normally.)
 */
@AutoValue
abstract class ResolvedBindings {
  /** The binding key for which the {@link #bindings()} have been resolved. */
  abstract Key key();

  /**
   * The {@link ContributionBinding}s for {@link #key()} indexed by the component that owns the
   * binding. Each key in the multimap is a part of the same component ancestry.
   */
  abstract ImmutableSetMultimap<XTypeElement, ContributionBinding> allContributionBindings();

  /**
   * The {@link MembersInjectionBinding}s for {@link #key()} indexed by the component that owns the
   * binding. Each key in the map is a part of the same component ancestry.
   */
  abstract ImmutableMap<XTypeElement, MembersInjectionBinding> allMembersInjectionBindings();

  /** The multibinding declarations for {@link #key()}. */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  /** The subcomponent declarations for {@link #key()}. */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /** The optional binding declarations for {@link #key()}. */
  abstract ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations();

  // Computing the hash code is an expensive operation.
  @Memoized
  @Override
  public abstract int hashCode();

  // Suppresses ErrorProne warning that hashCode was overridden w/o equals
  @Override
  public abstract boolean equals(Object other);

  /** All bindings for {@link #key()}, indexed by the component that owns the binding. */
  final ImmutableSetMultimap<XTypeElement, ? extends Binding> allBindings() {
    return !allMembersInjectionBindings().isEmpty()
        ? allMembersInjectionBindings().asMultimap()
        : allContributionBindings();
  }

  /** All bindings for {@link #key()}, regardless of which component owns them. */
  final ImmutableCollection<? extends Binding> bindings() {
    return allBindings().values();
  }

  /**
   * {@code true} if there are no {@link #bindings()}, {@link #multibindingDeclarations()}, {@link
   * #optionalBindingDeclarations()}, or {@link #subcomponentDeclarations()}.
   */
  final boolean isEmpty() {
    return allMembersInjectionBindings().isEmpty()
        && allContributionBindings().isEmpty()
        && multibindingDeclarations().isEmpty()
        && optionalBindingDeclarations().isEmpty()
        && subcomponentDeclarations().isEmpty();
  }

  /** All bindings for {@link #key()} that are owned by a component. */
  ImmutableSet<? extends Binding> bindingsOwnedBy(ComponentDescriptor component) {
    return allBindings().get(component.typeElement());
  }

  /**
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  @Memoized
  ImmutableSet<ContributionBinding> contributionBindings() {
    // TODO(ronshapiro): consider optimizing ImmutableSet.copyOf(Collection) for small immutable
    // collections so that it doesn't need to call toArray(). Even though this method is memoized,
    // toArray() can take ~150ms for large components, and there are surely other places in the
    // processor that can benefit from this.
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /** The component that owns {@code binding}. */
  final XTypeElement owningComponent(ContributionBinding binding) {
    checkArgument(
        contributionBindings().contains(binding),
        "binding is not resolved for %s: %s",
        key(),
        binding);
    return getOnlyElement(allContributionBindings().inverse().get(binding));
  }

  /** Creates a {@link ResolvedBindings} for contribution bindings. */
  static ResolvedBindings forContributionBindings(
      Key key,
      Multimap<XTypeElement, ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings,
      Iterable<SubcomponentDeclaration> subcomponentDeclarations,
      Iterable<OptionalBindingDeclaration> optionalBindingDeclarations) {
    return new AutoValue_ResolvedBindings(
        key,
        ImmutableSetMultimap.copyOf(contributionBindings),
        ImmutableMap.of(),
        ImmutableSet.copyOf(multibindings),
        ImmutableSet.copyOf(subcomponentDeclarations),
        ImmutableSet.copyOf(optionalBindingDeclarations));
  }

  /**
   * Creates a {@link ResolvedBindings} for members injection bindings.
   */
  static ResolvedBindings forMembersInjectionBinding(
      Key key,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    return new AutoValue_ResolvedBindings(
        key,
        ImmutableSetMultimap.of(),
        ImmutableMap.of(owningComponent.typeElement(), ownedMembersInjectionBinding),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(Key key) {
    return new AutoValue_ResolvedBindings(
        key,
        ImmutableSetMultimap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }
}
