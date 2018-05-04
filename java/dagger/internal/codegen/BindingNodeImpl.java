/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.ComponentPath;
import dagger.multibindings.Multibinds;
import java.util.function.Supplier;
import javax.lang.model.element.Element;

/**
 * An implementation of {@link BindingNode} that also exposes {@link BindingDeclaration}s associated
 * with the binding.
 */
@AutoValue
abstract class BindingNodeImpl implements BindingNode {
  static BindingNode create(
      ComponentPath component,
      dagger.model.Binding binding,
      Iterable<BindingDeclaration> associatedDeclarations,
      Supplier<String> toStringFunction) {
    BindingNodeImpl node =
        new AutoValue_BindingNodeImpl(
            component, binding, ImmutableSet.copyOf(associatedDeclarations));
    node.toStringFunction = checkNotNull(toStringFunction);
    return node;
  }

  private Supplier<String> toStringFunction;

  /**
   * The {@link Element}s (other than the binding's {@link dagger.model.Binding#bindingElement()})
   * that are associated with the binding.
   *
   * <ul>
   *   <li>{@linkplain BindsOptionalOf optional binding} declarations
   *   <li>{@linkplain Module#subcomponents() module subcomponent} declarations
   *   <li>{@linkplain Multibinds multibinding} declarations
   * </ul>
   */
  abstract ImmutableSet<BindingDeclaration> associatedDeclarations();

  @Override
  public final String toString() {
    return toStringFunction.get();
  }
}
