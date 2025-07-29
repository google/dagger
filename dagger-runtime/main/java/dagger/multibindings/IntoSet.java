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

package dagger.multibindings;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The method's return type forms the generic type argument of a {@code Set<T>}, and the returned
 * value is contributed to the set. The object graph will pass dependencies to the method as
 * parameters. The {@code Set<T>} produced from the accumulation of values will be immutable.
 *
 * @see <a href="https://dagger.dev/multibindings#set-multibindings">Set multibinding</a>
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface IntoSet {}
