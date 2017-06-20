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

package dagger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Qualifier;

/**
 * Annotates methods that declare bindings for {@code Optional} containers of values from bindings
 * that may or may not be present in the component.
 *
 * <p>If a module contains a method declaration like this:
 *
 * <pre>
 * {@literal @BindsOptionalOf} abstract Foo optionalFoo();</pre>
 *
 * then any binding in the component can depend on an {@code Optional} of {@code Foo}. If there is
 * no binding for {@code Foo} in the component, the {@code Optional} will be absent. If there is a
 * binding for {@code Foo} in the component, the {@code Optional} will be present, and its value
 * will be the value given by the binding for {@code Foo}.
 *
 * <p>A {@code @BindsOptionalOf} method:
 *
 * <ul>
 * <li>must be {@code abstract}
 * <li>may have a {@linkplain Qualifier qualifier} annotation
 * <li>must not return {@code void}
 * <li>must not have parameters
 * <li>must not throw exceptions
 * <li>must not return an unqualified type with an {@link Inject @Inject}-annotated constructor,
 *     since such a type is always present
 * </ul>
 *
 * <p>Other bindings may inject any of:
 *
 * <ul>
 * <li>{@code Optional<Foo>}
 * <li>{@code Optional<Provider<Foo>>}
 * <li>{@code Optional<Lazy<Foo>>}
 * <li>{@code Optional<Provider<Lazy<Foo>>>}
 * </ul>
 *
 * <p>Explicit bindings for any of the above will conflict with a {@code @BindsOptionalOf} binding.
 *
 * <p>If the binding for {@code Foo} is a {@code @Produces} binding, then another {@code @Produces}
 * binding can depend on any of:
 *
 * <ul>
 * <li>{@code Optional<Foo>}
 * <li>{@code Optional<Producer<Foo>>}
 * <li>{@code Optional<Produced<Foo>>}
 * </ul>
 *
 * <p>You can inject either {@code com.google.common.base.Optional} or {@code java.util.Optional}.
 */
@Documented
@Beta
@Retention(RUNTIME)
@Target(METHOD)
public @interface BindsOptionalOf {}
