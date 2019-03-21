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

import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.model.Scope;

/** An implementation of {@link ComponentNode} that also exposes the {@link ComponentDescriptor}. */
@AutoValue
abstract class ComponentNodeImpl implements ComponentNode {
  static ComponentNode create(
      ComponentPath componentPath, ComponentDescriptor componentDescriptor) {
    return new AutoValue_ComponentNodeImpl(componentPath, componentDescriptor);
  }

  @Override
  public final boolean isSubcomponent() {
    return componentDescriptor().isSubcomponent();
  }

  @Override
  public boolean isRealComponent() {
    return componentDescriptor().isRealComponent();
  }

  @Override
  public final ImmutableSet<DependencyRequest> entryPoints() {
    return componentDescriptor().entryPointMethods().stream()
        .map(method -> method.dependencyRequest().get())
        .collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<Scope> scopes() {
    return componentDescriptor().scopes();
  }

  abstract ComponentDescriptor componentDescriptor();

  @Override
  public final String toString() {
    return componentPath().toString();
  }
}
