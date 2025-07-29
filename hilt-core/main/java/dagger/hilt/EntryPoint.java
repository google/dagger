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
 * Annotation for marking an interface as an entry point into a generated component. This annotation
 * must be used with {@link dagger.hilt.InstallIn} to indicate which component(s) should have this
 * entry point. When assembling components, Hilt will make the indicated components extend the
 * interface marked with this annotation.
 *
 * <p>To use the annotated interface to access Dagger objects, use {@link dagger.hilt.EntryPoints}.
 *
 * <p>Example usage:
 *
 * <pre><code>
 *   {@literal @}EntryPoint
 *   {@literal @}InstallIn(SingletonComponent.class)
 *   public interface FooEntryPoint {
 *     Foo getFoo();
 *   }
 *
 *   Foo foo = EntryPoints.get(component, FooEntryPoint.class).getFoo();
 * </code></pre>
 *
 * @see <a href="https://dagger.dev/hilt/entry-points">Entry points</a>
 */
@Retention(CLASS)
@Target(ElementType.TYPE)
@GeneratesRootInput
public @interface EntryPoint {}
