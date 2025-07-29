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

package dagger.internal.codegen.binding;

import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.getCreatorAnnotations;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotations;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypes.isSubtype;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.internal.codegen.base.ComponentCreatorKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.List;

/**
 * A descriptor for a component <i>creator</i> type: that is, a type annotated with
 * {@code @Component.Builder} (or one of the corresponding production or subcomponent versions).
 */
@AutoValue
public abstract class ComponentCreatorDescriptor {

  /** Returns the annotation marking this creator. */
  public abstract ComponentCreatorAnnotation annotation();

  /** The kind of this creator. */
  public final ComponentCreatorKind kind() {
    return annotation().creatorKind();
  }

  /** The annotated creator type. */
  public abstract XTypeElement typeElement();

  /** The method that creates and returns a component instance. */
  public abstract XMethodElement factoryMethod();

  /**
   * Multimap of component requirements to setter methods that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  abstract ImmutableSetMultimap<ComponentRequirement, XMethodElement> unvalidatedSetterMethods();

  /**
   * Multimap of component requirements to factory method parameters that set that requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  abstract ImmutableSetMultimap<ComponentRequirement, XExecutableParameterElement>
      unvalidatedFactoryParameters();

  /**
   * Multimap of component requirements to elements (methods or parameters) that set that
   * requirement.
   *
   * <p>In a valid creator, there will be exactly one element per component requirement, so this
   * method should only be called when validating the descriptor.
   */
  public final ImmutableSetMultimap<ComponentRequirement, XElement>
      unvalidatedRequirementElements() {
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
  ImmutableMap<ComponentRequirement, XElement> requirementElements() {
    return flatten(unvalidatedRequirementElements());
  }

  /** Map of component requirements to setter methods for those requirements. */
  @Memoized
  public ImmutableMap<ComponentRequirement, XMethodElement> setterMethods() {
    return flatten(unvalidatedSetterMethods());
  }

  /** Map of component requirements to factory method parameters for those requirements. */
  @Memoized
  public ImmutableMap<ComponentRequirement, XExecutableParameterElement> factoryParameters() {
    return flatten(unvalidatedFactoryParameters());
  }

  private static <K, V> ImmutableMap<K, V> flatten(Multimap<K, V> multimap) {
    return ImmutableMap.copyOf(
        Maps.transformValues(multimap.asMap(), values -> getOnlyElement(values)));
  }

  /** Returns the set of component requirements this creator allows the user to set. */
  public final ImmutableSet<ComponentRequirement> userSettableRequirements() {
    // Note: they should have been validated at the point this is used, so this set is valid.
    return unvalidatedRequirementElements().keySet();
  }

  /** Returns the set of requirements for modules and component dependencies for this creator. */
  public final ImmutableSet<ComponentRequirement> moduleAndDependencyRequirements() {
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
  final XElement elementForRequirement(ComponentRequirement requirement) {
    return requirementElements().get(requirement);
  }

  /** Creates a new {@link ComponentCreatorDescriptor} for the given creator {@code type}. */
  public static ComponentCreatorDescriptor create(
      XTypeElement creator, DependencyRequestFactory dependencyRequestFactory) {
    XType componentType = creator.getEnclosingTypeElement().getType();

    ImmutableSetMultimap.Builder<ComponentRequirement, XMethodElement> setterMethods =
        ImmutableSetMultimap.builder();
    XMethodElement factoryMethod = null;
    for (XMethodElement method : getAllUnimplementedMethods(creator)) {
      XMethodType resolvedMethodType = method.asMemberOf(creator.getType());
      if (isSubtype(componentType, resolvedMethodType.getReturnType())) {
        verify(
            factoryMethod == null,
            "Expected a single factory method for %s but found multiple: [%s, %s]",
            XElements.toStableString(creator),
            XElements.toStableString(factoryMethod),
            XElements.toStableString(method));
        factoryMethod = method;
      } else {
        XExecutableParameterElement parameter = getOnlyElement(method.getParameters());
        XType parameterType = getOnlyElement(resolvedMethodType.getParameterTypes());
        setterMethods.put(
            requirement(method, parameter, parameterType, dependencyRequestFactory, method),
            method);
      }
    }
    verify(
        factoryMethod != null,
        "Expected a single factory method for %s but found none.",
        XElements.toStableString(creator));

    ImmutableSetMultimap.Builder<ComponentRequirement, XExecutableParameterElement>
        factoryParameters = ImmutableSetMultimap.builder();

    XMethodType resolvedFactoryMethodType = factoryMethod.asMemberOf(creator.getType());
    List<XExecutableParameterElement> parameters = factoryMethod.getParameters();
    List<XType> parameterTypes = resolvedFactoryMethodType.getParameterTypes();
    for (int i = 0; i < parameters.size(); i++) {
      XExecutableParameterElement parameter = parameters.get(i);
      XType parameterType = parameterTypes.get(i);
      factoryParameters.put(
          requirement(
              factoryMethod,
              parameter,
              parameterType,
              dependencyRequestFactory,
              parameter),
          parameter);
    }
    // Validation should have ensured exactly one creator annotation is present on the type.
    ComponentCreatorAnnotation annotation = getOnlyElement(getCreatorAnnotations(creator));
    return new AutoValue_ComponentCreatorDescriptor(
        annotation, creator, factoryMethod, setterMethods.build(), factoryParameters.build());
  }

  private static ComponentRequirement requirement(
      XMethodElement method,
      XExecutableParameterElement parameter,
      XType parameterType,
      DependencyRequestFactory dependencyRequestFactory,
      XElement elementForVariableName) {
    if (method.hasAnnotation(XTypeNames.BINDS_INSTANCE)
        || parameter.hasAnnotation(XTypeNames.BINDS_INSTANCE)) {
      DependencyRequest request =
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType);
      return ComponentRequirement.forBoundInstance(
          request.key(),
          request.isNullable(),
          elementForVariableName,
          Nullability.of(elementForVariableName));
    }

    return hasAnyAnnotation(parameterType.getTypeElement(), moduleAnnotations())
        ? ComponentRequirement.forModule(parameterType)
        : ComponentRequirement.forDependency(parameterType);
  }
}
