/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import dagger.model.Key;

/**
 * The framework class and binding key for a resolved dependency of a binding. If a binding has
 * several dependencies for a key, then only one instance of this class will represent them all.
 *
 * <p>In the following example, the binding {@code provideFoo()} has two dependency requests:
 *
 * <ol>
 *   <li>{@code Bar bar}
 *   <li>{@code Provider<Bar> barProvider}
 * </ol>
 *
 * But they both can be satisfied with the same instance of {@code Provider<Bar>}. So one instance
 * of {@code FrameworkDependency} will be used for both. Its {@link #key()} will be for {@code Bar},
 * and its {@link #frameworkType()} will be {@link FrameworkType#PROVIDER}.
 *
 * <pre><code>
 *   {@literal @Provides} static Foo provideFoo(Bar bar, {@literal Provider<Bar>} barProvider) {
 *     return new Foo(…);
 *   }
 * </code></pre>
 */
@AutoValue
abstract class FrameworkDependency {

  /** The fully-resolved key shared by all the dependency requests. */
  abstract Key key();

  /** The type of the framework dependency. */
  abstract FrameworkType frameworkType();

  /** The framework class to use for this dependency. */
  final Class<?> frameworkClass() {
    return frameworkType().frameworkClass();
  }

  /** Returns a new instance with the given key and type. */
  static FrameworkDependency create(Key key, FrameworkType frameworkType) {
    return new AutoValue_FrameworkDependency(key, frameworkType);
  }
}
