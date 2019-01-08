/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

/** The implementation of a component creator type. */
@AutoValue
abstract class ComponentCreatorImplementation {

  /** Creates a new {@link ComponentCreatorImplementation}. */
  static ComponentCreatorImplementation create(
      TypeSpec componentCreatorClass,
      ClassName name,
      ImmutableMap<ComponentRequirement, String> requirementNames) {
    return new AutoValue_ComponentCreatorImplementation(
        componentCreatorClass, name, requirementNames);
  }

  /** The type for the creator implementation. */
  abstract TypeSpec componentCreatorClass();

  /** The name of the creator implementation class. */
  abstract ClassName name();

  /**
   * The names to use for fields or parameters for the requirements this creator implementation
   * provides.
   */
  abstract ImmutableMap<ComponentRequirement, String> requirementNames();
}
