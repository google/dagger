/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import dagger.BindsInstance;
import dagger.model.DependencyRequest;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * A descriptor for a component <i>creator</i> type: that is, a type annotated with
 * {@code @Component.Builder} (or one of the corresponding production or subcomponent versions).
 */
@AutoValue
abstract class ComponentCreatorDescriptor {

  /** The annotated creator type. */
  abstract TypeElement typeElement();

  /** The method that creates and returns a component instance. */
  abstract ExecutableElement factoryMethod();

  /**
   * Multimap of component requirements to the element that sets that requirements.
   *
   * <p>Validation must ensure that no more than one element exists that sets a given requirement.
   */
  abstract ImmutableSetMultimap<ComponentRequirement, ExecutableElement> requirementElements();

  /** Returns the set of component requirements for this creator. */
  final ImmutableSet<ComponentRequirement> requirements() {
    return requirementElements().keySet();
  }

  /** Returns the set of requirements for modules and component dependencies for this creator. */
  final ImmutableSet<ComponentRequirement> moduleAndDependencyRequirements() {
    return requirements().stream()
        .filter(requirement -> !requirement.isBoundInstance())
        .collect(toImmutableSet());
  }

  /** Returns the set of bound instance requirements for this creator. */
  final ImmutableSet<ComponentRequirement> boundInstanceRequirements() {
    return requirements().stream()
        .filter(requirement -> requirement.isBoundInstance())
        .collect(toImmutableSet());
  }

  /** Returns the element in this creator that sets the given {@code requirement}. */
  final ExecutableElement elementForRequirement(ComponentRequirement requirement) {
    return getOnlyElement(requirementElements().get(requirement));
  }

  /** Creates a new {@link ComponentCreatorDescriptor} for the given creator {@code type}. */
  static ComponentCreatorDescriptor create(
      DeclaredType type,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory) {
    TypeElement typeElement = asTypeElement(type);
    TypeMirror componentType = typeElement.getEnclosingElement().asType();

    ImmutableSetMultimap.Builder<ComponentRequirement, ExecutableElement> requirementElements =
        ImmutableSetMultimap.builder();

    ExecutableElement factoryMethod = null;
    for (ExecutableElement method : elements.getUnimplementedMethods(typeElement)) {
      ExecutableType resolvedMethodType = MoreTypes.asExecutable(types.asMemberOf(type, method));

      if (types.isSubtype(componentType, resolvedMethodType.getReturnType())) {
        factoryMethod = method;
      } else {
        VariableElement parameter = getOnlyElement(method.getParameters());
        TypeMirror parameterType = getOnlyElement(resolvedMethodType.getParameterTypes());
        requirementElements.put(
            requirement(method, parameter, parameterType, dependencyRequestFactory), method);
      }
    }
    verify(factoryMethod != null); // validation should have ensured this.

    return new AutoValue_ComponentCreatorDescriptor(
        typeElement, factoryMethod, requirementElements.build());
  }

  private static ComponentRequirement requirement(
      ExecutableElement method,
      VariableElement parameter,
      TypeMirror type,
      DependencyRequestFactory dependencyRequestFactory) {
    if (isAnnotationPresent(method, BindsInstance.class)) {
      DependencyRequest request =
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, type);
      return ComponentRequirement.forBoundInstance(
          request.key(), request.isNullable(), method.getSimpleName().toString());
    }

    return ConfigurationAnnotations.getModuleAnnotation(asTypeElement(type)).isPresent()
        ? ComponentRequirement.forModule(type)
        : ComponentRequirement.forDependency(type);
  }
}
