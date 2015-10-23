/*
 * Copyright (C) 2014 Google, Inc.
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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Identifies annotation types that are used to associate keys with values returned by
 * {@linkplain Provides provider methods} in order to compose a {@linkplain Provides.Type#MAP map}.
 *
 * <p>Every provider method annotated with {@code @Provides(type = MAP)} must also have an
 * annotation that identifies the key for that map entry. That annotation's type must be annotated
 * with {@code @MapKey}.
 *
 * <p>Typically, the key annotation has a single member, whose value is used as the map key.
 *
 * <p>For example, to add an entry to a {@code Map<String, Integer>} with key "foo", you could use
 * an annotation called {@code @StringKey}:
 *
 * <pre><code>
 * {@literal @}MapKey
 * {@literal @}interface StringKey {
 *   String value();
 * }
 *
 * {@literal @}Module
 * class SomeModule {
 *   {@literal @}Provides(type = MAP)
 *   {@literal @}StringKey("foo")
 *   Integer provideFooValue() {
 *     return 2;
 *   }
 * }
 *
 * class SomeInjectedType {
 *   {@literal @}Inject
 *   SomeInjectedType(Map<String, Integer> map) {
 *     assert map.get("foo") == 2;
 *   }
 * }
 * </code></pre>
 *
 * <p>If {@code unwrapValue} is true, the annotation's single member can be any type except an
 * array.
 *
 * <h2>Annotations as keys</h2>
 *
 * <p>If {@link #unwrapValue} is false, then the annotation itself is used as the map key. For
 * example, to add an entry to a {@code Map<MyMapKey, Integer>} map:
 *
 * <pre><code>
 * {@literal @}MapKey(unwrapValue = false)
 * {@literal @}interface MyMapKey {
 *   String someString();
 *   MyEnum someEnum();
 * }
 *
 * {@literal @}Module
 * class SomeModule {
 *   {@literal @}Provides(type = MAP)
 *   {@literal @}MyMapKey(someString = "foo", someEnum = BAR)
 *   Integer provideFooBarValue() {
 *     return 2;
 *   }
 * }
 *
 * class SomeInjectedType {
 *   {@literal @}Inject
 *   SomeInjectedType(Map<MyMapKey, Integer> map) {
 *     assert map.get(new MyMapKeyImpl("foo", MyEnum.BAR)) == 2;
 *   }
 * }
 * </code></pre>
 *
 * <p>(Note that there must be a class {@code MyMapKeyImpl} that implements {@code MyMapKey} in
 * order to call {@link Map#get(Object)} on the provided map.)
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
@Beta
public @interface MapKey {
  /**
   * True to use the value of the single member of the annotated annotation as the map key; false
   * to use the annotation instance as the map key.
   *
   * <p>If true, the single member must not be an array.
   */
  boolean unwrapValue() default true;
}
