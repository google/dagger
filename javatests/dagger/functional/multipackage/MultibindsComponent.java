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

package dagger.functional.multipackage;

import dagger.Component;
import dagger.functional.multipackage.a.AMultibindsModule;
import dagger.functional.multipackage.a.UsesInaccessible;

/**
 * A component that tests the interaction between multiple packages and {@code @Multibinding}s.
 * Specifically, we want:
 *
 * <ul>
 * <li>A {@code @Multibinding} for an empty set of a type not accessible from this package.
 * <li>A {@code @Multibinding} for an empty map of a type not accessible from this package.
 * <li>A public type that injects the empty set and map of inaccessible objects.
 * </ul>
 */
@Component(modules = {AMultibindsModule.class})
interface MultibindsComponent {
  UsesInaccessible usesInaccessible();
}
