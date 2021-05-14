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

package dagger.hilt.android;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An escape hatch for when a Hilt entry point usage needs to be called before the singleton
 * component is available in a Hilt test.
 *
 * <p>Warning: Please see documentation for more details:
 * https://dagger.dev/hilt/early-entry-point
 *
 * <p>Usage:
 *
 * <p>To enable an existing entry point to be called early in a Hilt test, replace its
 * {@link dagger.hilt.EntryPoint} annotation with {@link EarlyEntryPoint}. (Note that,
 * {@link EarlyEntryPoint} is only allowed on entry points installed in the
 * {@link dagger.hilt.components.SingletonComponent}).
 *
 * <pre><code>
 * @EarlyEntryPoint  // <- This replaces @EntryPoint
 * @InstallIn(SingletonComponent.class)
 * interface FooEntryPoint {
 *   Foo getFoo();
 * }
 * </code></pre>
 *
 * <p>Then, replace any of the corresponding usages of {@link dagger.hilt.EntryPoints} with
 * {@link EarlyEntryPoints}, as shown below:
 *
 * <pre><code>
 * // EarlyEntryPoints.get() must be used with entry points annotated with @EarlyEntryPoint
 * // This entry point can now be called at any point during a test, e.g. in Application.onCreate().
 * Foo foo = EarlyEntryPoints.get(appContext, FooEntryPoint.class).getFoo();
 * </code></pre>
 */
@Beta
@Retention(RUNTIME) // Needs to be runtime for checks in EntryPoints and EarlyEntryPoints.
@Target(ElementType.TYPE)
public @interface EarlyEntryPoint {}
