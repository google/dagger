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

package dagger.functional.tck;

import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;
import org.atinject.tck.auto.Engine;
import org.atinject.tck.auto.V8Engine;

@Module
public class EngineModule {
  @Provides
  static Engine provideEngine(MembersInjector<V8Engine> injector) {
    // This is provided because V8Engine has no @Inject constructor and Dagger requires an @Inject
    // constructor, however this is a TCK supplied class that we prefer to leave unmodified.
    V8Engine engine = new V8Engine();
    injector.injectMembers(engine);
    return engine;
  }
}
