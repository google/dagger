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

package dagger.functional;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.Subcomponent;
import javax.inject.Provider;
import javax.inject.Qualifier;

@Component(modules = ComponentWithReusableBindings.ReusableBindingsModule.class)
interface ComponentWithReusableBindings {

  @Qualifier
  @interface InParent {}

  @Qualifier
  @interface InChildren {}

  @InParent
  Object reusableInParent();

  ChildOne childOne();

  ChildTwo childTwo();

  // b/77150738
  int primitive();

  // b/77150738: This is used as a regression test for fastInit mode's switching providers. In
  // particular, it occurs when a @Provides method returns the boxed type but the component method
  // returns the unboxed type, and the instance is requested from a SwitchingProvider.
  boolean unboxedPrimitive();

  // b/77150738
  Provider<Boolean> booleanProvider();

  @Subcomponent
  interface ChildOne {
    @InParent
    Object reusableInParent();

    @InChildren
    Object reusableInChild();
  }

  @Subcomponent
  interface ChildTwo {
    @InParent
    Object reusableInParent();

    @InChildren
    Object reusableInChild();
  }

  @Module
  static class ReusableBindingsModule {
    @Provides
    @Reusable
    @InParent
    static Object inParent() {
      return new Object();
    }

    @Provides
    @Reusable
    @InChildren
    static Object inChildren() {
      return new Object();
    }

    // b/77150738
    @Provides
    @Reusable
    static int primitive() {
      return 0;
    }

    // b/77150738
    @Provides
    @Reusable
    static Boolean boxedPrimitive() {
      return false;
    }
  }
}
