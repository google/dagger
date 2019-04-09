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

package dagger.internal;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;

/**
 * Annotates a {@code configureInitialization()} method with {@code ComponentRequirement}s that it
 * accepts as parameters.
 */
@Target(METHOD)
public @interface ConfigureInitializationParameters {
  /**
   * The list of parameters.
   *
   * Each value is a {@link dagger.internal.codegen.serialization.ComponentRequirementProto}
   * serialized in Base64.
   */
  String[] value() default {};
}
