/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.internal.builders;

import androidx.lifecycle.SavedStateHandle;
import dagger.BindsInstance;
import dagger.hilt.DefineComponent;
import dagger.hilt.android.components.ViewModelComponent;

/** Interface for creating a {@link ViewModelComponent}. */
@DefineComponent.Builder
public interface ViewModelComponentBuilder {
  ViewModelComponentBuilder savedStateHandle(@BindsInstance SavedStateHandle handle);

  ViewModelComponent build();
}
