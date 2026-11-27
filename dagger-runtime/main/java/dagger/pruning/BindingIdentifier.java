/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.pruning;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Assigns an explicit identifier class to a binding so it can be referenced by {@link OnlyUsedBy}.
 *
 * <p>Apply this annotation to a {@code @Provides} method, {@code @Binds} method, or {@code @Inject}
 * constructor. For {@code @Inject} constructors without this annotation, the bound class itself is
 * used as the default identifier.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, CONSTRUCTOR})
public @interface BindingIdentifier {
  /** The identifier class representing this binding for {@link OnlyUsedBy}. */
  Class<?> value();
}
