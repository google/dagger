/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Stores the qualifier information about a type after it's been processed. */
@Retention(CLASS)
@Target(TYPE)
public @interface QualifierMetadata {
  /**
   * Returns the list of fully qualified qualifier names used in a particular context.
   *
   * <p>For example, when annotating Dagger's generated {@code _Factory} class for an inject
   * constructor, it contains all qualifiers used on parameters within the constructor. When
   * annotating Dagger's generated {@code _MembersInjector} class for inject fields and methods, it
   * contains all qualifiers found on the fields and method parameters. When annotating Dagger's
   * generated {@code _Factory} class for provision methods it includes all qualifiers used on the
   * provision method and its parameters.
   */
  String[] value() default {};
}
