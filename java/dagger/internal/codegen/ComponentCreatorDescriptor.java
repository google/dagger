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
import static dagger.internal.codegen.ComponentCreatorAnnotation.getCreatorAnnotations;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.ModuleAnnotation.moduleAnnotation;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import dagger.BindsInstance;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import java.util.List;
import javax.lang.model.element.Element;
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

  /** Returns the annotation marking this creator. */
  abstract ComponentCreatorAnnotation annotation();

  /** The kind of this creator. */
  final ComponentCreatorKind kind() {
    return annotation().creatorKind();
  }

  /** The annotated creator type. */
  abstract TypeElement typeElement();

  /** The method that creates and returns a component instance. */
  abstract ExecutableElement factoryMethod();

  /**
   * Multimap of component requirements to setter methods that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  abstract ImmutableSetMultimap<ComponentRequirement, ExecutableElement> unvalidatedSetterMethods();

  /**
   * Multimap of component requirements to factory method parameters that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  abstract ImmutableSetMultimap<ComponentRequirement, VariableElement>
      unvalidatedFactoryParameters();

  /**
   * Multimap of component requirements to elements (methods or parameters) that set that
   * requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  final ImmutableSetMultimap<ComponentRequirement, Element> unvalidatedRequirementElements() {
    // ComponentCreatorValidator ensures that there are either setter methods or factory method
    // parameters, but not both, so we can cheat a little here since we know that only one of
    // the two multimaps will be non-empty.
    return ImmutableSetMultimap.copyOf( // no actual copy
        unvalidatedSetterMethods().isEmpty()
            ? unvalidatedFactoryParameters()
            : unvalidatedSetterMethods());
  }

  /**
   * Map of component requirements to elements (setter methods or factory method parameters) that
   * set them.
   */
  @Memoized
  ImmutableMap<ComponentRequirement, Element> requirementElements() {
    return flatten(unvalidatedRequirementElements());
  }

  /** Map of component requirements to setter methods for those requirements. */
  @Memoized
  ImmutableMap<ComponentRequirement, ExecutableElement> setterMethods() {
    return flatten(unvalidatedSetterMethods());
  }

  /** Map of component requirements to factory method parameters for those requirements. */
  @Memoized
  ImmutableMap<ComponentRequirement, VariableElement> factoryParameters() {
    return flatten(unvalidatedFactoryParameters());
  }

  private static <K, V> ImmutableMap<K, V> flatten(Multimap<K, V> multimap) {
    return ImmutableMap.copyOf(
        Maps.transformValues(multimap.asMap(), values -> getOnlyElement(values)));
  }

  /** Returns the set of component requirements this creator allows the user to set. */
  final ImmutableSet<ComponentRequirement> userSettableRequirements() {
    // Note: they should have been validated at the point this is used, so this set is valid.
    return unvalidatedRequirementElements().keySet();
  }

  /** Returns the set of requirements for modules and component dependencies for this creator. */
  final ImmutableSet<ComponentRequirement> moduleAndDependencyRequirements() {
    return userSettableRequirements().stream()
        .filter(requirement -> !requirement.isBoundInstance())
        .collect(toImmutableSet());
  }

  /** Returns the set of bound instance requirements for this creator. */
  final ImmutableSet<ComponentRequirement> boundInstanceRequirements() {
    return userSettableRequirements().stream()
        .filter(ComponentRequirement::isBoundInstance)
        .collect(toImmutableSet());
  }

  /** Returns the element in this creator that sets the given {@code requirement}. */
  final Element elementForRequirement(ComponentRequirement requirement) {
    return requirementElements().get(requirement);
  }

  /** Creates a new {@link ComponentCreatorDescriptor} for the given creator {@code type}. */
  static ComponentCreatorDescriptor create(
      DeclaredType type,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory) {
    TypeElement typeElement = asTypeElement(type);
    TypeMirror componentType = typeElement.getEnclosingElement().asType();

    ImmutableSetMultimap.Builder<ComponentRequirement, ExecutableElement> setterMethods =
        ImmutableSetMultimap.builder();

    ExecutableElement factoryMethod = null;
    for (ExecutableElement method : elements.getUnimplementedMethods(typeElement)) {
      ExecutableType resolvedMethodType = MoreTypes.asExecutable(types.asMemberOf(type, method));

      if (types.isSubtype(componentType, resolvedMethodType.getReturnType())) {
        factoryMethod = method;
      } else {
        VariableElement parameter = getOnlyElement(method.getParameters());
        TypeMirror parameterType = getOnlyElement(resolvedMethodType.getParameterTypes());
        setterMethods.put(
            requirement(method, parameter, parameterType, dependencyRequestFactory, method),
            method);
      }
    }
    verify(factoryMethod != null); // validation should have ensured this.

    ImmutableSetMultimap.Builder<ComponentRequirement, VariableElement> factoryParameters =
        ImmutableSetMultimap.builder();

    ExecutableType resolvedFactoryMethodType =
        MoreTypes.asExecutable(types.asMemberOf(type, factoryMethod));
    List<? extends VariableElement> parameters = factoryMethod.getParameters();
    List<? extends TypeMirror> parameterTypes = resolvedFactoryMethodType.getParameterTypes();
    for (int i = 0; i < parameters.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeMirror parameterType = parameterTypes.get(i);
      factoryParameters.put(
          requirement(factoryMethod, parameter, parameterType, dependencyRequestFactory, parameter),
          parameter);
    }

    // Validation should have ensured exactly one creator annotation is present on the type.
    ComponentCreatorAnnotation annotation = getOnlyElement(getCreatorAnnotations(typeElement));
    return new AutoValue_ComponentCreatorDescriptor(
        annotation, typeElement, factoryMethod, setterMethods.build(), factoryParameters.build());
  }

  private static ComponentRequirement requirement(
      ExecutableElement method,
      VariableElement parameter,
      TypeMirror type,
      DependencyRequestFactory dependencyRequestFactory,
      Element elementForVariableName) {
    if (isAnnotationPresent(method, BindsInstance.class)
        || isAnnotationPresent(parameter, BindsInstance.class)) {
      DependencyRequest request =
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, type);
      String variableName = elementForVariableName.getSimpleName().toString();
      return ComponentRequirement.forBoundInstance(
          request.key(), request.isNullable(), variableName);
    }

    return moduleAnnotation(asTypeElement(type)).isPresent()
        ? ComponentRequirement.forModule(type)
        : ComponentRequirement.forDependency(type);
  }
}
