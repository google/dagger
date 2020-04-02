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

package dagger.hilt.android.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * An annotation used to ignore modules in tests.
 *
 * <p>This feature is useful for replacing production bindings with fake bindings in tests. The
 * basic idea is to ignore the module that provided the production binding and include your own
 * module in your test.
 *
 * <p>Example:
 *
 * <pre><code>
 *   {@literal @}IgnoreModules({
 *       ProdFooModule.class,
 *   })
 *   {@literal @}GenerateComponents
 *   {@literal @}AndroidRobolectricEntryPoint
 *   public class MyTest {
 *     {@literal @}Module
 *     {@literal @}InstallIn(ApplicationComponent.class)
 *     interface FakeFooModule {
 *       {@literal @}Binds Foo bindFoo(FakeFoo fakeFoo);
 *     }
 *   }
 * </code></pre>
 */
@Target({ElementType.TYPE})
public @interface IgnoreModules {

  /**
   * Returns the list of classes to ignore.
   *
   * <p>These classes must be annotated with both {@link dagger.Module} and {@link
   * dagger.hilt.InstallIn}.
   *
   * <p>Note:A module that is included as part of another module's {@link dagger.Module#includes()}
   * cannot be truly uninstalled until the including module is also uninstalled.
   */
  Class<?>[] value() default {};
}
