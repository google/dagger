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

package dagger.hilt.testing;

import static java.lang.annotation.RetentionPolicy.CLASS;

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation that replaces one or more {@link dagger.hilt.InstallIn} modules with the annotated
 * module in tests.
 *
 * <p>The annotated class must also be annotated with {@link dagger.Module}.
 *
 * <p>Example:
 *
 * <pre><code>
 *   // Replaces FooModule with FakeFooModule, and installs it into the same component as FooModule.
 *   {@literal @}Module
 *   {@literal @}TestInstallIn(components = SingletonComponent.class, replaces = FooModule.class)
 *   public final class FakeFooModule {
 *     {@literal @}Provides
 *     static Foo provideFoo() {
 *       return new FakeFoo();
 *     }
 *   }
 * </code></pre>
 *
 * @see <a href="https://dagger.dev/hilt/modules">Hilt Modules</a>
 */
@Retention(CLASS)
@Target({ElementType.TYPE})
@GeneratesRootInput
public @interface TestInstallIn {
  /** Returns the component(s) into which the annotated module will be installed. */
  Class<?>[] components();

  /** Returns the {@link InstallIn} module(s) that the annotated class will replace in tests. */
  Class<?>[] replaces();
}
