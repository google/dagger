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

package dagger.hilt.internal.definecomponent;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An annotation used to aggregate {@link dagger.hilt.DefineComponent} types in a common location.
 *
 * <p>Note: The types are given using {@link String} rather than {@link Class} since the {@link
 * dagger.hilt.DefineComponent} type is not necessarily in the same package and not necessarily
 * public.
 */
@Retention(CLASS)
@Target(TYPE)
public @interface DefineComponentClasses {
  /**
   * Returns the fully qualified name of the {@link dagger.hilt.DefineComponent} type, or an empty
   * string if it wasn't given.
   */
  String component() default "";

  /**
   * Returns the fully qualified name of the {@link dagger.hilt.DefineComponent.Builder} type, or an
   * empty string if it wasn't given.
   */
  String builder() default "";
}
