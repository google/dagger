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

package dagger.functional.subcomponent;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Inject;

/** Supporting types for {@link ModuleWithSubcomponentsTest}. */
@Component(modules = UsesModuleSubcomponents.ModuleWithSubcomponents.class)
public interface UsesModuleSubcomponents {
  UsesChild usesChild();

  Set<String> strings();

  @Module(subcomponents = Child.class, includes = AlsoIncludesSubcomponents.class)
  class ModuleWithSubcomponents {
    @Provides
    @IntoSet
    static String provideStringInParent() {
      return "from parent";
    }
  }

  @Module(subcomponents = Child.class)
  class AlsoIncludesSubcomponents {}

  @Subcomponent(modules = ChildModule.class)
  interface Child {
    Set<String> strings();

    @Subcomponent.Builder
    interface Builder {
      Child build();
    }
  }

  @Module
  class ChildModule {
    @Provides
    @IntoSet
    static String provideStringInChild() {
      return "from child";
    }
  }

  class UsesChild {
    Set<String> strings;

    @Inject
    UsesChild(Child.Builder childBuilder) {
      this.strings = childBuilder.build().strings();
    }
  }

  @Module(includes = ModuleWithSubcomponents.class)
  class OnlyIncludesModuleWithSubcomponents {}

  @Component(modules = OnlyIncludesModuleWithSubcomponents.class)
  interface ParentIncludesSubcomponentTransitively extends UsesModuleSubcomponents {}

}
