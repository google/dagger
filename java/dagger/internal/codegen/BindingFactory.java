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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Binding.hasNonDefaultTypeParameters;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.ContributionBinding.bindingKindForMultibindingKey;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static dagger.internal.codegen.Scopes.uniqueScopeOf;
import static dagger.model.BindingKind.BOUND_INSTANCE;
import static dagger.model.BindingKind.COMPONENT;
import static dagger.model.BindingKind.COMPONENT_DEPENDENCY;
import static dagger.model.BindingKind.COMPONENT_PRODUCTION;
import static dagger.model.BindingKind.COMPONENT_PROVISION;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.INJECTION;
import static dagger.model.BindingKind.MEMBERS_INJECTOR;
import static dagger.model.BindingKind.OPTIONAL;
import static dagger.model.BindingKind.PRODUCTION;
import static dagger.model.BindingKind.PROVISION;
import static dagger.model.BindingKind.SUBCOMPONENT_CREATOR;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.ProductionBinding.ProductionKind;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link Binding} objects. */
final class BindingFactory {
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final InjectionSiteFactory injectionSiteFactory;
  private final DaggerElements elements;

  @Inject
  BindingFactory(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      DependencyRequestFactory dependencyRequestFactory,
      InjectionSiteFactory injectionSiteFactory) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.injectionSiteFactory = injectionSiteFactory;
  }

  /**
   * Returns an {@link dagger.model.BindingKind#INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedType the parameterized type if the constructor is for a generic class and the
   *     binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  ProvisionBinding injectionBinding(
      ExecutableElement constructorElement, Optional<TypeMirror> resolvedType) {
    checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
    checkArgument(isAnnotationPresent(constructorElement, Inject.class));
    checkArgument(!getQualifier(constructorElement).isPresent());

    ExecutableType constructorType = MoreTypes.asExecutable(constructorElement.asType());
    DeclaredType constructedType =
        MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
    // If the class this is constructing has some type arguments, resolve everything.
    if (!constructedType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
      DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
      // Validate that we're resolving from the correct type.
      checkState(
          types.isSameType(types.erasure(resolved), types.erasure(constructedType)),
          "erased expected type: %s, erased actual type: %s",
          types.erasure(resolved),
          types.erasure(constructedType));
      constructorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
      constructedType = resolved;
    }

    Key key = keyFactory.forInjectConstructorWithResolvedType(constructedType);
    ImmutableSet<DependencyRequest> provisionDependencies =
        dependencyRequestFactory.forRequiredResolvedVariables(
            constructorElement.getParameters(), constructorType.getParameterTypes());

    ProvisionBinding.Builder builder =
        ProvisionBinding.builder()
            .contributionType(ContributionType.UNIQUE)
            .bindingElement(constructorElement)
            .key(key)
            .provisionDependencies(provisionDependencies)
            .injectionSites(injectionSiteFactory.getInjectionSites(constructedType))
            .kind(INJECTION)
            .scope(uniqueScopeOf(constructorElement.getEnclosingElement()));

    TypeElement bindingTypeElement = MoreElements.asType(constructorElement.getEnclosingElement());
    if (hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)) {
      builder.unresolved(injectionBinding(constructorElement, Optional.empty()));
    }
    return builder.build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#PROVISION} binding for a {@code @Provides}-annotated
   * method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  ProvisionBinding providesMethodBinding(
      ExecutableElement providesMethod, TypeElement contributedBy) {
    return setMethodBindingProperties(
            ProvisionBinding.builder(),
            providesMethod,
            contributedBy,
            keyFactory.forProvidesMethod(providesMethod, contributedBy),
            this::providesMethodBinding)
        .kind(PROVISION)
        .scope(uniqueScopeOf(providesMethod))
        .nullableType(getNullableType(providesMethod))
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#PRODUCTION} binding for a {@code @Produces}-annotated
   * method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  ProductionBinding producesMethodBinding(
      ExecutableElement producesMethod, TypeElement contributedBy) {
    // TODO(beder): Add nullability checking with Java 8.
    ProductionBinding.Builder builder =
        setMethodBindingProperties(
                ProductionBinding.builder(),
                producesMethod,
                contributedBy,
                keyFactory.forProducesMethod(producesMethod, contributedBy),
                this::producesMethodBinding)
            .kind(PRODUCTION)
            .productionKind(ProductionKind.fromProducesMethod(producesMethod))
            .thrownTypes(producesMethod.getThrownTypes())
            .executorRequest(dependencyRequestFactory.forProductionImplementationExecutor())
            .monitorRequest(dependencyRequestFactory.forProductionComponentMonitor());
    return builder.build();
  }

  private <C extends ContributionBinding, B extends ContributionBinding.Builder<C, B>>
      B setMethodBindingProperties(
          B builder,
          ExecutableElement method,
          TypeElement contributedBy,
          Key key,
          BiFunction<ExecutableElement, TypeElement, C> create) {
    checkArgument(method.getKind().equals(METHOD));
    ExecutableType methodType =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), method));
    if (!types.isSameType(methodType, method.asType())) {
      builder.unresolved(create.apply(method, MoreElements.asType(method.getEnclosingElement())));
    }
    return builder
        .contributionType(ContributionType.fromBindingElement(method))
        .bindingElement(method)
        .contributingModule(contributedBy)
        .key(key)
        .dependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()))
        .wrappedMapKeyAnnotation(wrapOptionalInEquivalence(getMapKey(method)));
  }

  /**
   * Returns a {@link dagger.model.BindingKind#MULTIBOUND_MAP} or {@link
   * dagger.model.BindingKind#MULTIBOUND_SET} binding given a set of multibinding contribution
   * bindings.
   *
   * @param key a key that may be satisfied by a multibinding
   */
  ContributionBinding syntheticMultibinding(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    ContributionBinding.Builder<?, ?> builder =
        multibindingRequiresProduction(key, multibindingContributions)
            ? ProductionBinding.builder()
            : ProvisionBinding.builder();
    return builder
        .contributionType(ContributionType.UNIQUE)
        .key(key)
        .dependencies(
            dependencyRequestFactory.forMultibindingContributions(key, multibindingContributions))
        .kind(bindingKindForMultibindingKey(key))
        .build();
  }

  private boolean multibindingRequiresProduction(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (mapType.valuesAreTypeOf(Producer.class) || mapType.valuesAreTypeOf(Produced.class)) {
        return true;
      }
    } else if (SetType.isSet(key) && SetType.from(key).elementsAreTypeOf(Produced.class)) {
      return true;
    }
    return Iterables.any(
        multibindingContributions, binding -> binding.bindingType().equals(BindingType.PRODUCTION));
  }

  /** Returns a {@link dagger.model.BindingKind#COMPONENT} binding for the component. */
  ProvisionBinding componentBinding(TypeElement componentDefinitionType) {
    checkNotNull(componentDefinitionType);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(componentDefinitionType)
        .key(keyFactory.forType(componentDefinitionType.asType()))
        .kind(COMPONENT)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#COMPONENT_DEPENDENCY} binding for a component's
   * dependency.
   */
  ProvisionBinding componentDependencyBinding(ComponentRequirement dependency) {
    checkNotNull(dependency);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(dependency.typeElement())
        .key(keyFactory.forType(dependency.type()))
        .kind(COMPONENT_DEPENDENCY)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#COMPONENT_PROVISION} or {@link
   * dagger.model.BindingKind#COMPONENT_PRODUCTION} binding for a method on a component's
   * dependency.
   *
   * @param componentDescriptor the component with the dependency, not the dependency that has the
   *     method
   */
  ContributionBinding componentDependencyMethodBinding(
      ComponentDescriptor componentDescriptor, ExecutableElement dependencyMethod) {
    checkArgument(dependencyMethod.getKind().equals(METHOD));
    checkArgument(dependencyMethod.getParameters().isEmpty());
    ContributionBinding.Builder<?, ?> builder;
    if (componentDescriptor.isProduction()
        && isComponentProductionMethod(elements, dependencyMethod)) {
      builder =
          ProductionBinding.builder()
              .key(keyFactory.forProductionComponentMethod(dependencyMethod))
              .kind(COMPONENT_PRODUCTION)
              .thrownTypes(dependencyMethod.getThrownTypes());
    } else {
      builder =
          ProvisionBinding.builder()
              .key(keyFactory.forComponentMethod(dependencyMethod))
              .nullableType(getNullableType(dependencyMethod))
              .kind(COMPONENT_PROVISION)
              .scope(uniqueScopeOf(dependencyMethod));
    }
    return builder
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(dependencyMethod)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#BOUND_INSTANCE} binding for a
   * {@code @BindsInstance}-annotated builder setter method or factory method parameter.
   */
  ProvisionBinding boundInstanceBinding(ComponentRequirement requirement, Element element) {
    checkArgument(element instanceof VariableElement || element instanceof ExecutableElement);
    VariableElement parameterElement =
        element instanceof VariableElement
            ? MoreElements.asVariable(element)
            : getOnlyElement(MoreElements.asExecutable(element).getParameters());
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(element)
        .key(requirement.key().get())
        .nullableType(getNullableType(parameterElement))
        .kind(BOUND_INSTANCE)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared by a component
   * method that returns a subcomponent builder. Use {{@link
   * #subcomponentCreatorBinding(ImmutableSet)}} for bindings declared using {@link
   * Module#subcomponents()}.
   *
   * @param component the component that declares or inherits the method
   */
  ProvisionBinding subcomponentCreatorBinding(
      ExecutableElement subcomponentCreatorMethod, TypeElement component) {
    checkArgument(subcomponentCreatorMethod.getKind().equals(METHOD));
    checkArgument(subcomponentCreatorMethod.getParameters().isEmpty());
    Key key =
        keyFactory.forSubcomponentCreatorMethod(
            subcomponentCreatorMethod, asDeclared(component.asType()));
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(subcomponentCreatorMethod)
        .key(key)
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared using {@link
   * Module#subcomponents()}.
   */
  ProvisionBinding subcomponentCreatorBinding(
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
    SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .key(subcomponentDeclaration.key())
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#DELEGATE} binding.
   *
   * @param delegateDeclaration the {@code @Binds}-annotated declaration
   * @param actualBinding the binding that satisfies the {@code @Binds} declaration
   */
  ContributionBinding delegateBinding(
      DelegateDeclaration delegateDeclaration, ContributionBinding actualBinding) {
    switch (actualBinding.bindingType()) {
      case PRODUCTION:
        return buildDelegateBinding(
            ProductionBinding.builder().nullableType(actualBinding.nullableType()),
            delegateDeclaration,
            Producer.class);

      case PROVISION:
        return buildDelegateBinding(
            ProvisionBinding.builder()
                .scope(uniqueScopeOf(delegateDeclaration.bindingElement().get()))
                .nullableType(actualBinding.nullableType()),
            delegateDeclaration,
            Provider.class);

      case MEMBERS_INJECTION: // fall-through to throw
    }
    throw new AssertionError("bindingType: " + actualBinding);
  }

  /**
   * Returns a {@link dagger.model.BindingKind#DELEGATE} binding used when there is no binding that
   * satisfies the {@code @Binds} declaration.
   */
  ContributionBinding unresolvedDelegateBinding(DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder().scope(uniqueScopeOf(delegateDeclaration.bindingElement().get())),
        delegateDeclaration,
        Provider.class);
  }

  private ContributionBinding buildDelegateBinding(
      ContributionBinding.Builder<?, ?> builder,
      DelegateDeclaration delegateDeclaration,
      Class<?> frameworkType) {
    return builder
        .contributionType(delegateDeclaration.contributionType())
        .bindingElement(delegateDeclaration.bindingElement().get())
        .contributingModule(delegateDeclaration.contributingModule().get())
        .key(keyFactory.forDelegateBinding(delegateDeclaration, frameworkType))
        .dependencies(delegateDeclaration.delegateRequest())
        .wrappedMapKeyAnnotation(delegateDeclaration.wrappedMapKey())
        .kind(DELEGATE)
        .build();
  }

  /**
   * Returns an {@link dagger.model.BindingKind#OPTIONAL} binding for {@code key}.
   *
   * @param requestKind the kind of request for the optional binding
   * @param underlyingKeyBindings the possibly empty set of bindings that exist in the component for
   *     the underlying (non-optional) key
   */
  ContributionBinding syntheticOptionalBinding(
      Key key, RequestKind requestKind, ResolvedBindings underlyingKeyBindings) {
    ContributionBinding.Builder<?, ?> builder =
        syntheticOptionalBindingBuilder(requestKind, underlyingKeyBindings)
            .contributionType(ContributionType.UNIQUE)
            .key(key)
            .kind(OPTIONAL);
    if (!underlyingKeyBindings.isEmpty()) {
      builder.dependencies(
          dependencyRequestFactory.forSyntheticPresentOptionalBinding(key, requestKind));
    }
    return builder.build();
  }

  private ContributionBinding.Builder<?, ?> syntheticOptionalBindingBuilder(
      RequestKind requestKind, ResolvedBindings underlyingKeyBindings) {
    return !underlyingKeyBindings.isEmpty()
            && (underlyingKeyBindings.bindingTypes().contains(BindingType.PRODUCTION)
                || requestKind.equals(RequestKind.PRODUCER) // handles producerFromProvider cases
                || requestKind.equals(RequestKind.PRODUCED)) // handles producerFromProvider cases
        ? ProductionBinding.builder()
        : ProvisionBinding.builder();
  }

  /** Returns a {@link dagger.model.BindingKind#MEMBERS_INJECTOR} binding. */
  ProvisionBinding membersInjectorBinding(
      Key key, MembersInjectionBinding membersInjectionBinding) {
    return ProvisionBinding.builder()
        .key(key)
        .contributionType(ContributionType.UNIQUE)
        .kind(MEMBERS_INJECTOR)
        .bindingElement(MoreTypes.asTypeElement(membersInjectionBinding.key().type()))
        .provisionDependencies(membersInjectionBinding.dependencies())
        .injectionSites(membersInjectionBinding.injectionSites())
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#MEMBERS_INJECTION} binding.
   *
   * @param resolvedType if {@code declaredType} is a generic class and {@code resolvedType} is a
   *     parameterization of that type, the returned binding will be for the resolved type
   */
  // TODO(dpb): See if we can just pass one nongeneric/parameterized type.
  MembersInjectionBinding membersInjectionBinding(
      DeclaredType declaredType, Optional<TypeMirror> resolvedType) {
    // If the class this is injecting has some type arguments, resolve everything.
    if (!declaredType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
      DeclaredType resolved = asDeclared(resolvedType.get());
      // Validate that we're resolving from the correct type.
      checkState(
          types.isSameType(types.erasure(resolved), types.erasure(declaredType)),
          "erased expected type: %s, erased actual type: %s",
          types.erasure(resolved),
          types.erasure(declaredType));
      declaredType = resolved;
    }
    ImmutableSortedSet<InjectionSite> injectionSites =
        injectionSiteFactory.getInjectionSites(declaredType);
    ImmutableSet<DependencyRequest> dependencies =
        injectionSites
            .stream()
            .flatMap(injectionSite -> injectionSite.dependencies().stream())
            .collect(toImmutableSet());

    Key key = keyFactory.forMembersInjectedType(declaredType);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    return new AutoValue_MembersInjectionBinding(
        key,
        dependencies,
        typeElement,
        hasNonDefaultTypeParameters(typeElement, key.type(), types)
            ? Optional.of(
                membersInjectionBinding(asDeclared(typeElement.asType()), Optional.empty()))
            : Optional.empty(),
        injectionSites);
  }
}
