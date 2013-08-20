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

  static class TestClass {
    @MyQualifier int field1; // non-injectable field
    
    @SuppressWarnings("some string other than 'qualifiers'")
    @MyQualifier 
    int field2;
    
    @SuppressWarnings("qualifiers")
    @MyQualifier 
    int fieldWithWarningSuppressed1;
    
    @SuppressWarnings({"foo", "qualifiers", "bar"})
    @MyQualifier 
    int fieldWithWarningSuppressed2;
    
    // qualfier on non-injectable constructor parameter
    public TestClass(@MyQualifier String constructorParam) {}
    
    @MyQualifier 
    void nonProvidesMethod(@MyQualifier String methodParam) {}
  }
  
  @Qualifier
  @Retention(value = RUNTIME)
  @interface MyQualifier {}
}
