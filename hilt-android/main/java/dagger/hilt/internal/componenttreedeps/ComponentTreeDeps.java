/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.internal.componenttreedeps;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An annotation that kicks off the generation of a component tree. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ComponentTreeDeps {

  /** Returns the set of {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot} dependencies. */
  Class<?>[] rootDeps() default {};

  /**
   * Returns the set of {@link dagger.hilt.internal.definecomponent.DefineComponentClasses}
   * dependencies.
   */
  Class<?>[] defineComponentDeps() default {};

  /** Returns the set of {@link dagger.hilt.internal.aliasof.AliasOfPropagatedData} dependencies. */
  Class<?>[] aliasOfDeps() default {};

  /** Returns the set of {@link dagger.hilt.internal.aggregateddeps.AggregatedDeps} dependencies. */
  Class<?>[] aggregatedDeps() default {};

  /**
   * Returns the set of {@link
   * dagger.hilt.internal.uninstallmodules.AggregatedUninstallModulesMetadata} dependencies.
   */
  Class<?>[] uninstallModulesDeps() default {};

  /**
   * Returns the set of {@link dagger.hilt.android.earlyentrypoint.AggregatedEarlyEntryPoint}
   * dependencies.
   */
  Class<?>[] earlyEntryPointDeps() default {};
}
