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
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
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
import static dagger.model.BindingKind.RELEASABLE_REFERENCE_MANAGER;
import static dagger.model.BindingKind.RELEASABLE_REFERENCE_MANAGERS;
import static dagger.model.BindingKind.SUBCOMPONENT_BUILDER;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import dagger.Module;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.ProductionBinding.ProductionKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.model.Scope;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

/** A factory for {@link Binding} objects. */
final class BindingFactory {
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final DaggerElements elements;

  @Inject
  BindingFactory(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      DependencyRequestFactory dependencyRequestFactory) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.dependencyRequestFactory = dependencyRequestFactory;
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
            .injectionSites(getInjectionSites(constructedType))
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
        .contributionType(ContributionType.fromBindingMethod(method))
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
    if (componentDescriptor.kind().equals(PRODUCTION_COMPONENT)
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
   * {@code @BindsInstance}-annotated builder method.
   */
  ProvisionBinding boundInstanceBinding(BuilderRequirementMethod bindsInstanceMethod) {
    checkArgument(bindsInstanceMethod.method().getKind().equals(METHOD));
    checkArgument(bindsInstanceMethod.method().getParameters().size() == 1);
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(bindsInstanceMethod.method())
        .key(bindsInstanceMethod.requirement().key().get())
        .nullableType(getNullableType(getOnlyElement(bindsInstanceMethod.method().getParameters())))
        .kind(BOUND_INSTANCE)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_BUILDER} binding declared by a component
   * method that returns a subcomponent builder. Use {{@link
   * #subcomponentBuilderBinding(ImmutableSet)}} for bindings declared using {@link
   * Module#subcomponents()}.
   *
   * @param component the component that declares or inherits the method
   */
  ProvisionBinding subcomponentBuilderBinding(
      ExecutableElement subcomponentBuilderMethod, TypeElement component) {
    checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
    checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
    Key key =
        keyFactory.forSubcomponentBuilderMethod(
            subcomponentBuilderMethod, asDeclared(component.asType()));
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .bindingElement(subcomponentBuilderMethod)
        .key(key)
        .kind(SUBCOMPONENT_BUILDER)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#SUBCOMPONENT_BUILDER} binding declared using {@link
   * Module#subcomponents()}.
   */
  ProvisionBinding subcomponentBuilderBinding(
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
    SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .key(subcomponentDeclaration.key())
        .kind(SUBCOMPONENT_BUILDER)
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
   * Returns a {@link dagger.model.BindingKind#RELEASABLE_REFERENCE_MANAGER} binding for a {@code
   * ReleasableReferenceManager}.
   */
  ProvisionBinding releasableReferenceManagerBinding(Scope scope) {
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .key(keyFactory.forReleasableReferenceManager(scope))
        .kind(RELEASABLE_REFERENCE_MANAGER)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#RELEASABLE_REFERENCE_MANAGER} binding for a {@code
   * TypedReleasableReferenceManager<M>}.
   */
  ProvisionBinding typedReleasableReferenceManagerBinding(Scope scope, DeclaredType metadataType) {
    return releasableReferenceManagerBinding(scope)
        .toBuilder()
        .key(keyFactory.forTypedReleasableReferenceManager(scope, metadataType))
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#RELEASABLE_REFERENCE_MANAGERS} binding for a set of
   * {@code ReleasableReferenceManager}s.
   */
  ProvisionBinding setOfReleasableReferenceManagersBinding() {
    return ProvisionBinding.builder()
        .contributionType(ContributionType.UNIQUE)
        .key(keyFactory.forSetOfReleasableReferenceManagers())
        .kind(RELEASABLE_REFERENCE_MANAGERS)
        .build();
  }

  /**
   * Returns a {@link dagger.model.BindingKind#RELEASABLE_REFERENCE_MANAGERS} binding for a set of
   * {@code TypedReleasableReferenceManager<M>}s.
   */
  ProvisionBinding setOfTypedReleasableReferenceManagersBinding(DeclaredType metadataType) {
    return setOfReleasableReferenceManagersBinding()
        .toBuilder()
        .key(keyFactory.forSetOfTypedReleasableReferenceManagers(metadataType))
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
    ImmutableSortedSet<InjectionSite> injectionSites = getInjectionSites(declaredType);
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

  private final ElementVisitor<Optional<InjectionSite>, DeclaredType> injectionSiteVisitor =
      new ElementKindVisitor6<Optional<InjectionSite>, DeclaredType>(Optional.empty()) {
        @Override
        public Optional<InjectionSite> visitExecutableAsMethod(
            ExecutableElement e, DeclaredType type) {
          return Optional.of(injectionSiteForInjectMethod(e, type));
        }

        @Override
        public Optional<InjectionSite> visitVariableAsField(VariableElement e, DeclaredType type) {
          return (isAnnotationPresent(e, Inject.class)
                  && !e.getModifiers().contains(PRIVATE)
                  && !e.getModifiers().contains(STATIC))
              ? Optional.of(injectionSiteForInjectField(e, type))
              : Optional.empty();
        }
      };

  private ImmutableSortedSet<InjectionSite> getInjectionSites(DeclaredType declaredType) {
    Set<InjectionSite> injectionSites = new HashSet<>();
    List<TypeElement> ancestors = new ArrayList<>();
    SetMultimap<String, ExecutableElement> overriddenMethodMap = LinkedHashMultimap.create();
    for (Optional<DeclaredType> currentType = Optional.of(declaredType);
        currentType.isPresent();
        currentType = types.nonObjectSuperclass(currentType.get())) {
      DeclaredType type = currentType.get();
      ancestors.add(MoreElements.asType(type.asElement()));
      for (Element enclosedElement : type.asElement().getEnclosedElements()) {
        Optional<InjectionSite> maybeInjectionSite =
            injectionSiteVisitor.visit(enclosedElement, type);
        if (maybeInjectionSite.isPresent()) {
          InjectionSite injectionSite = maybeInjectionSite.get();
          if (shouldBeInjected(injectionSite.element(), overriddenMethodMap)) {
            injectionSites.add(injectionSite);
          }
          if (injectionSite.kind().equals(InjectionSite.Kind.METHOD)) {
            ExecutableElement injectionSiteMethod =
                MoreElements.asExecutable(injectionSite.element());
            overriddenMethodMap.put(
                injectionSiteMethod.getSimpleName().toString(), injectionSiteMethod);
          }
        }
      }
    }
    return ImmutableSortedSet.copyOf(
        // supertypes before subtypes
        Comparator.comparing(
                (InjectionSite injectionSite) ->
                    ancestors.indexOf(injectionSite.element().getEnclosingElement()))
            .reversed()
            // fields before methods
            .thenComparing(injectionSite -> injectionSite.element().getKind())
            // then sort by whichever element comes first in the parent
            // this isn't necessary, but makes the processor nice and predictable
            .thenComparing(InjectionSite::indexAmongSiblingMembers),
        injectionSites);
  }

  private boolean shouldBeInjected(
      Element injectionSite, SetMultimap<String, ExecutableElement> overriddenMethodMap) {
    if (!isAnnotationPresent(injectionSite, Inject.class)
        || injectionSite.getModifiers().contains(PRIVATE)
        || injectionSite.getModifiers().contains(STATIC)) {
      return false;
    }

    if (injectionSite.getKind().isField()) { // Inject all fields (self and ancestors)
      return true;
    }

    // For each method with the same name belonging to any descendant class, return false if any
    // method has already overridden the injectionSite method. To decrease the number of methods
    // that are checked, we store the already injected methods in a SetMultimap and only
    // check the methods with the same name.
    ExecutableElement injectionSiteMethod = MoreElements.asExecutable(injectionSite);
    TypeElement injectionSiteType = MoreElements.asType(injectionSite.getEnclosingElement());
    for (ExecutableElement method :
        overriddenMethodMap.get(injectionSiteMethod.getSimpleName().toString())) {
      if (elements.overrides(method, injectionSiteMethod, injectionSiteType)) {
        return false;
      }
    }
    return true;
  }

  private InjectionSite injectionSiteForInjectMethod(
      ExecutableElement methodElement, DeclaredType containingType) {
    checkNotNull(methodElement);
    checkArgument(methodElement.getKind().equals(ElementKind.METHOD));
    ExecutableType resolved =
        MoreTypes.asExecutable(types.asMemberOf(containingType, methodElement));
    return new AutoValue_MembersInjectionBinding_InjectionSite(
        InjectionSite.Kind.METHOD,
        methodElement,
        dependencyRequestFactory.forRequiredResolvedVariables(
            methodElement.getParameters(), resolved.getParameterTypes()));
  }

  private InjectionSite injectionSiteForInjectField(
      VariableElement fieldElement, DeclaredType containingType) {
    checkNotNull(fieldElement);
    checkArgument(fieldElement.getKind().equals(ElementKind.FIELD));
    checkArgument(isAnnotationPresent(fieldElement, Inject.class));
    TypeMirror resolved = types.asMemberOf(containingType, fieldElement);
    return new AutoValue_MembersInjectionBinding_InjectionSite(
        InjectionSite.Kind.FIELD,
        fieldElement,
        ImmutableSet.of(
            dependencyRequestFactory.forRequiredResolvedVariable(fieldElement, resolved)));
  }
}
