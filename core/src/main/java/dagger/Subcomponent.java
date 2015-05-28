/*
 * Copyright (C) 2015 Google, Inc.
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
package dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * A subcomponent that inherits the bindings from a parent {@link Component} or
 * {@link Subcomponent}. The details of how to associate a subcomponent with a parent are described
 * in the documentation for {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@Target(TYPE)
@Documented
public @interface Subcomponent {
  /**
   * A list of classes annotated with {@link Module} whose bindings are used to generate the
   * subcomponent implementation.  Note that through the use of {@link Module#includes} the full set
   * of modules used to implement the subcomponent may include more modules that just those listed
   * here.
   */
  Class<?>[] modules() default {};
}
