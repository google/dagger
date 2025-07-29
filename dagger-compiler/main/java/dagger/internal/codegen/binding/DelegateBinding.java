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

/** A binding for a {@link BindingKind#DELEGATE}. */
@CheckReturnValue
@AutoValue
public abstract class DelegateBinding extends ContributionBinding {
  @Override
  public BindingKind kind() {
    return BindingKind.DELEGATE;
  }

  @Override
  @Memoized
  public ImmutableSet<DependencyRequest> dependencies() {
    return ImmutableSet.of(delegateRequest());
  }

  /** Returns a request for the binding that this binding delegates to. */
  abstract DependencyRequest delegateRequest();

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
    return new AutoValue_DelegateBinding.Builder();
  }

  /** A {@link DelegateBinding} builder. */
  @AutoValue.Builder
  abstract static class Builder extends ContributionBinding.Builder<DelegateBinding, Builder> {
    abstract Builder delegateRequest(DependencyRequest delegateRequest);

    abstract Builder optionalBindingType(Optional<BindingType> bindingType);

    abstract Builder contributionType(ContributionType contributionType);

    abstract Builder nullability(Nullability nullability);
  }
}
