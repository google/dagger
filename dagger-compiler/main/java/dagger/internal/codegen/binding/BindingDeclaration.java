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

import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Scope;
import java.util.Optional;

/** An object that declares or specifies a binding. */
public abstract class BindingDeclaration extends Declaration {

  /**
   * Returns {@code true} if using this binding requires an instance of the {@link
   * #contributingModule()}.
   */
  public abstract boolean requiresModuleInstance();

  /**
   * Returns {@code true} if this binding may provide {@code null} instead of an instance of {@link
   * #key()}. Nullable bindings cannot be requested from {@linkplain DependencyRequest#isNullable()
   * non-nullable dependency requests}.
   */
  public abstract boolean isNullable();

  /** The kind of binding this instance represents. */
  public abstract BindingKind kind();

  /** The set of {@link DependencyRequest dependencies} required to satisfy this binding. */
  public abstract ImmutableSet<DependencyRequest> dependencies();

  /**
   * If this binding's key's type parameters are different from those of the {@link
   * #bindingTypeElement()}, this is the binding for the {@link #bindingTypeElement()}'s unresolved
   * type.
   */
  public abstract Optional<? extends Binding> unresolved();

  /** Returns the optional scope used on the binding. */
  public abstract Optional<Scope> scope();
}
