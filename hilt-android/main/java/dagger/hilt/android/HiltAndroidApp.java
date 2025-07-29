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

package dagger.hilt.android;

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking the {@link android.app.Application} class where the Dagger components
 * should be generated. Since all components will be built in the same compilation as the annotated
 * application, all modules and entry points that should be installed in the component need to be
 * transitive compilation dependencies of the annotated application.
 *
 * <p>Usage of this annotation is similar to {@link dagger.hilt.android.AndroidEntryPoint} with the
 * only difference being that it only works on application classes and additionally triggers Dagger
 * component generation.
 *
 * <p>This annotation will generate a base class that the annotated class should extend, either
 * directly or via the Hilt Gradle Plugin. This base class will take care of injecting members into
 * the Android class as well as handling instantiating the proper Hilt components at the right point
 * in the lifecycle. The name of the base class will be "Hilt_<annotated class name>".
 *
 * <p>Example usage (with the Hilt Gradle Plugin):
 *
 * <pre><code>
 *   {@literal @}HiltAndroidApp
 *   public final class FooApplication extends Application {
 *     {@literal @}Inject Foo foo;
 *
 *     {@literal @}Override
 *     public void onCreate() {
 *       super.onCreate();  // The foo field is injected in super.onCreate()
 *     }
 *   }
 * </code></pre>
 *
 * <p>Example usage (without the Hilt Gradle Plugin):
 *
 * <pre><code>
 *   {@literal @}HiltAndroidApp(Application.class)
 *   public final class FooApplication extends Hilt_FooApplication {
 *     {@literal @}Inject Foo foo;
 *
 *     {@literal @}Override
 *     public void onCreate() {
 *       super.onCreate();  // The foo field is injected in super.onCreate()
 *     }
 *   }
 * </code></pre>
 *
 * @see AndroidEntryPoint
 */
// Set the retention to RUNTIME because we check it via reflection in the HiltAndroidRule.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@GeneratesRootInput
public @interface HiltAndroidApp {
  /**
   * The base class for the generated Hilt application. When applying the Hilt Gradle Plugin this
   * value is not necessary and will be inferred from the current superclass.
   */
  // TODO(erichang): It would be nice to make this Class<? extends Application> but then the default
  // would have to be Application which would make the default actually valid even without the
  // plugin. Maybe that is a good thing...but might be better to have users be explicit about the
  // base class they want.
  Class<?> value() default Void.class;
}
