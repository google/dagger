/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package test;

import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;

class TestApp {

  static class TestClass1 {
    // Scoped Injectable field
    @Inject
    @Singleton
    Integer field;

    // method with a scoped parameter
    void method(@Singleton int param) {}
  }

  static class TestClass2 {
    // Scoped non-injectable field
    @Singleton
    String string;
  }

  @Module(complete = false, library = true)
  static class TestModule {
    
    // Even though it's a @Provides method, its parameters cannot be scoped
    @Provides
    Integer integer(@Singleton int myInt) {
      return myInt;
    }
  }
}
