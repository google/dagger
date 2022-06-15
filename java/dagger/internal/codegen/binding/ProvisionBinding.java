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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.spi.model.BindingKind.COMPONENT_PROVISION;
import static dagger.spi.model.BindingKind.PROVISION;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.Scope;
import java.util.Optional;

/** A value object representing the mechanism by which a {@link Key} can be provided. */
@CheckReturnValue
@AutoValue
public abstract class ProvisionBinding extends ContributionBinding {

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> explicitDependencies() {
    return ImmutableSet.<DependencyRequest>builder()
        .addAll(provisionDependencies())
        .addAll(membersInjectionDependencies())
        .build();
  }

  /**
   * Dependencies necessary to invoke an {@code @Inject} constructor or {@code @Provides} method.
   */
  public abstract ImmutableSet<DependencyRequest> provisionDependencies();

  @Memoized
  ImmutableSet<DependencyRequest> membersInjectionDependencies() {
    return injectionSites()
        .stream()
        .flatMap(i -> i.dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * {@link InjectionSite}s for all {@code @Inject} members if {@link #kind()} is {@link
   * BindingKind#INJECTION}, otherwise empty.
   */
  public abstract ImmutableSortedSet<InjectionSite> injectionSites();

  @Override
  public BindingType bindingType() {
    return BindingType.PROVISION;
  }

  @Override
  public abstract Optional<ProvisionBinding> unresolved();

  // TODO(ronshapiro): we should be able to remove this, but AutoValue barks on the Builder's scope
  // method, saying that the method doesn't correspond to a property of ProvisionBinding
  @Override
  public abstract Optional<Scope> scope();

  public static Builder builder() {
    return new AutoValue_ProvisionBinding.Builder()
        .provisionDependencies(ImmutableSet.of())
        .injectionSites(ImmutableSortedSet.of());
  }

  @Override
  public abstract Builder toBuilder();

  private static final ImmutableSet<BindingKind> KINDS_TO_CHECK_FOR_NULL =
      ImmutableSet.of(PROVISION, COMPONENT_PROVISION);

  public boolean shouldCheckForNull(CompilerOptions compilerOptions) {
    return KINDS_TO_CHECK_FOR_NULL.contains(kind())
        && !contributedPrimitiveType().isPresent()
        && !nullableType().isPresent()
        && compilerOptions.doCheckForNulls();
  }

  // Profiling determined that this method is called enough times that memoizing it had a measurable
  // performance improvement for large components.
  @Memoized
  @Override
  public boolean requiresModuleInstance() {
    return super.requiresModuleInstance();
  }

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  /** A {@link ProvisionBinding} builder. */
  @AutoValue.Builder
  public abstract static class Builder
      extends ContributionBinding.Builder<ProvisionBinding, Builder> {

    @CanIgnoreReturnValue
    @Override
    public Builder dependencies(Iterable<DependencyRequest> dependencies) {
      return provisionDependencies(dependencies);
    }

    abstract Builder provisionDependencies(Iterable<DependencyRequest> provisionDependencies);

    public abstract Builder injectionSites(ImmutableSortedSet<InjectionSite> injectionSites);

    @CanIgnoreReturnValue // TODO(kak): remove this once open-source checkers understand AutoValue
    @Override
    public abstract Builder unresolved(ProvisionBinding unresolved);

    public abstract Builder scope(Optional<Scope> scope);
  }

}
