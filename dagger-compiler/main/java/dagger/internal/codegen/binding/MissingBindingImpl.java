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
import dagger.internal.codegen.model.BindingGraph.MissingBinding;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.Key;

/** An implementation of {@link MissingBinding}. */
@AutoValue
abstract class MissingBindingImpl extends MissingBinding {
  static MissingBinding create(ComponentPath component, Key key) {
    return new AutoValue_MissingBindingImpl(component, key);
  }

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object o);
}