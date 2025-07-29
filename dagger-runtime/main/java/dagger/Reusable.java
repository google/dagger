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

package dagger;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import javax.inject.Scope;

/**
 * A scope that indicates that the object returned by a binding may be (but might not be) reused.
 *
 * <p>{@code @Reusable} is useful when you want to limit the number of provisions of a type, but
 * there is no specific lifetime over which there must be only one instance.
 *
 * @see <a href="https://dagger.dev/users-guide#reusable-scope">Reusable Scope</a>
 */
@Documented
@Beta
@Retention(RUNTIME)
@Scope
public @interface Reusable {}
