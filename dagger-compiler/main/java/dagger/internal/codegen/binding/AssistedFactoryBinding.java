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
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.Nullability;
import java.util.Optional;

/** A binding for a {@link BindingKind#ASSISTED_FACTORY}. */
@CheckReturnValue
@AutoValue
public abstract class AssistedFactoryBinding extends ContributionBinding {
  @Override
  public BindingKind kind() {
    return BindingKind.ASSISTED_FACTORY;
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

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> dependencies() {
    return ImmutableSet.of(
        DependencyRequest.builder()
            .key(assistedInjectKey())
            .kind(RequestKind.PROVIDER)
            .build());
  }

  /** Returns the key for the associated {@code @AssistedInject} binding. */
  public abstract Key assistedInjectKey();

  @Override
  public abstract Builder toBuilder();

  @Memoized
  @Override
  public abstract int hashCode();

  // TODO(ronshapiro,dpb): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  static Builder builder() {
    return new AutoValue_AssistedFactoryBinding.Builder();
  }

  /** A {@link AssistedFactoryBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder
      extends ContributionBinding.Builder<AssistedFactoryBinding, Builder> {
    abstract Builder assistedInjectKey(Key assistedInjectKey);
  }
}
