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

package dagger.functional.assisted.subpackage;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/** An accessible type using assisted inject from a separate package. */
public final class AccessibleFoo {
  // Accessible for testing
  public final Dep dep;
  public final AssistedDep assistedDep;

  @AssistedInject
  AccessibleFoo(Dep dep, @Assisted AssistedDep assistedDep) {
    this.dep = dep;
    this.assistedDep = assistedDep;
  }
}
