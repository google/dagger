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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.Nullability;
import java.util.Optional;

/** A binding for a {@link BindingKind#PRODUCTION}. */
@CheckReturnValue
@AutoValue
public abstract class ProductionBinding extends ContributionBinding {
  @Override
  public BindingKind kind() {
    return BindingKind.PRODUCTION;
  }

  @Override
  public Optional<BindingType> optionalBindingType() {
    return Optional.of(BindingType.PRODUCTION);
  }
  @Override
  @Memoized
  public ContributionType contributionType() {
    return ContributionType.fromBindingElement(bindingElement().get());
  }

  @Override
  public Nullability nullability() {
    return Nullability.NOT_NULLABLE;
  }

  /** Dependencies necessary to invoke the {@code @Produces} method. */
  public abstract ImmutableSet<DependencyRequest> explicitDependencies();

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> dependencies() {
    return ImmutableSet.<DependencyRequest>builder()
        .add(executorRequest())
        .add(monitorRequest())
        .addAll(explicitDependencies())
        .build();
  }

  public abstract DependencyRequest executorRequest();

  public abstract DependencyRequest monitorRequest();

  // Profiling determined that this method is called enough times that memoizing it had a measurable
  // performance improvement for large components.
  @Memoized
  @Override
  public boolean requiresModuleInstance() {
    return super.requiresModuleInstance();
  }

  @Override
  public abstract Builder toBuilder();

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  static Builder builder() {
    return new AutoValue_ProductionBinding.Builder();
  }

  /** A {@link ProductionBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder extends ContributionBinding.Builder<ProductionBinding, Builder> {
    abstract Builder executorRequest(DependencyRequest executorRequest);

    abstract Builder monitorRequest(DependencyRequest monitorRequest);

    abstract Builder explicitDependencies(Iterable<DependencyRequest> explicitDependencies);
  }
}
