/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.subcomponent;

import dagger.Subcomponent;

/**
 * A subcomponent that's not resolvable in any parent component, for testing that qualified methods
 * on components that return subcomponents do not trigger actual subcomponents.
 */
@Subcomponent
interface UnresolvableChildComponent {
  /**
   * Requests a type that is never bound in any component that this subcomponent might be installed
   * in. If this subcomponent is ever attempted to be installed in a component, then it will produce
   * a compiler error.
   */
  @Unbound
  String unboundString();

  @Subcomponent.Builder
  interface Builder {
    UnresolvableChildComponent build();
  }
}
