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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates <em>abstract</em> methods of a {@link Module} that delegate bindings. For example, to
 * bind {@link java.util.Random} to {@link java.security.SecureRandom} a module could declare the
 * following: {@code @Binds abstract Random bindRandom(SecureRandom secureRandom);}
 *
 * <p>{@code @Binds} methods are a drop-in replacement for {@link Provides} methods that simply
 * return an injected parameter. Prefer {@code @Binds} because the generated implementation is
 * likely to be more efficient.
 *
 * <p>A {@code @Binds} method:
 *
 * <ul>
 *   <li>Must be {@code abstract}.
 *   <li>May be {@linkplain javax.inject.Scope scoped}.
 *   <li>May be {@linkplain javax.inject.Qualifier qualified}.
 *   <li>Must have a single parameter whose type is assignable to the return type. The return type
 *       declares the bound type (just as it would for a {@literal @}{@link dagger.Provides} method)
 *       and the parameter is the type to which it is bound.
 *       <p>For {@linkplain dagger.multibindings multibindings}, assignability is checked in similar
 *       ways:
 *       <dl>
 *         <dt>{@link dagger.multibindings.IntoSet}
 *         <dd>The parameter must be assignable to the only parameter of {@link java.util.Set#add}
 *             when viewed as a member of the return type — the parameter must be assignable to the
 *             return type.
 *         <dt>{@link dagger.multibindings.ElementsIntoSet}
 *         <dd>The parameter must be assignable to the only parameter of {@link
 *             java.util.Set#addAll} when viewed as a member of the return type — if the return type
 *             is {@code Set<E>}, the parameter must be assignable to {@code Collection<? extends
 *             E>}.
 *         <dt>{@link dagger.multibindings.IntoMap}
 *         <dd>The parameter must be assignable to the {@code value} parameter of {@link
 *             java.util.Map#put} when viewed as a member of a {@link java.util.Map} in which {@code
 *             V} is bound to the return type — the parameter must be assignable to the return type
 *       </dl>
 * </ul>
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Binds {}
