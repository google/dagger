/*
 * Copyright (C) 2022 The Dagger Authors.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates module with a bind method when there's only one implementation for an interface.
 * 
 * Example usage:
 * 
 * <pre><code>
 * @InstallBinding(SingletonComponent::class)
 * class AuthenticatorImpl @Inject constructor(): Authenticator
 * </code></pre>
 * 
 * the above example is equivalent to:
 * 
 * <pre><code>
 * @Module
 * @InstallIn(SingletonComponent::class)
 * interface BindAuthModule {
 *   @Binds
 *   fun bind(impl: AuthenticatorImpl): Authenticator
 * }
 * </code></pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@GeneratesRootInput
public @interface InstallBinding {
  Class<?> component();

  Class<?> boundType() default Object.class;
}
