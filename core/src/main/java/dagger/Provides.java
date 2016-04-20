/*
 * Copyright (C) 2007 Google Inc.
 * Copyright (C) 2012 Square, Inc.
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

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
 *
 * @author Bob Lee
 */
@Documented @Target(METHOD) @Retention(RUNTIME)
public @interface Provides {
  /** The type of binding into which the return type of the annotated method contributes. */
  enum Type {
    /**
     * The method is the only one which can produce the value for the specified return type. This
     * is the default behavior.
     */
    UNIQUE,

    /**
     * The method's return type forms the generic type argument of a {@code Set<T>}, and the
     * returned value is contributed to the set. The object graph will pass dependencies to the
     * method as parameters. The {@code Set<T>} produced from the accumulation of values will be
     * immutable.
     *
     */
    SET,

    /**
     * Like {@link #SET}, except the method's return type is {@code Set<T>}, where any values are
     * contributed to the set. An example use is to provide a default empty set binding, which is
     * otherwise not possible using {@link #SET}.
     *
     */
    SET_VALUES,

    /**
     * The method's return type forms the type argument for the value of a
     * {@code Map<K, Provider<V>>}, and the combination of the annotated key and the returned value
     * is contributed to the map as a key/value pair. The {@code Map<K, Provider<V>>} produced from
     * the accumulation of values will be immutable.
     *
     */
    @Beta
    MAP;
  }

  Type type() default Type.UNIQUE;
}
