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

/**
 * Marks a method on a component builder or subcomponent builder that allows an instance to be bound
 * to some type within the component.
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
 * </pre>
 *
 * <p>will allow clients of this builder to pass their own instances of {@code Foo} and {@code Bar},
 * and those instances can be injected within the component as {@code Foo} or {@code @Blue Bar},
 * respectively.
 *
 * <p>{@code @BindsInstance} methods may not be passed null arguments unless the parameter is
 * annotated with {@code @Nullable}; in that case, both null and non-null arguments may be passed to
 * the method.
 *
 * <p>{@code @BindsInstance} methods must be called before building the component, unless their
 * parameter is marked {@code @Nullable}, in which case the component will act as though it was
 * called with a null argument. Primitives, of course, may not be marked {@code @Nullable}.
 *
 * <p>Binding an instance is equivalent to passing an instance to a module constructor and providing
 * that instance, but is often more efficient. When possible, binding object instances should be
 * preferred to using module instances.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
@Beta
public @interface BindsInstance {}
