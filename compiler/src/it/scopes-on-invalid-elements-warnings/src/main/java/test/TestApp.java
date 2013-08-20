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

import javax.inject.Singleton;

class TestApp {
   
    // Scoped non @Provides method
    @Singleton
    void method1() {}
    
    @SuppressWarnings("some string other than 'scoping'")
    @Singleton
    void method2() {}
    
    @SuppressWarnings("scoping")
    @Singleton
    void methodWithWarningSupressed1() {}
    
    @SuppressWarnings({"foo", "scoping", "bar"})
    @Singleton
    void methodWithWarningSupressed2() {}
}
