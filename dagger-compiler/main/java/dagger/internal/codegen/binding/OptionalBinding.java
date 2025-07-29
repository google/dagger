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

/** A binding for a {@link BindingKind#OPTIONAL}. */
@CheckReturnValue
@AutoValue
public abstract class OptionalBinding extends ContributionBinding {
  @Override
  public BindingKind kind() {
    return BindingKind.OPTIONAL;
  }

  @Override
  public ContributionType contributionType() {
    return ContributionType.UNIQUE;
  }

  @Override
  public Nullability nullability() {
    return Nullability.NOT_NULLABLE;
  }

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> dependencies() {
    return delegateRequest().isPresent()
        ? ImmutableSet.of(delegateRequest().get())
        : ImmutableSet.of();
  }

  /** Returns the delegate {@link DependencyRequest} if this represents a "present" optional. */
  abstract Optional<DependencyRequest> delegateRequest();

  @Override
  public boolean requiresModuleInstance() {
    return false;
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
    return new AutoValue_OptionalBinding.Builder();
  }

  /** A {@link OptionalBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder extends ContributionBinding.Builder<OptionalBinding, Builder> {
    abstract Builder delegateRequest(DependencyRequest delegateRequest);

    abstract Builder optionalBindingType(Optional<BindingType> optionalBindingType);
  }
}
