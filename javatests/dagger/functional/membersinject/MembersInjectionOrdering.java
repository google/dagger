/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.membersinject;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;

/**
 * This exhibits a regression case, that albeit weird, is valid according to the JSR 330 spec. JSR
 * 330 specifies a rough ordering by which members should be injected, and it is possible to rely on
 * such ordering. When members injecting {@link Subtype}, field injection is guaranteed to be
 * performed on {@link Base} first. The binding for {@code @FirstToString} in {@link
 * OrderingModule#provideToString()} relies on this ordering, and thus uses the value in {@link
 * Base#first} to satisfy the binding.
 */
class MembersInjectionOrdering {
  static class Base {
    @Inject First first;
  }

  static class Subtype extends Base {
    @Inject String firstToString;
  }

  @Module
  static class OrderingModule {
    private final Subtype subtype;

    OrderingModule(Subtype subtype) {
      this.subtype = subtype;
    }

    @Provides
    String provideToString() {
      return subtype.first.toString();
    }
  }

  @Component(modules = OrderingModule.class)
  interface TestComponent {
    void inject(Subtype subtype);
  }

  static class First {
    @Inject
    First() {}
  }
}
