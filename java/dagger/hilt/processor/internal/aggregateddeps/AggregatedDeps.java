/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.aggregateddeps;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;

// TODO(bcorso): Change this API to clearly represent that each AggeregatedDeps should only contain
// a single module, entry point, or component entry point.
/** Annotation for propagating dependency information through javac runs. */
@Retention(CLASS)
public @interface AggregatedDeps {
  /** Returns the components that this dependency will be installed in. */
  String[] components();

  /** Returns the test this dependency is associated with, otherwise an empty string. */
  String test() default "";

  /** Returns the deps that this dep replaces. */
  String[] replaces() default {};

  String[] modules() default {};

  String[] entryPoints() default {};

  String[] componentEntryPoints() default {};
}
