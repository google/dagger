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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.RequestKinds.getRequestKind;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/** A factory for {@link Binding} objects. */
public final class BindingFactory {
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final InjectionSiteFactory injectionSiteFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  BindingFactory(
      KeyFactory keyFactory,
      DependencyRequestFactory dependencyRequestFactory,
      InjectionSiteFactory injectionSiteFactory,
      InjectionAnnotations injectionAnnotations) {
    this.keyFactory = keyFactory;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.injectionSiteFactory = injectionSiteFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /**
   * Returns an {@link BindingKind#INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedEnclosingType the parameterized type if the constructor is for a generic class
   *     and the binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  public InjectionBinding injectionBinding(
      XConstructorElement constructorElement, Optional<XType> resolvedEnclosingType) {
    checkArgument(InjectionAnnotations.hasInjectAnnotation(constructorElement));

    XConstructorType constructorType = constructorElement.getExecutableType();
    XType enclosingType = constructorElement.getEnclosingElement().getType();
    // If the class this is constructing has some type arguments, resolve everything.
    if (!enclosingType.getTypeArguments().isEmpty() && resolvedEnclosingType.isPresent()) {
      checkIsSameErasedType(resolvedEnclosingType.get(), enclosingType);
      enclosingType = resolvedEnclosingType.get();
      constructorType = constructorElement.asMemberOf(enclosingType);
    }

    // Collect all dependency requests within the provision method.
    ImmutableSet.Builder<DependencyRequest> constructorDependencies = ImmutableSet.builder();
    for (int i = 0; i < constructorElement.getParameters().size(); i++) {
      XExecutableParameterElement parameter = constructorElement.getParameters().get(i);
      XType parameterType = constructorType.getParameterTypes().get(i);
      constructorDependencies.add(
          dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType));
    }

    return InjectionBinding.builder()
        .bindingElement(constructorElement)
        .key(keyFactory.forInjectConstructorWithResolvedType(enclosingType))
        .constructorDependencies(constructorDependencies.build())
        .injectionSites(injectionSiteFactory.getInjectionSites(enclosingType))
        .scope(injectionAnnotations.getScope(constructorElement.getEnclosingElement()))
        .unresolved(
            hasNonDefaultTypeParameters(enclosingType)
                ? Optional.of(injectionBinding(constructorElement, Optional.empty()))
                : Optional.empty())
        .build();
  }

  /**
   * Returns an {@link BindingKind#ASSISTED_INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedEnclosingType the parameterized type if the constructor is for a generic class
   *     and the binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  public AssistedInjectionBinding assistedInjectionBinding(
      XConstructorElement constructorElement, Optional<XType> resolvedEnclosingType) {
    checkArgument(constructorElement.hasAnnotation(XTypeNames.ASSISTED_INJECT));

    XConstructorType constructorType = constructorElement.getExecutableType();
    XType enclosingType = constructorElement.getEnclosingElement().getType();
    // If the class this is constructing has some type arguments, resolve everything.
    if (!enclosingType.getTypeArguments().isEmpty() && resolvedEnclosingType.isPresent()) {
      checkIsSameErasedType(resolvedEnclosingType.get(), enclosingType);
      enclosingType = resolvedEnclosingType.get();
      constructorType = constructorElement.asMemberOf(enclosingType);
    }

    // Collect all dependency requests within the provision method.
    ImmutableSet.Builder<DependencyRequest> constructorDependencies = ImmutableSet.builder();
    for (int i = 0; i < constructorElement.getParameters().size(); i++) {
      XExecutableParameterElement parameter = constructorElement.getParameters().get(i);
      XType parameterType = constructorType.getParameterTypes().get(i);
      // Note: we filter out @Assisted parameters since these aren't considered dependency requests.
      if (!AssistedInjectionAnnotations.isAssistedParameter(parameter)) {
        constructorDependencies.add(
            dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType));
      }
    }

    return AssistedInjectionBinding.builder()
        .bindingElement(constructorElement)
        .key(keyFactory.forInjectConstructorWithResolvedType(enclosingType))
        .constructorDependencies(constructorDependencies.build())
        .injectionSites(injectionSiteFactory.getInjectionSites(enclosingType))
        .scope(injectionAnnotations.getScope(constructorElement.getEnclosingElement()))
        .unresolved(
            hasNonDefaultTypeParameters(enclosingType)
                ? Optional.of(assistedInjectionBinding(constructorElement, Optional.empty()))
                : Optional.empty())
        .build();
  }

  public AssistedFactoryBinding assistedFactoryBinding(
      XTypeElement factory, Optional<XType> resolvedFactoryType) {

    // If the class this is constructing has some type arguments, resolve everything.
    XType factoryType = factory.getType();
    if (!factoryType.getTypeArguments().isEmpty() && resolvedFactoryType.isPresent()) {
      checkIsSameErasedType(resolvedFactoryType.get(), factoryType);
      factoryType = resolvedFactoryType.get();
    }

    XMethodElement factoryMethod = AssistedInjectionAnnotations.assistedFactoryMethod(factory);
    XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
    return AssistedFactoryBinding.builder()
        .key(keyFactory.forType(factoryType))
        .bindingElement(factory)
        .assistedInjectKey(keyFactory.forType(factoryMethodType.getReturnType()))
        .build();
  }

  /**
   * Returns a {@link BindingKind#PROVISION} binding for a {@code @Provides}-annotated method.
   *
   * @param module the installed module that declares or inherits the method
   */
  public ProvisionBinding providesMethodBinding(XMethodElement method, XTypeElement module) {
    XMethodType methodType = method.asMemberOf(module.getType());
    return ProvisionBinding.builder()
        .scope(injectionAnnotations.getScope(method))
        .nullability(Nullability.of(method))
        .bindingElement(method)
        .contributingModule(module)
        .key(keyFactory.forProvidesMethod(method, module))
        .dependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()))
        .unresolved(
            methodType.isSameType(method.getExecutableType())
                ? Optional.empty()
                : Optional.of(
                    providesMethodBinding(method, asTypeElement(method.getEnclosingElement()))))
        .build();
  }

  /**
   * Returns a {@link BindingKind#PRODUCTION} binding for a {@code @Produces}-annotated method.
   *
   * @param module the installed module that declares or inherits the method
   */
  public ProductionBinding producesMethodBinding(XMethodElement method, XTypeElement module) {
    // TODO(beder): Add nullability checking with Java 8.
    XMethodType methodType = method.asMemberOf(module.getType());
    return ProductionBinding.builder()
        .bindingElement(method)
        .contributingModule(module)
        .key(keyFactory.forProducesMethod(method, module))
        .executorRequest(dependencyRequestFactory.forProductionImplementationExecutor())
        .monitorRequest(dependencyRequestFactory.forProductionComponentMonitor())
        .explicitDependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()))
        .scope(injectionAnnotations.getScope(method))
        .unresolved(
            methodType.isSameType(method.getExecutableType())
                ? Optional.empty()
                : Optional.of(
                    producesMethodBinding(method, asTypeElement(method.getEnclosingElement()))))
        .build();
  }

  /**
   * Returns a {@link BindingKind#MULTIBOUND_MAP} binding given a set of multibinding contributions.
   *
   * @param key a key that may be satisfied by a multibinding
   */
  public MultiboundMapBinding multiboundMap(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    return MultiboundMapBinding.builder()
        .optionalBindingType(multibindingBindingType(key, multibindingContributions))
        .key(key)
        .dependencies(
            dependencyRequestFactory.forMultibindingContributions(key, multibindingContributions))
        .build();
  }

  /**
   * Returns a {@link BindingKind#MULTIBOUND_SET} binding given a set of multibinding contributions.
   *
   * @param key a key that may be satisfied by a multibinding
   */
  public MultiboundSetBinding multiboundSet(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    return MultiboundSetBinding.builder()
        .optionalBindingType(multibindingBindingType(key, multibindingContributions))
        .key(key)
        .dependencies(
            dependencyRequestFactory.forMultibindingContributions(key, multibindingContributions))
        .build();
  }

  private Optional<BindingType> multibindingBindingType(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (mapType.valuesAreTypeOf(XTypeNames.PRODUCER)
          || mapType.valuesAreTypeOf(XTypeNames.PRODUCED)) {
        return Optional.of(BindingType.PRODUCTION);
      }
    } else if (SetType.isSet(key) && SetType.from(key).elementsAreTypeOf(XTypeNames.PRODUCED)) {
      return Optional.of(BindingType.PRODUCTION);
    }
    if (Iterables.any(
            multibindingContributions,
            binding -> binding.optionalBindingType().equals(Optional.of(BindingType.PRODUCTION)))) {
      return Optional.of(BindingType.PRODUCTION);
    }
    return Iterables.any(
            multibindingContributions,
            binding -> binding.optionalBindingType().isEmpty())
        // If a dependency is missing a BindingType then we can't determine the BindingType of this
        // binding yet since it may end up depending on a production type.
        ? Optional.empty()
        : Optional.of(BindingType.PROVISION);
  }

  /**
   * Returns a {@link BindingKind#COMPONENT} binding for the
   * component.
   */
  public ComponentBinding componentBinding(XTypeElement componentDefinitionType) {
    checkNotNull(componentDefinitionType);
    return ComponentBinding.builder()
        .bindingElement(componentDefinitionType)
        .key(keyFactory.forType(componentDefinitionType.getType()))
        .build();
  }

  /**
   * Returns a {@link BindingKind#COMPONENT_DEPENDENCY} binding for a
   * component's dependency.
   */
  public ComponentDependencyBinding componentDependencyBinding(ComponentRequirement dependency) {
    checkNotNull(dependency);
    return ComponentDependencyBinding.builder()
        .bindingElement(dependency.typeElement())
        .key(keyFactory.forType(dependency.type()))
        .build();
  }

  /**
   * Returns a {@link BindingKind#COMPONENT_PROVISION} binding for a
   * method on a component's dependency.
   */
  public ComponentDependencyProvisionBinding componentDependencyProvisionMethodBinding(
      XMethodElement dependencyMethod) {
    checkArgument(dependencyMethod.getParameters().isEmpty());
    return ComponentDependencyProvisionBinding.builder()
        .key(keyFactory.forComponentMethod(dependencyMethod))
        .nullability(Nullability.of(dependencyMethod))
        .scope(injectionAnnotations.getScope(dependencyMethod))
        .bindingElement(dependencyMethod)
        .build();
  }

  /**
   * Returns a {@link BindingKind#COMPONENT_PRODUCTION} binding for a
   * method on a component's dependency.
   */
  public ComponentDependencyProductionBinding componentDependencyProductionMethodBinding(
      XMethodElement dependencyMethod) {
    checkArgument(dependencyMethod.getParameters().isEmpty());
    return ComponentDependencyProductionBinding.builder()
        .key(keyFactory.forProductionComponentMethod(dependencyMethod))
        .bindingElement(dependencyMethod)
        .build();
  }

  /**
   * Returns a {@link BindingKind#BOUND_INSTANCE} binding for a
   * {@code @BindsInstance}-annotated builder setter method or factory method parameter.
   */
  BoundInstanceBinding boundInstanceBinding(ComponentRequirement requirement, XElement element) {
    checkArgument(isVariableElement(element) || isMethod(element));
    XVariableElement parameterElement =
        isVariableElement(element)
            ? asVariable(element)
            : getOnlyElement(asMethod(element).getParameters());
    return BoundInstanceBinding.builder()
        .bindingElement(element)
        .key(requirement.key().get())
        .nullability(Nullability.of(parameterElement))
        .build();
  }

  /**
   * Returns a {@link BindingKind#SUBCOMPONENT_CREATOR} binding
   * declared by a component method that returns a subcomponent builder. Use {{@link
   * #subcomponentCreatorBinding(ImmutableSet)}} for bindings declared using {@link
   * Module#subcomponents()}.
   *
   * @param component the component that declares or inherits the method
   */
  SubcomponentCreatorBinding subcomponentCreatorBinding(
      XMethodElement subcomponentCreatorMethod, XTypeElement component) {
    checkArgument(subcomponentCreatorMethod.getParameters().isEmpty());
    Key key =
        keyFactory.forSubcomponentCreatorMethod(subcomponentCreatorMethod, component.getType());
    return SubcomponentCreatorBinding.builder()
        .bindingElement(subcomponentCreatorMethod)
        .key(key)
        .build();
  }

  /**
   * Returns a {@link BindingKind#SUBCOMPONENT_CREATOR} binding
   * declared using {@link Module#subcomponents()}.
   */
  SubcomponentCreatorBinding subcomponentCreatorBinding(
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
    SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
    return SubcomponentCreatorBinding.builder().key(subcomponentDeclaration.key()).build();
  }

  /** Returns a {@link BindingKind#DELEGATE} binding. */
  DelegateBinding delegateBinding(DelegateDeclaration delegateDeclaration) {
    return delegateBinding(delegateDeclaration, Optional.empty());
  }

  /**
   * Returns a {@link BindingKind#DELEGATE} binding.
   *
   * @param delegateDeclaration the {@code @Binds}-annotated declaration
   * @param actualBinding the binding that satisfies the {@code @Binds} declaration
   */
  DelegateBinding delegateBinding(
      DelegateDeclaration delegateDeclaration, ContributionBinding actualBinding) {
    return delegateBinding(delegateDeclaration, delegateBindingType(Optional.of(actualBinding)));
  }

  private DelegateBinding delegateBinding(
      DelegateDeclaration delegateDeclaration, Optional<BindingType> optionalBindingType) {
    return DelegateBinding.builder()
        .contributionType(delegateDeclaration.contributionType())
        .bindingElement(delegateDeclaration.bindingElement().get())
        .contributingModule(delegateDeclaration.contributingModule().get())
        .delegateRequest(delegateDeclaration.delegateRequest())
        .nullability(Nullability.of(delegateDeclaration.bindingElement().get()))
        .optionalBindingType(optionalBindingType)
        .key(
            optionalBindingType.isEmpty()
                // This is used by BindingGraphFactory which passes in an empty optionalBindingType.
                // In this case, multibound map contributions will always return the key type
                // without framework types, i.e. Map<K,V>.
                ? delegateDeclaration.key()
                // This is used by LegacyBindingGraphFactory, which passes in a non-empty
                // optionalBindingType. Then, KeyFactory decides whether or not multibound map
                // contributions should include the factory type based on the compiler flag,
                // -Adagger.useFrameworkTypeInMapMultibindingContributionKey.
                : optionalBindingType.get() == BindingType.PRODUCTION
                    ? keyFactory.forDelegateBinding(delegateDeclaration, XTypeNames.PRODUCER)
                    : keyFactory.forDelegateBinding(delegateDeclaration, XTypeNames.JAVAX_PROVIDER))
        .scope(injectionAnnotations.getScope(delegateDeclaration.bindingElement().get()))
        .build();
  }

  /**
   * Returns a {@link BindingKind#DELEGATE} binding used when there is
   * no binding that satisfies the {@code @Binds} declaration.
   */
  public DelegateBinding unresolvedDelegateBinding(DelegateDeclaration delegateDeclaration) {
    return delegateBinding(delegateDeclaration, Optional.of(BindingType.PROVISION));
  }

  private Optional<BindingType> delegateBindingType(Optional<ContributionBinding> actualBinding) {
    if (actualBinding.isEmpty()) {
      return Optional.empty();
    }
    checkArgument(actualBinding.get().bindingType() != BindingType.MEMBERS_INJECTION);
    return Optional.of(actualBinding.get().bindingType());
  }

  /** Returns an {@link BindingKind#OPTIONAL} present binding for {@code key}. */
  OptionalBinding syntheticPresentOptionalDeclaration(
      Key key, ImmutableCollection<Binding> optionalContributions) {
    checkArgument(!optionalContributions.isEmpty());
    return OptionalBinding.builder()
        .optionalBindingType(presentOptionalBindingType(key, optionalContributions))
        .key(key)
        .delegateRequest(dependencyRequestFactory.forSyntheticPresentOptionalBinding(key))
        .build();
  }

  private Optional<BindingType> presentOptionalBindingType(
      Key key, ImmutableCollection<Binding> optionalContributions) {
    RequestKind requestKind = getRequestKind(OptionalType.from(key).valueType());
    if (requestKind.equals(RequestKind.PRODUCER) // handles producerFromProvider cases
            || requestKind.equals(RequestKind.PRODUCED)) { // handles producerFromProvider cases
      return Optional.of(BindingType.PRODUCTION);
    }
    if (optionalContributions.stream()
            .filter(binding -> binding.optionalBindingType().isPresent())
            .anyMatch(binding -> binding.bindingType() == BindingType.PRODUCTION)) {
      return Optional.of(BindingType.PRODUCTION);
    }
    return optionalContributions.stream()
            .anyMatch(binding -> binding.optionalBindingType().isEmpty())
        // If a dependency is missing a BindingType then we can't determine the BindingType of this
        // binding yet since it may end up depending on a production type.
        ? Optional.empty()
        : Optional.of(BindingType.PROVISION);
  }

  /** Returns an {@link BindingKind#OPTIONAL} absent binding for {@code key}. */
  OptionalBinding syntheticAbsentOptionalDeclaration(Key key) {
    return OptionalBinding.builder()
        .key(key)
        .optionalBindingType(Optional.of(BindingType.PROVISION))
        .build();
  }

  /** Returns a {@link BindingKind#MEMBERS_INJECTOR} binding. */
  public MembersInjectorBinding membersInjectorBinding(
      Key key, MembersInjectionBinding membersInjectionBinding) {
    return MembersInjectorBinding.builder()
        .key(key)
        .bindingElement(membersInjectionBinding.key().type().xprocessing().getTypeElement())
        .injectionSites(membersInjectionBinding.injectionSites())
        .build();
  }

  /**
   * Returns a {@link BindingKind#MEMBERS_INJECTION} binding.
   *
   * @param resolvedType if {@code declaredType} is a generic class and {@code resolvedType} is a
   *     parameterization of that type, the returned binding will be for the resolved type
   */
  // TODO(dpb): See if we can just pass one nongeneric/parameterized type.
  public MembersInjectionBinding membersInjectionBinding(XType type, Optional<XType> resolvedType) {
    // If the class this is injecting has some type arguments, resolve everything.
    if (!type.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
      checkIsSameErasedType(resolvedType.get(), type);
      type = resolvedType.get();
    }
    return MembersInjectionBinding.builder()
        .key(keyFactory.forMembersInjectedType(type))
        .injectionSites(injectionSiteFactory.getInjectionSites(type))
        .unresolved(
            hasNonDefaultTypeParameters(type)
                ? Optional.of(
                    membersInjectionBinding(type.getTypeElement().getType(), Optional.empty()))
                : Optional.empty())
        .build();
  }

  private void checkIsSameErasedType(XType type1, XType type2) {
    checkState(
        erasedTypeName(type1).equals(erasedTypeName(type2)),
        "erased expected type: %s, erased actual type: %s",
        erasedTypeName(type1),
        erasedTypeName(type2));
  }

  private static boolean hasNonDefaultTypeParameters(XType type) {
    // If the type is not declared, then it can't have type parameters.
    if (!isDeclared(type)) {
      return false;
    }

    // If the element has no type parameters, none can be non-default.
    XType defaultType = type.getTypeElement().getType();
    if (defaultType.getTypeArguments().isEmpty()) {
      return false;
    }

    // The actual type parameter size can be different if the user is using a raw type.
    if (defaultType.getTypeArguments().size() != type.getTypeArguments().size()) {
      return true;
    }

    for (int i = 0; i < defaultType.getTypeArguments().size(); i++) {
      if (!defaultType.getTypeArguments().get(i).isSameType(type.getTypeArguments().get(i))) {
        return true;
      }
    }
    return false;
  }
}
