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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.model.Key;
import dagger.model.Scope;
import java.util.Optional;

/**
 * The collection of bindings that have been resolved for a key. For valid graphs, contains exactly
 * one binding.
 *
 * <p>Separate {@link ResolvedBindings} instances should be used if a {@link
 * MembersInjectionBinding} and a {@link ProvisionBinding} for the same key exist in the same
 * component. (this will only happen if a type has an {@code @Inject} constructor and members, the
 * component has a {@link ComponentDescriptor.ComponentMethodKind#MEMBERS_INJECTION members
 * injection method}, and the type is also requested normally.
 */
@AutoValue
abstract class ResolvedBindings implements HasContributionType {
  /**
   * The binding key for which the {@link #bindings()} have been resolved.
   */
  abstract Key key();

  /** The component in which the bindings in {@link #ownedBindings()}, were resolved. */
  abstract ComponentDescriptor owningComponent();

  /**
   * The contribution bindings for {@link #key()} that were resolved in {@link #owningComponent()}
   * or its ancestor components, indexed by the component in which the binding was resolved.
   */
  abstract ImmutableSetMultimap<ComponentDescriptor, ContributionBinding> allContributionBindings();

  /**
   * The members-injection bindings for {@link #key()} that were resolved in {@link
   * #owningComponent()} or its ancestor components, indexed by the component in which the binding
   * was resolved.
   */
  abstract ImmutableMap<ComponentDescriptor, MembersInjectionBinding> allMembersInjectionBindings();

  /** The multibinding declarations for {@link #key()}. */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  /** The subcomponent declarations for {@link #key()}. */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /**
   * The optional binding declarations for {@link #key()}.
   */
  abstract ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations();

  /**
   * All bindings for {@link #key()}, indexed by the component in which the binding was resolved.
   */
  final ImmutableSetMultimap<ComponentDescriptor, ? extends Binding> allBindings() {
    return !allMembersInjectionBindings().isEmpty()
        ? allMembersInjectionBindings().asMultimap()
        : allContributionBindings();
  }

  /** All bindings for {@link #key()}, regardless of in which component they were resolved. */
  final ImmutableSet<? extends Binding> bindings() {
    return ImmutableSet.copyOf(allBindings().values());
  }

  /**
   * Returns the single binding.
   *
   * @throws IllegalStateException if there is not exactly one element in {@link #bindings()}, which
   *     will never happen for contributions in valid graphs
   */
  final Binding binding() {
    return getOnlyElement(bindings());
  }

  /**
   * {@code true} if there are no {@link #bindings()}, {@link #multibindingDeclarations()}, {@link
   * #optionalBindingDeclarations()}, or {@link #subcomponentDeclarations()}.
   */
  final boolean isEmpty() {
    return bindings().isEmpty()
        && multibindingDeclarations().isEmpty()
        && optionalBindingDeclarations().isEmpty()
        && subcomponentDeclarations().isEmpty();
  }

  /** All bindings for {@link #key()} that were resolved in {@link #owningComponent()}. */
  ImmutableSet<? extends Binding> ownedBindings() {
    return allBindings().get(owningComponent());
  }

  /**
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  final ImmutableSet<ContributionBinding> contributionBindings() {
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /** The component that owns {@code binding}. */
  final ComponentDescriptor owningComponent(ContributionBinding binding) {
    checkArgument(
        contributionBindings().contains(binding),
        "binding is not resolved for %s: %s",
        key(),
        binding);
    return getOnlyElement(allContributionBindings().inverse().get(binding));
  }

  /**
   * The members-injection binding, regardless of owning component. Absent if these are contribution
   * bindings, or if there is no members-injection binding because the type fails validation.
   */
  final Optional<MembersInjectionBinding> membersInjectionBinding() {
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        FluentIterable.from(allMembersInjectionBindings().values()).toSet();
    return membersInjectionBindings.isEmpty()
        ? Optional.empty()
        : Optional.of(Iterables.getOnlyElement(membersInjectionBindings));
  }

  /** Creates a {@link ResolvedBindings} for contribution bindings. */
  static ResolvedBindings forContributionBindings(
      Key key,
      ComponentDescriptor owningComponent,
      Multimap<ComponentDescriptor, ? extends ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings,
      Iterable<SubcomponentDeclaration> subcomponentDeclarations,
      Iterable<OptionalBindingDeclaration> optionalBindingDeclarations) {
    return new AutoValue_ResolvedBindings(
        key,
        owningComponent,
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
        owningComponent,
        ImmutableSetMultimap.of(),
        ImmutableMap.of(owningComponent, ownedMembersInjectionBinding),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(Key key, ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        key,
        owningComponent,
        ImmutableSetMultimap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  /**
   * Returns a {@code ResolvedBindings} with the same {@link #key()} and {@link #bindings()}
   * as this one, but no {@link #ownedBindings()}.
   */
  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        key(),
        owningComponent,
        allContributionBindings(),
        allMembersInjectionBindings(),
        multibindingDeclarations(),
        subcomponentDeclarations(),
        optionalBindingDeclarations());
  }

  /**
   * {@code true} if this is a multibinding contribution.
   */
  boolean isMultibindingContribution() {
    return contributionBindings().size() == 1
        && contributionBinding().contributionType().isMultibinding();
  }

  /**
   * Returns the single contribution binding.
   *
   * @throws IllegalStateException if there is not exactly one element in
   *     {@link #contributionBindings()}, which will never happen for contributions in valid graphs
   */
  ContributionBinding contributionBinding() {
    return getOnlyElement(contributionBindings());
  }

  /**
   * The binding type for these bindings. If there are {@link #multibindingDeclarations()} or {@link
   * #subcomponentDeclarations()} but no {@link #bindings()}, returns {@link BindingType#PROVISION}.
   *
   * @throws IllegalStateException if {@link #isEmpty()} or the binding types conflict
   */
  final BindingType bindingType() {
    checkState(!isEmpty(), "empty bindings for %s", key());
    if (bindings().isEmpty()
        && (!multibindingDeclarations().isEmpty() || !subcomponentDeclarations().isEmpty())) {
      // Only multibinding declarations, so assume provision.
      return BindingType.PROVISION;
    }
    ImmutableSet<BindingType> bindingTypes = bindingTypes();
    checkState(bindingTypes.size() == 1, "conflicting binding types: %s", bindings());
    return getOnlyElement(bindingTypes);
  }

  /** The binding types for {@link #bindings()}. */
  ImmutableSet<BindingType> bindingTypes() {
    return bindings().stream().map(Binding::bindingType).collect(toImmutableSet());
  }

  /**
   * The contribution type for these bindings.
   *
   * @throws IllegalStateException if there is not exactly one element in {@link
   *     #contributionBindings()}, which will never happen for contributions in valid graphs
   */
  @Override
  public ContributionType contributionType() {
    return contributionBinding().contributionType();
  }

  /**
   * The framework class associated with these bindings.
   */
  Class<?> frameworkClass() {
    return bindingType().frameworkClass();
  }

  /**
   * The scope associated with the single binding.
   *
   * @throws IllegalStateException if {@link #bindings()} does not have exactly one element
   */
  Optional<Scope> scope() {
    return getOnlyElement(bindings()).scope();
  }
}
