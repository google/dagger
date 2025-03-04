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

import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.Key;

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
  /** Creates a {@link ResolvedBindings} appropriate for when there are no bindings for a key. */
  static ResolvedBindings create(Key key) {
    return create(key, ImmutableSet.of());
  }

  /** Creates a {@link ResolvedBindings} for a single binding. */
  static ResolvedBindings create(Key key, BindingNode bindingNode) {
    return create(key, ImmutableSet.of(bindingNode));
  }

  /** Creates a {@link ResolvedBindings} for multiple bindings. */
  static ResolvedBindings create(Key key, ImmutableSet<BindingNode> bindingNodes) {
    return new AutoValue_ResolvedBindings(key, bindingNodes);
  }

  /** The binding key for which the {@link #bindings()} have been resolved. */
  abstract Key key();

  /** All binding nodes for {@link #key()}, regardless of which component owns them. */
  abstract ImmutableSet<BindingNode> bindingNodes();

  // Computing the hash code is an expensive operation.
  @Memoized
  @Override
  public abstract int hashCode();

  // Suppresses ErrorProne warning that hashCode was overridden w/o equals
  @Override
  public abstract boolean equals(Object other);

  /** All bindings for {@link #key()}, regardless of which component owns them. */
  final ImmutableSet<Binding> bindings() {
    return bindingNodes().stream()
        .map(BindingNode::delegate)
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if there are no {@link #bindings()}. */
  final boolean isEmpty() {
    return bindingNodes().isEmpty();
  }

  /** All bindings for {@link #key()} that are owned by a component. */
  ImmutableSet<BindingNode> bindingNodesOwnedBy(ComponentPath componentPath) {
    return bindingNodes().stream()
        .filter(bindingNode -> bindingNode.componentPath().equals(componentPath))
        .collect(toImmutableSet());
  }

  /** Returns the binding node representing the given binding, or throws ISE if none exist. */
  final BindingNode forBinding(Binding binding) {
    return bindingNodes().stream()
        .filter(bindingNode -> bindingNode.delegate().equals(binding))
        .collect(onlyElement());
  }
}
