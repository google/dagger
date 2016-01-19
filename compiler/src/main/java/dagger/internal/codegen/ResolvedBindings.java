/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import dagger.MembersInjector;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.Key.HasKey;
import dagger.internal.codegen.SourceElement.HasSourceElement;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ContributionType.indexByContributionType;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_CONTRIBUTION_TYPES_FORMAT;

/**
 * The collection of bindings that have been resolved for a binding key.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class ResolvedBindings implements HasBindingType, HasContributionType, HasKey {
  /**
   * The binding key for which the {@link #bindings()} have been resolved.
   */
  abstract BindingKey bindingKey();

  /**
   * The component in which the bindings in {@link #ownedBindings()},
   * {@link #ownedContributionBindings()}, and {@link #ownedMembersInjectionBinding()} were
   * resolved.
   */
  abstract ComponentDescriptor owningComponent();

  /**
   * The contribution bindings for {@link #bindingKey()} that were resolved in
   * {@link #owningComponent()} or its ancestor components, keyed by the component in which the
   * binding was resolved. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSetMultimap<ComponentDescriptor, ContributionBinding> allContributionBindings();

  /**
   * The members-injection bindings for {@link #bindingKey()} that were resolved in
   * {@link #owningComponent()} or its ancestor components, keyed by the component in which the
   * binding was resolved. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#MEMBERS_INJECTION}, this is empty.
   */
  abstract ImmutableMap<ComponentDescriptor, MembersInjectionBinding> allMembersInjectionBindings();

  @Override
  public Key key() {
    return bindingKey().key();
  }
  
  /**
   * The multibinding declarations for {@link #bindingKey()}. If {@link #bindingKey()}'s kind is not
   * {@link BindingKey.Kind#CONTRIBUTION}, this is empty.
   */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();
  
  /**
   * All bindings for {@link #bindingKey()}, regardless of in which component they were resolved.
   */
  ImmutableSet<? extends Binding> bindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return contributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(membersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  /**
   * {@code true} if there are no {@link #bindings()} or {@link #multibindingDeclarations()}.
   */
  boolean isEmpty() {
    return bindings().isEmpty() && multibindingDeclarations().isEmpty();
  }

  /**
   * All bindings for {@link #bindingKey()} that were resolved in {@link #owningComponent()}.
   */
  ImmutableSet<? extends Binding> ownedBindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return ownedContributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(ownedMembersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  /**
   * All contribution bindings, regardless of owning component. Empty if this is a members-injection
   * binding.
   */
  ImmutableSet<ContributionBinding> contributionBindings() {
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  /**
   * The contribution bindings that were resolved in {@link #owningComponent()}. Empty if this is a
   * members-injection binding.
   */
  ImmutableSet<ContributionBinding> ownedContributionBindings() {
    return allContributionBindings().get(owningComponent());
  }

  /**
   * The members-injection binding, regardless of owning component. Empty if these are contribution
   * bindings.
   */
  Optional<MembersInjectionBinding> membersInjectionBinding() {
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        FluentIterable.from(allMembersInjectionBindings().values()).toSet();
    return membersInjectionBindings.isEmpty()
        ? Optional.<MembersInjectionBinding>absent()
        : Optional.of(Iterables.getOnlyElement(membersInjectionBindings));
  }

  /**
   * The members-injection binding that was resolved in {@link #owningComponent()}. Empty if these
   * are contribution bindings.
   */
  Optional<MembersInjectionBinding> ownedMembersInjectionBinding() {
    return Optional.fromNullable(allMembersInjectionBindings().get(owningComponent()));
  }

  /**
   * Creates a {@link ResolvedBindings} for contribution bindings.
   */
  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Multimap<ComponentDescriptor, ? extends ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>copyOf(contributionBindings),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.copyOf(multibindings));
  }

  /**
   * Creates a {@link ResolvedBindings} for contribution bindings.
   */
  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      ContributionBinding... ownedContributionBindings) {
    return forContributionBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>builder()
            .putAll(owningComponent, ownedContributionBindings)
            .build(),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  /**
   * Creates a {@link ResolvedBindings} for members injection bindings.
   */
  static ResolvedBindings forMembersInjectionBinding(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.of(owningComponent, ownedMembersInjectionBinding),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  /**
   * Creates a {@link ResolvedBindings} appropriate for when there are no bindings for the key.
   */
  static ResolvedBindings noBindings(BindingKey bindingKey, ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  /**
   * Returns a {@code ResolvedBindings} with the same {@link #bindingKey()} and {@link #bindings()}
   * as this one, but no {@link #ownedBindings()}.
   */
  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey(),
        owningComponent,
        allContributionBindings(),
        allMembersInjectionBindings(),
        multibindingDeclarations());
  }

  /**
   * {@code true} if this is a multibindings contribution.
   */
  boolean isMultibindings() {
    return !(contributionBindings().isEmpty() && multibindingDeclarations().isEmpty())
        && contributionType().isMultibinding();
  }

  /**
   * {@code true} if this is a unique contribution binding.
   */
  boolean isUniqueContribution() {
    return !contributionBindings().isEmpty() && !contributionType().isMultibinding();
  }

  /**
   * The binding type for all {@link #bindings()} and {@link #multibindingDeclarations()}.
   *
   * @throws IllegalStateException if {@link #isEmpty()} or the binding types conflict
   */
  @Override
  public BindingType bindingType() {
    checkState(!isEmpty(), "empty bindings for %s", bindingKey());
    ImmutableSet<BindingType> bindingTypes =
        FluentIterable.from(concat(bindings(), multibindingDeclarations()))
            .transform(BindingType.BINDING_TYPE)
            .toSet();
    checkState(bindingTypes.size() == 1, "conflicting binding types: %s", this);
    return getOnlyElement(bindingTypes);
  }

  /**
   * The contribution type for these bindings.
   *
   * @throws IllegalStateException if {@link #isEmpty()} or the contribution types conflict
   */
  @Override
  public ContributionType contributionType() {
    ImmutableSet<ContributionType> types = contributionTypes();
    checkState(!types.isEmpty(), "no bindings or declarations for %s", bindingKey());
    checkState(types.size() == 1, MULTIPLE_CONTRIBUTION_TYPES_FORMAT, types);
    return getOnlyElement(types);
  }

  /**
   * The contribution types represented by {@link #contributionBindings()} and
   * {@link #multibindingDeclarations()}.
   */
  ImmutableSet<ContributionType> contributionTypes() {
    return bindingsAndDeclarationsByContributionType().keySet();
  }

  /**
   * The {@link #contributionBindings()} and {@link #multibindingDeclarations()}, indexed by
   * {@link ContributionType}.
   */
  ImmutableListMultimap<ContributionType, HasSourceElement>
      bindingsAndDeclarationsByContributionType() {
    return new ImmutableListMultimap.Builder<ContributionType, HasSourceElement>()
        .putAll(indexByContributionType(contributionBindings()))
        .putAll(indexByContributionType(multibindingDeclarations()))
        .build();
  }

  /**
   * The name of the package in which these bindings must be managed, for
   * example if a binding references non-public types.
   * 
   * @throws IllegalArgumentException if the bindings must be managed in more than one package
   */
  Optional<String> bindingPackage() {
    ImmutableSet.Builder<String> bindingPackagesBuilder = ImmutableSet.builder();
    for (Binding binding : bindings()) {
      bindingPackagesBuilder.addAll(binding.bindingPackage().asSet());
    }
    ImmutableSet<String> bindingPackages = bindingPackagesBuilder.build();
    switch (bindingPackages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(bindingPackages.iterator().next());
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * The framework class associated with these bindings.
   */
  Class<?> frameworkClass() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return Iterables.any(contributionBindings(), BindingType.isOfType(BindingType.PRODUCTION))
            ? BindingType.PRODUCTION.frameworkClass()
            : BindingType.PROVISION.frameworkClass();
      case MEMBERS_INJECTION:
        return MembersInjector.class;
      default:
        throw new AssertionError();
    }
  }
}
