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

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Annotates interfaces that declare multibindings.
 *
 * <p>You can declare that a multibound set or map is bound by nesting a
 * {@code @Multibindings}-annotated interface within a module, with methods that return the sets or
 * maps you want to declare.
 *
 * <p>You do not have to use {@code @Multibindings} for sets or maps that have at least one
 * contribution, but you do have to declare them if they may be empty.
 *
 * <pre><code>
 * {@literal @Module}
 * class MyModule {
 *   {@literal @Multibindings}
 *   interface MyMultibindings {
 *     {@literal Set<Foo>} aSet();
 *     {@literal @MyQualifier Set<Foo>} aQualifiedSet();
 *     {@literal Map<String, Foo>} aMap();
 *     {@literal @MyQualifier Map<String, Foo>} aQualifiedMap();
 *   }
 *
 *   {@literal @Provides}
 *   static Object usesMultibindings(
 *       {@literal Set<Foo>} set, {@literal @MyQualifier Map<String, Foo>} map) {
 *     return …
 *   }
 * }
 * </code></pre>
 *
 * <p>All methods on the interface and any supertypes (except for methods on {@link Object}) are
 * used to declare multibindings. The names of the interface and its methods are ignored. A given
 * set or map multibinding can be declared any number of times without error. Dagger never
 * implements the interface or calls any of its methods.
 *
 * @see <a href="http://google.github.io/dagger/multibindings">Multibindings</a>
 */
@Documented
@Target(TYPE)
@Beta
public @interface Multibindings {}
