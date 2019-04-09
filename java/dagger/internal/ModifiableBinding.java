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

/** Annotates methods that implement bindings that may be modified by subclass implementations. */
@Target(METHOD)
public @interface ModifiableBinding {
  /** {@code ModifiableBindingType} of the binding. */
  // TODO(ronshapiro): should this be a shared enum with dagger.internal.codegen?
  String modifiableBindingType();

  /** A {@link dagger.internal.codegen.serialization.BindingRequestProto} serialized in Base64. */
  String bindingRequest();

  /**
   * For a multibinding, the keys of all contributions it depends on in this implementation.
   */
  String[] multibindingContributions() default {};
}
