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

package dagger.assisted;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates an abstract class or interface used to create an instance of a type via an {@link
 * AssistedInject} constructor.
 *
 * <p>An {@link AssistedFactory}-annotated type must obey the following constraints:
 *
 * <ul>
 *   <li>The type must be an abstract class or interface,
 *   <li>The type must contain exactly one abstract, non-default method whose
 *       <ul>
 *         <li>return type must exactly match the type of an assisted injection type, and
 *         <li>parameters must match the exact list of {@link Assisted} parameters in the assisted
 *             injection type's constructor (and in the same order).
 *       </ul>
 * </ul>
 *
 * See {@link AssistedInject}
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface AssistedFactory {}
