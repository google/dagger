/*
 * Copyright (C) 2015 Google, Inc.
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

package test.multipackage;

import dagger.Component;
import java.util.Set;
import test.multipackage.a.AModule;
import test.multipackage.sub.FooChildComponent;

/**
 * A component that tests the interaction between subcomponents, multiple packages, and
 * multibindings. Specifically, we want:
 * <ul>
 * <li>A set binding with some contributions in the parent component, and some in the subcomponent.
 * <li>The contributions come from different packages, but not the package of either component.
 * <li>The set binding is requested in the subcomponent through a binding from a separate package.
 * <li>No binding in the subcomponent, that's in the subcomponent's package, directly uses any
 *     binding from the component's package.
 * </ul>
 */
// NOTE(beder): Be careful about changing any bindings in either this component or the subcomponent.
// Even adding a binding might stop this test from testing what it's supposed to test.
@Component(modules = {AModule.class})
interface FooComponent {
  Set<String> setOfString();

  FooChildComponent fooChildComponent();
}
