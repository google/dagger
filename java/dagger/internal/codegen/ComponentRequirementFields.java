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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import java.util.HashMap;
import java.util.Map;

/**
 * A central repository of fields used to access any {@link ComponentRequirement} available to a
 * component.
 */
final class ComponentRequirementFields {

  // TODO(dpb,ronshapiro): refactor this and ComponentBindingExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make ComponentRequirementField.Factory create it.

  /**
   * A list of component requirement field maps. The first element contains the fields on this
   * component; the second contains the fields owned by its parent; and so on.
   */
  private final ImmutableList<Map<ComponentRequirement, ComponentRequirementField>>
      componentRequirementFieldsMaps;

  private ComponentRequirementFields(
      ImmutableList<Map<ComponentRequirement, ComponentRequirementField>>
          componentRequirementFieldsMaps) {
    this.componentRequirementFieldsMaps = componentRequirementFieldsMaps;
  }

  ComponentRequirementFields() {
    this(ImmutableList.of(newComponentRequirementsMap()));
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used when implementing a
   * component method. This may add a field to the component in order to reference the component
   * requirement outside of the {@code initialize()} methods.
   */
  CodeBlock getExpression(ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getField(componentRequirement).getExpression(requestingClass);
  }

  /**
   * Returns an expression for the {@code componentRequirement} to be used only within {@code
   * initialize()} methods, where the component builder is available.
   *
   * <p>When accessing this field from a subcomponent, this may cause a field to be initialized in
   * the component that owns this {@link ComponentRequirement}.
   */
  CodeBlock getExpressionDuringInitialization(
      ComponentRequirement componentRequirement, ClassName requestingClass) {
    return getField(componentRequirement).getExpressionDuringInitialization(requestingClass);
  }

  private ComponentRequirementField getField(ComponentRequirement componentRequirement) {
    for (Map<ComponentRequirement, ComponentRequirementField> componentRequirementFieldsMap :
        componentRequirementFieldsMaps) {
      ComponentRequirementField field = componentRequirementFieldsMap.get(componentRequirement);
      if (field != null) {
        return field;
      }
    }
    throw new IllegalStateException(
        "no component requirement field found for " + componentRequirement);
  }

  /**
   * Adds a component requirement field for a single component requirement owned by this component.
   */
  void add(ComponentRequirementField field) {
    componentRequirementFieldsMaps.get(0).put(field.componentRequirement(), field);
  }

  /**
   * Returns {@code true} if the component that owns this {@link ComponentRequirementFields} has a
   * registered {@link ComponentRequirementField} for {@code componentRequirement}.
   */
  boolean contains(ComponentRequirement componentRequirement) {
    return componentRequirementFieldsMaps
        .stream()
        .anyMatch(map -> map.containsKey(componentRequirement));
  }

  private static Map<ComponentRequirement, ComponentRequirementField>
      newComponentRequirementsMap() {
    return new HashMap<>();
  }

  /**
   * Returns a new object representing the fields available from a child component of this one.
   */
  ComponentRequirementFields forChildComponent() {
    return new ComponentRequirementFields(
        FluentIterable.of(newComponentRequirementsMap())
            .append(componentRequirementFieldsMaps)
            .toList());
  }
}
