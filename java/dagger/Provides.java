/*
 * Copyright (C) 2007 The Dagger Authors.
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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates methods of a {@linkplain Module module} to create a provider method binding. The
 * method's return type is bound to its returned value. The {@linkplain Component component}
 * implementation will pass dependencies to the method as parameters.
 *
 * <h3>Nullability</h3>
 *
 * <p>Dagger forbids injecting {@code null} by default. Component implemenations that invoke
 * {@code @Provides} methods that return {@code null} will throw a {@link NullPointerException}
 * immediately thereafter. {@code @Provides} methods may opt into allowing {@code null} by
 * annotating the method with any {@code @Nullable} annotation like
 * {@code javax.annotation.Nullable} or {@code android.support.annotation.Nullable}.
 *
 * <p>If a {@code @Provides} method is marked {@code @Nullable}, Dagger will <em>only</em>
 * allow injection into sites that are marked {@code @Nullable} as well. A component that
 * attempts to pair a {@code @Nullable} provision with a non-{@code @Nullable} injection site
 * will fail to compile.
 */
@Documented @Target(METHOD) @Retention(RUNTIME)
public @interface Provides {
}
