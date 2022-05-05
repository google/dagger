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

import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.xprocessing.XElements.isAbstract;
import static dagger.internal.codegen.xprocessing.XElements.isStatic;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import java.util.Optional;

/**
 * An abstract type for classes representing a Dagger binding. Particularly, contains the element
 * that generated the binding and the {@link DependencyRequest} instances that are required to
 * satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding to the
 * subtypes.
 */
public abstract class Binding extends BindingDeclaration {

  /**
   * Returns {@code true} if using this binding requires an instance of the {@link
   * #contributingModule()}.
   */
  public boolean requiresModuleInstance() {
    return contributingModule().isPresent()
        && bindingElement().isPresent()
        && !isAbstract(bindingElement().get())
        && !isStatic(bindingElement().get());
  }

  /**
   * Returns {@code true} if this binding may provide {@code null} instead of an instance of {@link
   * #key()}. Nullable bindings cannot be requested from {@linkplain DependencyRequest#isNullable()
   * non-nullable dependency requests}.
   */
  public abstract boolean isNullable();

  /** The kind of binding this instance represents. */
  public abstract BindingKind kind();

  /** The {@link BindingType} of this binding. */
  public abstract BindingType bindingType();

  /** The {@link FrameworkType} of this binding. */
  public final FrameworkType frameworkType() {
    return FrameworkType.forBindingType(bindingType());
  }

  /**
   * The explicit set of {@link DependencyRequest dependencies} required to satisfy this binding as
   * defined by the user-defined injection sites.
   */
  public abstract ImmutableSet<DependencyRequest> explicitDependencies();

  /**
   * The set of {@link DependencyRequest dependencies} that are added by the framework rather than a
   * user-defined injection site. This returns an unmodifiable set.
   */
  public ImmutableSet<DependencyRequest> implicitDependencies() {
    return ImmutableSet.of();
  }

  private final Supplier<ImmutableSet<DependencyRequest>> dependencies =
      memoize(
          () -> {
            ImmutableSet<DependencyRequest> implicitDependencies = implicitDependencies();
            return ImmutableSet.copyOf(
                implicitDependencies.isEmpty()
                    ? explicitDependencies()
                    : Sets.union(implicitDependencies, explicitDependencies()));
          });

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding. This is the
   * union of {@link #explicitDependencies()} and {@link #implicitDependencies()}. This returns an
   * unmodifiable set.
   */
  public final ImmutableSet<DependencyRequest> dependencies() {
    return dependencies.get();
  }

  /**
   * If this binding's key's type parameters are different from those of the {@link
   * #bindingTypeElement()}, this is the binding for the {@link #bindingTypeElement()}'s unresolved
   * type.
   */
  public abstract Optional<? extends Binding> unresolved();

  public Optional<Scope> scope() {
    return Optional.empty();
  }
}
