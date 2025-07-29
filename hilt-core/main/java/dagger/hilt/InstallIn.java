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


package dagger.hilt;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation that declares which component(s) the annotated class should be included in when
 * Hilt generates the components. This may only be used with classes annotated with
 * {@literal @}{@link dagger.Module} or {@literal @}{@link dagger.hilt.EntryPoint}.
 *
 * <p>Example usage for installing a module in the generated {@code ApplicationComponent}:
 *
 * <pre><code>
 *   {@literal @}Module
 *   {@literal @}InstallIn(SingletonComponent.class)
 *   public final class FooModule {
 *     {@literal @}Provides
 *     static Foo provideFoo() {
 *       return new Foo();
 *     }
 *   }
 * </code></pre>
 *
 * @see <a href="https://dagger.dev/hilt/modules">Hilt Modules</a>
 */
@Retention(CLASS)
@Target({ElementType.TYPE})
@GeneratesRootInput
public @interface InstallIn {
  Class<?>[] value();
}
