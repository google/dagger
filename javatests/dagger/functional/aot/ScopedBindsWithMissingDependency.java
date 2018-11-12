/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.aot;

import dagger.Binds;
import dagger.Module;
import dagger.Reusable;
import dagger.Subcomponent;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Scope;

/**
 * A regression test for ahead-of-time subcomponents mode where a scoped {@link Binds} method whose
 * dependency was missing in a partial subcomponent implementation threw an exception in the
 * processor.
 */
final class ScopedBindsWithMissingDependency {

  @Retention(RetentionPolicy.RUNTIME)
  @Scope
  @interface CustomScope {}

  @Module
  interface ScopedBindsWithMissingDependencyModule {
    @Binds
    @CustomScope
    Object bindsCustomScopeToMissingDep(String missingDependency);

    @Binds
    @Reusable
    CharSequence bindsReusableScopeToMissingDep(String missingDependency);
  }

  @CustomScope
  @Subcomponent(modules = ScopedBindsWithMissingDependencyModule.class)
  interface HasScopedBindsWithMissingDependency {
    Object customScopedBindsWithMissingDependency();
    CharSequence reusableScopedBindsWithMissingDependency();
  }
}
