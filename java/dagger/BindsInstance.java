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
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method on a {@linkplain Component.Builder component builder} or a parameter on a
 * {@linkplain Component.Factory component factory} as binding an instance to some key within the
 * component.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @Component.Builder}
 *   interface Builder {
 *     {@literal @BindsInstance} Builder foo(Foo foo);
 *     {@literal @BindsInstance} Builder bar({@literal @Blue} Bar bar);
 *     ...
 *   }
 *
 *   // or
 *
 *   {@literal @Component.Factory}
 *   interface Factory {
 *     MyComponent newMyComponent(
 *         {@literal @BindsInstance} Foo foo,
 *         {@literal @BindsInstance @Blue} Bar bar);
 *   }
 * </pre>
 *
 * <p>will allow clients of the builder or factory to pass their own instances of {@code Foo} and
 * {@code Bar}, and those instances can be injected within the component as {@code Foo} or
 * {@code @Blue Bar}, respectively.
 *
 * <p>{@code @BindsInstance} arguments may not be {@code null} unless the parameter is annotated
 * with {@code @Nullable}.
 *
 * <p>For builders, {@code @BindsInstance} methods must be called before building the component,
 * unless their parameter is marked {@code @Nullable}, in which case the component will act as
 * though it was called with a {@code null} argument. Primitives, of course, may not be marked
 * {@code @Nullable}.
 *
 * <p>Binding an instance is equivalent to passing an instance to a module constructor and providing
 * that instance, but is often more efficient. When possible, binding object instances should be
 * preferred to using module instances.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
@Beta
public @interface BindsInstance {}
