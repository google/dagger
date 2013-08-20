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
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Qualifier;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

class TestApp {
  static class TestClass1 {
    @Inject @MyQualifier1 String field;
  }
  
  static class TestClass2 {
    String string;
    
    @Inject
    public TestClass2(@MyQualifier1 String constructorParam) {
      this.string = string;
    }
  }

  @Module(injects = TestClass1.class)
  static class TestModule {

    @Provides
    @MyQualifier1
    String providesString(@MyQualifier2 String providesMethodParam) {
      return providesMethodParam + "foo";
    }
    
    @Provides
    @MyQualifier2
    String providesString() {
      return "foo";
    }
  }
  @Qualifier
  @Retention(value = RUNTIME)
  @interface MyQualifier1 {}
  
  @Qualifier
  @Retention(value = RUNTIME)
  @interface MyQualifier2 {}
}
