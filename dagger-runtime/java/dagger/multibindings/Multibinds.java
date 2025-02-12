/*
 * Copyright (C) 2015 The Dagger Authors.
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
 * Annotates abstract module methods that declare multibindings.
 *
 * <p>You can declare that a multibound set or map is bound by annotating an abstract module method
 * that returns the set or map you want to declare with {@code @Multibinds}.
 *
 * <p>You do not have to use {@code @Multibinds} for sets or maps that have at least one
 * contribution, but you do have to declare them if they may be empty.
 *
 * <pre><code>
 *   {@literal @Module} abstract class MyModule {
 *     {@literal @Multibinds abstract Set<Foo> aSet();}
 *     {@literal @Multibinds abstract @MyQualifier Set<Foo> aQualifiedSet();}
 *     {@literal @Multibinds abstract Map<String, Foo> aMap();}
 *     {@literal @Multibinds abstract @MyQualifier Map<String, Foo> aQualifiedMap();}
 *
 *     {@literal @Provides}
 *     {@literal static Object usesMultibindings(Set<Foo> set, @MyQualifier Map<String, Foo> map}) {
 *       return …
 *     }
 *   }</code></pre>
 *
 * <p>A given set or map multibinding can be declared any number of times without error. Dagger
 * never implements or calls any {@code @Multibinds} methods.
 *
 * @see <a href="https://dagger.dev/multibindings">Multibindings</a>
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Multibinds {}
