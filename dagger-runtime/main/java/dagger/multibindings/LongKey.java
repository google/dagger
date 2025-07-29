/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.multibindings;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.MapKey;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A {@link MapKey} annotation for maps with {@code long} keys. */
@Documented
// While METHOD is the only valid target for Dagger, FIELD was added to support Hilt's
// @BindValueIntoMap and TYPE was added to support external extension types since it likely won't
// cause confusion/maintenance issues as this isn't part of Dagger's core API.
// See discussion on https://github.com/google/dagger/pull/2831#issuecomment-919417457 for details.
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RUNTIME)
@MapKey
public @interface LongKey {
  long value();
}
