/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.migration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * When used on a {@link dagger.hilt.android.HiltAndroidApp}-annotated application, this causes the
 * application to no longer inject itself in onCreate and instead allows it to be injected at some
 * other time.
 *
 * <p>When using this annotation, you can use {@link CustomInjection#inject} to inject the
 * application class. Additionally, this annotation will also cause a method, {@code customInject}
 * to be generated in the Hilt base class as well, that behaves the same as
 * {@link CustomInjection#inject}. The method is available to users that extend the Hilt base class
 * directly and don't use the Gradle plugin.
 *
 * <p> Example usage:
 *
 * <pre><code>
 * {@literal @}CustomInject
 * {@literal @}HiltAndroidApp(Application.class)
 * public final class MyApplication extends Hilt_MyApplication {
 *
 *   {@literal @}Inject Foo foo;
 *
 *   {@literal @}Override
 *   public void onCreate() {
 *     // Injection would normally happen in this super.onCreate() call, but won't now because this
 *     // is using CustomInject.
 *     super.onCreate();
 *     doSomethingBeforeInjection();
 *     // This call now injects the fields in the Application, like the foo field above.
 *     CustomInject.inject(this);
 *   }
 * }
 * </code></pre>
 */
@Target(ElementType.TYPE)
public @interface CustomInject {}
