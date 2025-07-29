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

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * An annotation used to uninstall modules that have previously been installed with {@link
 * dagger.hilt.InstallIn}.
 *
 * <p>This feature should only be used in tests. It is useful for replacing production bindings with
 * fake bindings. The basic idea is to allow users to uninstall the module that provided the
 * production binding so that a fake binding can be provided by the test.
 *
 * <p>Example:
 *
 * <pre><code>
 *   {@literal @}HiltAndroidTest
 *   {@literal @}UninstallModules({
 *       ProdFooModule.class,
 *   })
 *   public class MyTest {
 *     {@literal @}Module
 *     {@literal @}InstallIn(SingletonComponent.class)
 *     interface FakeFooModule {
 *       {@literal @}Binds Foo bindFoo(FakeFoo fakeFoo);
 *     }
 *   }
 * </code></pre>
 */
@GeneratesRootInput
@Target({ElementType.TYPE})
public @interface UninstallModules {

  /**
   * Returns the list of classes to uninstall.
   *
   * <p>These classes must be annotated with both {@link dagger.Module} and {@link
   * dagger.hilt.InstallIn}.
   *
   * <p>Note:A module that is included as part of another module's {@link dagger.Module#includes()}
   * cannot be truly uninstalled until the including module is also uninstalled.
   */
  Class<?>[] value() default {};
}
