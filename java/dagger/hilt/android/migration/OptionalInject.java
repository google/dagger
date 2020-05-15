/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.migration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * When placed on an {@link dagger.hilt.android.AndroidEntryPoint}-annotated activity / fragment /
 * view / etc, allows injection to occur optionally based on whether or not the application is using
 * Hilt. This may be useful for libraries that have to support Hilt users as well as non-Hilt users.
 *
 * <p>Usage of this annotation will also cause a method {@code wasInjectedByHilt} to be generated in
 * the Hilt base class as well, that returns a boolean for whether or not injection actually
 * happened. Injection will happen if the parent type (e.g. the activity to a fragment) is an {@link
 * dagger.hilt.android.AndroidEntryPoint} annotated class and if that parent was also injected via
 * Hilt.
 */
// TODO(user): Update documentation to mention static utility method whenever we make that.
@Target(ElementType.TYPE)
public @interface OptionalInject {}
