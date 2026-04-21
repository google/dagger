/*
 * Copyright (C) 2024 The Dagger Authors.
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

import androidx.room3.compiler.processing.XMethodElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.Nullability;
import java.util.Optional;

/**
 * A binding for a {@link BindingKind#INJECTION}.
 *
 * <p>This also represents parameterless {@code @Binds} methods, which are used to explicitly bind
 * an {@link javax.inject.Inject}-annotated constructor. By treating these as {@link
 * InjectionBinding}s rather than {@code DelegateBinding}s, we avoid issues with duplicate bindings
 * and cyclical dependencies, as both the {@code @Binds} method and the {@code @Inject} constructor
 * would otherwise have the same key.
 */
@CheckReturnValue
@AutoValue
public abstract class InjectionBinding extends ContributionBinding {
  @Override
  public BindingKind kind() {
    return BindingKind.INJECTION;
  }

  @Override
  public Optional<BindingType> optionalBindingType() {
    return Optional.of(BindingType.PROVISION);
  }

  @Override
  public ContributionType contributionType() {
    return ContributionType.UNIQUE;
  }

  @Override
  public Nullability nullability() {
    return Nullability.NOT_NULLABLE;
  }

  /** Dependencies necessary to invoke the {@code @Inject} annotated constructor. */
  public abstract ImmutableSet<DependencyRequest> constructorDependencies();

  /** {@link InjectionSite}s for all {@code @Inject} members. */
  public abstract ImmutableSortedSet<InjectionSite> injectionSites();

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> dependencies() {
    return ImmutableSet.<DependencyRequest>builder()
        .addAll(constructorDependencies())
        .addAll(
            injectionSites().stream()
                .flatMap(i -> i.dependencies().stream())
                .collect(toImmutableSet()))
        .build();
  }

  @Override
  public boolean requiresModuleInstance() {
    return false;
  }

  @Override
  public abstract Builder toBuilder();

  /** The element that declares this binding, used for parameterless {@code @Binds} methods. */
  public abstract Optional<XMethodElement> declaringElement();

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  static Builder builder() {
    return new AutoValue_InjectionBinding.Builder();
  }

  /** A {@link InjectionBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder extends ContributionBinding.Builder<InjectionBinding, Builder> {
    abstract Builder constructorDependencies(Iterable<DependencyRequest> constructorDependencies);

    abstract Builder injectionSites(ImmutableSortedSet<InjectionSite> injectionSites);

    abstract Builder declaringElement(XMethodElement declaringElement);
  }
}
