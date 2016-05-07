/*
 * Copyright (C) 2016 Google, Inc.
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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotates <em>abstract</em> methods of a {@link Module} that delegate bindings. For example, to
 * bind {@link java.util.Random} to {@link java.security.SecureRandom} a module could declare the
 * following: {@code @Binds abstract Random bindRandom(SecureRandom secureRandom);}
 *
 * <p>{@code @Binds} methods are a drop-in replacement for {@link Provides} methods that simply
 * return an injected parameter.  Prefer {@code @Binds} because the generated implementation is
 * likely to be more efficient.
 *
 * <p>A {@code @Binds} method:
 * <ul>
 * <li>Must be {@code abstract}.
 * <li>Must have a single parameter whose type is assignable to the return type.  The return type is
 * the bound type and the parameter is the type to which it is bound.
 * <li>May be {@linkplain javax.inject.Scope scoped}.
 * <li>May be {@linkplain javax.inject.Qualifier qualified}.
 * </ul>
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Binds {}
