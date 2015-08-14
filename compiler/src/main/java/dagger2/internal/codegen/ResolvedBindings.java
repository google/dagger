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
package dagger2.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * The collection of bindings that have been resolved for a binding key.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class ResolvedBindings {
  abstract BindingKey bindingKey();
  abstract ImmutableSet<? extends Binding> ownedBindings();
  abstract ImmutableSet<? extends Binding> bindings();

  static ResolvedBindings create(
      BindingKey bindingKey,
      Set<? extends Binding> ownedBindings,
      Set<? extends Binding> inheritedBindings) {
    ImmutableSet<Binding> immutableOwnedBindings = ImmutableSet.copyOf(ownedBindings);
    return new AutoValue_ResolvedBindings(
        bindingKey,
        immutableOwnedBindings,
        ImmutableSet.<Binding>builder()
        .addAll(inheritedBindings)
        .addAll(immutableOwnedBindings)
        .build());
  }

  static ResolvedBindings create(
      BindingKey bindingKey,
      Binding... ownedBindings) {
    ImmutableSet<Binding> bindings = ImmutableSet.copyOf(ownedBindings);
    return new AutoValue_ResolvedBindings(bindingKey, bindings, bindings);
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends ContributionBinding> ownedContributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return (ImmutableSet<? extends ContributionBinding>) ownedBindings();
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends ContributionBinding> contributionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION));
    return (ImmutableSet<? extends ContributionBinding>) bindings();
  }

  @SuppressWarnings("unchecked")  // checked by validator
  ImmutableSet<? extends MembersInjectionBinding> membersInjectionBindings() {
    checkState(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return (ImmutableSet<? extends MembersInjectionBinding>) bindings();
  }
}
