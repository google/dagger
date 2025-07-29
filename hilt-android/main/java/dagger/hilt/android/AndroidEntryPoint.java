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

package dagger.hilt.android;

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Marks an Android component class to be setup for injection with the standard Hilt Dagger Android
 * components. Currently, this supports activities, fragments, views, services, and broadcast
 * receivers.
 *
 * <p>This annotation will generate a base class that the annotated class should extend, either
 * directly or via the Hilt Gradle Plugin. This base class will take care of injecting members into
 * the Android class as well as handling instantiating the proper Hilt components at the right point
 * in the lifecycle. The name of the base class will be "Hilt_<annotated class name>".
 *
 * <p>Example usage (with the Hilt Gradle Plugin):
 *
 * <pre><code>
 *   {@literal @}AndroidEntryPoint
 *   public final class FooActivity extends FragmentActivity {
 *     {@literal @}Inject Foo foo;
 *
 *     {@literal @}Override
 *     public void onCreate(Bundle savedInstanceState) {
 *       super.onCreate(savedInstanceState);  // The foo field is injected in super.onCreate()
 *     }
 *   }
 * </code></pre>
 *
 * <p>Example usage (without the Hilt Gradle Plugin):
 *
 * <pre><code>
 *   {@literal @}AndroidEntryPoint(FragmentActivity.class)
 *   public final class FooActivity extends Hilt_FooActivity {
 *     {@literal @}Inject Foo foo;
 *
 *     {@literal @}Override
 *     public void onCreate(Bundle savedInstanceState) {
 *       super.onCreate(savedInstanceState);  // The foo field is injected in super.onCreate()
 *     }
 *   }
 * </code></pre>
 *
 * @see HiltAndroidApp
 */
@Target({ElementType.TYPE})
@GeneratesRootInput
public @interface AndroidEntryPoint {

  /**
   * The base class for the generated Hilt class. When applying the Hilt Gradle Plugin this value
   * is not necessary and will be inferred from the current superclass.
   */
  Class<?> value() default Void.class;
}
