/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional;

import dagger.Component;
import dagger.functional.sub.OtherThing;
import javax.inject.Inject;

@Component(dependencies = {NonComponentDependencyComponent.ThingComponent.class})
interface NonComponentDependencyComponent {
  ThingTwo thingTwo();

  static class ThingTwo {
    @SuppressWarnings("unused")
    @Inject
    ThingTwo(
        Thing thing,
        NonComponentDependencyComponent nonComponentDependencyComponent,
        NonComponentDependencyComponent.ThingComponent thingComponent) {}
  }

  // A non-component interface which this interface depends upon.
  interface ThingComponent {
    Thing thing();
  }

  // The implementation for that interface.
  static class ThingComponentImpl implements ThingComponent {
    @Override
    public Thing thing() {
      return new Thing(new OtherThing(1));
    }
  }
}
