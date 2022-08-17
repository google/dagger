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
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.spi.model.BindingKind.ASSISTED_FACTORY;
import static dagger.spi.model.BindingKind.ASSISTED_INJECTION;
import static dagger.spi.model.BindingKind.BOUND_INSTANCE;
import static dagger.spi.model.BindingKind.COMPONENT;
import static dagger.spi.model.BindingKind.COMPONENT_DEPENDENCY;
import static dagger.spi.model.BindingKind.COMPONENT_PRODUCTION;
import static dagger.spi.model.BindingKind.COMPONENT_PROVISION;
import static dagger.spi.model.BindingKind.DELEGATE;
import static dagger.spi.model.BindingKind.INJECTION;
import static dagger.spi.model.BindingKind.MEMBERS_INJECTOR;
import static dagger.spi.model.BindingKind.OPTIONAL;
import static dagger.spi.model.BindingKind.PRODUCTION;
import static dagger.spi.model.BindingKind.PROVISION;
import static dagger.spi.model.BindingKind.SUBCOMPONENT_CREATOR;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import dagger.Module;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProductionBinding.ProductionKind;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerType;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.inject.Inject;

/** A factory for {@link Binding} objects. */
public final class BindingFactory {
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final InjectionSiteFactory injectionSiteFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  BindingFactory(
      XProcessingEnv processingEnv,
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
   * Returns an {@link dagger.spi.model.BindingKind#INJECTION} binding.
   *
   * @param constructorElement the {@code @Inject}-annotated constructor
   * @param resolvedType the parameterized type if the constructor is for a generic class and the
   *     binding should be for the parameterized type
   */
  // TODO(dpb): See if we can just pass the parameterized type and not also the constructor.
  public ProvisionBinding injectionBinding(
      XConstructorElement constructorElement, Optional<XType> resolvedEnclosingType) {
    checkArgument(InjectionAnnotations.hasInjectOrAssistedInjectAnnotation(constructorElement));

    XConstructorType constructorType = constructorElement.getExecutableType();
    XType enclosingType = constructorElement.getEnclosingElement().getType();
    // If the class this is constructing has some type arguments, resolve everything.
    if (!enclosingType.getTypeArguments().isEmpty() && resolvedEnclosingType.isPresent()) {
      checkIsSameErasedType(resolvedEnclosingType.get(), enclosingType);
      enclosingType = resolvedEnclosingType.get();
      constructorType = constructorElement.asMemberOf(enclosingType);
    }

    // Collect all dependency requests within the provision method.
    // Note: we filter out @Assisted parameters since these aren't considered dependency requests.
    ImmutableSet.Builder<DependencyRequest> provisionDependencies = ImmutableSet.builder();
    for (int i = 0; i < constructorElement.getParameters().size(); i++) {
      XExecutableParameterElement parameter = constructorElement.getParameters().get(i);
      XType parameterType = constructorType.getParameterTypes().get(i);
      if (!AssistedInjectionAnnotations.isAssistedParameter(parameter)) {
        provisionDependencies.add(
            dependencyRequestFactory.forRequiredResolvedVariable(parameter, parameterType));
      }
    }

    ProvisionBinding.Builder builder =
        ProvisionBinding.builder()
            .contributionType(ContributionType.UNIQUE)
            .bindingElement(constructorElement)
            .key(keyFactory.forInjectConstructorWithResolvedType(enclosingType))
            .provisionDependencies(provisionDependencies.build())
            .injectionSites(injectionSiteFactory.getInjectionSites(enclosingType))
            .kind(
                constructorElement.hasAnnotation(TypeNames.ASSISTED_INJECT)
                    ? ASSISTED_INJECTION
                    : INJECTION)
            .scope(injectionAnnotations.getScope(constructorElement.getEnclosingElement()));

    if (hasNonDefaultTypeParameters(enclosingType)) {
      builder.unresolved(injectionBinding(constructorElement, Optional.empty()));
    }
    return builder.build();
  }

  public ProvisionBinding assistedFactoryBinding(
      XTypeElement factory, Optional<XType> resolvedFactoryType) {

    // If the class this is constructing has some type arguments, resolve everything.
    XType factoryType = factory.getType();
    if (!factoryType.getTypeArguments().isEmpty() && resolvedFactoryType.isPresent()) {
      checkIsSameErasedType(resolvedFactoryType.get(), factoryType);
      factoryType = resolvedFactoryType.get();
    }

    XMethodElement factoryMethod = AssistedInjectionAnnotations.assistedFactoryMethod(factory);
    XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .key(Key.builder(DaggerType.from(factoryType)).build())
        .bindingElement(factory)
        .provisionDependencies(
            ImmutableSet.of(
                DependencyRequest.builder()
                    .key(Key.builder(DaggerType.from(factoryMethodType.getReturnType())).build())
                    .kind(RequestKind.PROVIDER)
                    .build()))
        .kind(ASSISTED_FACTORY)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#PROVISION} binding for a
   * {@code @Provides}-annotated method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  public ProvisionBinding providesMethodBinding(
      XMethodElement providesMethod, XTypeElement contributedBy) {
    return setMethodBindingProperties(
            ProvisionBinding.builder(),
            providesMethod,
            contributedBy,
            keyFactory.forProvidesMethod(providesMethod, contributedBy),
            this::providesMethodBinding)
        .kind(PROVISION)
        .scope(injectionAnnotations.getScope(providesMethod))
        .nullableType(getNullableType(providesMethod))
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#PRODUCTION} binding for a
   * {@code @Produces}-annotated method.
   *
   * @param contributedBy the installed module that declares or inherits the method
   */
  public ProductionBinding producesMethodBinding(
      XMethodElement producesMethod, XTypeElement contributedBy) {
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
          XMethodElement method,
          XTypeElement contributedBy,
          Key key,
          BiFunction<XMethodElement, XTypeElement, C> create) {
    XMethodType methodType = method.asMemberOf(contributedBy.getType());
    if (!methodType.isSameType(method.getExecutableType())) {
      checkState(isTypeElement(method.getEnclosingElement()));
      builder.unresolved(create.apply(method, asTypeElement(method.getEnclosingElement())));
    }
    return builder
        .contributionType(ContributionType.fromBindingElement(method))
        .bindingElement(method)
        .contributingModule(contributedBy)
        .key(key)
        .dependencies(
            dependencyRequestFactory.forRequiredResolvedVariables(
                method.getParameters(), methodType.getParameterTypes()))
        .mapKey(getMapKey(method).map(DaggerAnnotation::from));
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#MULTIBOUND_MAP} or {@link
   * dagger.spi.model.BindingKind#MULTIBOUND_SET} binding given a set of multibinding contribution
   * bindings.
   *
   * @param key a key that may be satisfied by a multibinding
   */
  public ContributionBinding syntheticMultibinding(
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

  private static BindingKind bindingKindForMultibindingKey(Key key) {
    if (SetType.isSet(key)) {
      return BindingKind.MULTIBOUND_SET;
    } else if (MapType.isMap(key)) {
      return BindingKind.MULTIBOUND_MAP;
    } else {
      throw new IllegalArgumentException(String.format("key is not for a set or map: %s", key));
    }
  }

  private boolean multibindingRequiresProduction(
      Key key, Iterable<ContributionBinding> multibindingContributions) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (mapType.valuesAreTypeOf(TypeNames.PRODUCER)
          || mapType.valuesAreTypeOf(TypeNames.PRODUCED)) {
        return true;
      }
    } else if (SetType.isSet(key) && SetType.from(key).elementsAreTypeOf(TypeNames.PRODUCED)) {
      return true;
    }
    return Iterables.any(
        multibindingContributions, binding -> binding.bindingType().equals(BindingType.PRODUCTION));
  }

  /** Returns a {@link dagger.spi.model.BindingKind#COMPONENT} binding for the component. */
  public ProvisionBinding componentBinding(XTypeElement componentDefinitionType) {
    checkNotNull(componentDefinitionType);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(componentDefinitionType)
        .key(keyFactory.forType(componentDefinitionType.getType()))
        .kind(COMPONENT)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#COMPONENT_DEPENDENCY} binding for a component's
   * dependency.
   */
  public ProvisionBinding componentDependencyBinding(ComponentRequirement dependency) {
    checkNotNull(dependency);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(dependency.typeElement())
        .key(keyFactory.forType(dependency.type()))
        .kind(COMPONENT_DEPENDENCY)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#COMPONENT_PROVISION} or {@link
   * dagger.spi.model.BindingKind#COMPONENT_PRODUCTION} binding for a method on a component's
   * dependency.
   *
   * @param componentDescriptor the component with the dependency, not the dependency that has the
   *     method
   */
  public ContributionBinding componentDependencyMethodBinding(
      ComponentDescriptor componentDescriptor, XMethodElement dependencyMethod) {
    checkArgument(dependencyMethod.getParameters().isEmpty());
    ContributionBinding.Builder<?, ?> builder;
    if (componentDescriptor.isProduction() && isComponentProductionMethod(dependencyMethod)) {
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
              .scope(injectionAnnotations.getScope(dependencyMethod));
    }
    return builder
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(dependencyMethod)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#BOUND_INSTANCE} binding for a
   * {@code @BindsInstance}-annotated builder setter method or factory method parameter.
   */
  ProvisionBinding boundInstanceBinding(ComponentRequirement requirement, XElement element) {
    checkArgument(isVariableElement(element) || isMethod(element));
    XVariableElement parameterElement =
        isVariableElement(element)
            ? asVariable(element)
            : getOnlyElement(asMethod(element).getParameters());
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(element)
        .key(requirement.key().get())
        .nullableType(getNullableType(parameterElement))
        .kind(BOUND_INSTANCE)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared by a
   * component method that returns a subcomponent builder. Use {{@link
   * #subcomponentCreatorBinding(ImmutableSet)}} for bindings declared using {@link
   * Module#subcomponents()}.
   *
   * @param component the component that declares or inherits the method
   */
  ProvisionBinding subcomponentCreatorBinding(
      XMethodElement subcomponentCreatorMethod, XTypeElement component) {
    checkArgument(subcomponentCreatorMethod.getParameters().isEmpty());
    Key key =
        keyFactory.forSubcomponentCreatorMethod(subcomponentCreatorMethod, component.getType());
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(subcomponentCreatorMethod)
        .key(key)
        .kind(SUBCOMPONENT_CREATOR)
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#SUBCOMPONENT_CREATOR} binding declared using
   * {@link Module#subcomponents()}.
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
   * Returns a {@link dagger.spi.model.BindingKind#DELEGATE} binding.
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
            TypeNames.PRODUCER);

      case PROVISION:
        return buildDelegateBinding(
            ProvisionBinding.builder()
                .scope(injectionAnnotations.getScope(delegateDeclaration.bindingElement().get()))
                .nullableType(actualBinding.nullableType()),
            delegateDeclaration,
            TypeNames.PROVIDER);

      case MEMBERS_INJECTION: // fall-through to throw
    }
    throw new AssertionError("bindingType: " + actualBinding);
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#DELEGATE} binding used when there is no binding
   * that satisfies the {@code @Binds} declaration.
   */
  public ContributionBinding unresolvedDelegateBinding(DelegateDeclaration delegateDeclaration) {
    return buildDelegateBinding(
        ProvisionBinding.builder()
            .scope(injectionAnnotations.getScope(delegateDeclaration.bindingElement().get())),
        delegateDeclaration,
        TypeNames.PROVIDER);
  }

  private ContributionBinding buildDelegateBinding(
      ContributionBinding.Builder<?, ?> builder,
      DelegateDeclaration delegateDeclaration,
      ClassName frameworkType) {
    return builder
        .contributionType(delegateDeclaration.contributionType())
        .bindingElement(delegateDeclaration.bindingElement().get())
        .contributingModule(delegateDeclaration.contributingModule().get())
        .key(keyFactory.forDelegateBinding(delegateDeclaration, frameworkType))
        .dependencies(delegateDeclaration.delegateRequest())
        .mapKey(delegateDeclaration.mapKey())
        .kind(DELEGATE)
        .build();
  }

  /**
   * Returns an {@link dagger.spi.model.BindingKind#OPTIONAL} binding for {@code key}.
   *
   * @param requestKind the kind of request for the optional binding
   * @param underlyingKeyBindings the possibly empty set of bindings that exist in the component for
   *     the underlying (non-optional) key
   */
  ContributionBinding syntheticOptionalBinding(
      Key key,
      RequestKind requestKind,
      ImmutableCollection<? extends Binding> underlyingKeyBindings) {
    if (underlyingKeyBindings.isEmpty()) {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(key)
          .kind(OPTIONAL)
          .build();
    }

    boolean requiresProduction =
        underlyingKeyBindings.stream()
                .anyMatch(binding -> binding.bindingType() == BindingType.PRODUCTION)
            || requestKind.equals(RequestKind.PRODUCER) // handles producerFromProvider cases
            || requestKind.equals(RequestKind.PRODUCED); // handles producerFromProvider cases

    return (requiresProduction ? ProductionBinding.builder() : ProvisionBinding.builder())
        .contributionType(ContributionType.UNIQUE)
        .key(key)
        .kind(OPTIONAL)
        .dependencies(dependencyRequestFactory.forSyntheticPresentOptionalBinding(key, requestKind))
        .build();
  }

  /** Returns a {@link dagger.spi.model.BindingKind#MEMBERS_INJECTOR} binding. */
  public ProvisionBinding membersInjectorBinding(
      Key key, MembersInjectionBinding membersInjectionBinding) {
    return ProvisionBinding.builder()
        .key(key)
        .contributionType(ContributionType.UNIQUE)
        .kind(MEMBERS_INJECTOR)
        .bindingElement(membersInjectionBinding.key().type().xprocessing().getTypeElement())
        .provisionDependencies(membersInjectionBinding.dependencies())
        .injectionSites(membersInjectionBinding.injectionSites())
        .build();
  }

  /**
   * Returns a {@link dagger.spi.model.BindingKind#MEMBERS_INJECTION} binding.
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
    ImmutableSortedSet<InjectionSite> injectionSites = injectionSiteFactory.getInjectionSites(type);
    ImmutableSet<DependencyRequest> dependencies =
        injectionSites.stream()
            .flatMap(injectionSite -> injectionSite.dependencies().stream())
            .collect(toImmutableSet());

    return MembersInjectionBinding.create(
        keyFactory.forMembersInjectedType(type),
        dependencies,
        hasNonDefaultTypeParameters(type)
            ? Optional.of(
                membersInjectionBinding(type.getTypeElement().getType(), Optional.empty()))
            : Optional.empty(),
        injectionSites);
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
