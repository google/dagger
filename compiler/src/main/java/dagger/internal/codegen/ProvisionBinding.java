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
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends ContributionBinding {

  @Override
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return membersInjectionRequest().isPresent()
        ? ImmutableSet.of(membersInjectionRequest().get())
        : ImmutableSet.of();
  }

  /** If this provision requires members injection, this will be the corresponding request. */
  abstract Optional<DependencyRequest> membersInjectionRequest();

  @Override
  public BindingType bindingType() {
    return BindingType.PROVISION;
  }

  @Override
  abstract Optional<ProvisionBinding> unresolved();

  @Override
  abstract Optional<Scope> scope();

  private static Builder builder() {
    return new AutoValue_ProvisionBinding.Builder()
        .explicitDependencies(ImmutableSet.<DependencyRequest>of());
  }
  
  abstract Builder toBuilder();

  @AutoValue.Builder
  @CanIgnoreReturnValue
  abstract static class Builder extends ContributionBinding.Builder<Builder> {

    abstract Builder membersInjectionRequest(Optional<DependencyRequest> membersInjectionRequest);

    abstract Builder unresolved(ProvisionBinding unresolved);

    abstract Builder scope(Optional<Scope> scope);

    @CheckReturnValue
    abstract ProvisionBinding build();
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    /**
     * Returns a ProvisionBinding for the given element. If {@code resolvedType} is present, this
     * will return a resolved binding, with the key and type resolved to the given type (using
     * {@link Types#asMemberOf(DeclaredType, Element)}).
     */
    ProvisionBinding forInjectConstructor(
        ExecutableElement constructorElement, Optional<TypeMirror> resolvedType) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(isAnnotationPresent(constructorElement, Inject.class));
      checkArgument(!getQualifier(constructorElement).isPresent());

      ExecutableType cxtorType = MoreTypes.asExecutable(constructorElement.asType());
      DeclaredType enclosingCxtorType =
          MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
      // If the class this is constructing has some type arguments, resolve everything.
      if (!enclosingCxtorType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
        DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
        // Validate that we're resolving from the correct type.
        checkState(types.isSameType(types.erasure(resolved), types.erasure(enclosingCxtorType)),
            "erased expected type: %s, erased actual type: %s",
            types.erasure(resolved), types.erasure(enclosingCxtorType));
        cxtorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
        enclosingCxtorType = resolved;
      }

      Key key = keyFactory.forInjectConstructorWithResolvedType(enclosingCxtorType);
      checkArgument(!key.qualifier().isPresent());
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              constructorElement.getParameters(), cxtorType.getParameterTypes());
      Optional<DependencyRequest> membersInjectionRequest =
          membersInjectionRequest(enclosingCxtorType);

      ProvisionBinding.Builder builder =
          ProvisionBinding.builder()
              .contributionType(ContributionType.UNIQUE)
              .bindingElement(constructorElement)
              .key(key)
              .explicitDependencies(dependencies)
              .membersInjectionRequest(membersInjectionRequest)
              .bindingKind(Kind.INJECTION)
              .scope(Scope.uniqueScopeOf(constructorElement.getEnclosingElement()));

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());
      if (hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)) {
        builder.unresolved(forInjectConstructor(constructorElement, Optional.empty()));
      }
      return builder.build();
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(DeclaredType type) {
      TypeElement typeElement = MoreElements.asType(type.asElement());
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          typeElement.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
      }
      for (Element enclosedElement : typeElement.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
        }
      }
      return Optional.empty();
    }

    ProvisionBinding forProvidesMethod(
        ExecutableElement providesMethod, TypeElement contributedBy) {
      checkArgument(providesMethod.getKind().equals(METHOD));
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), providesMethod));
      Key key = keyFactory.forProvidesMethod(providesMethod, contributedBy);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              providesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.fromBindingMethod(providesMethod))
          .bindingElement(providesMethod)
          .contributingModule(contributedBy)
          .key(key)
          .explicitDependencies(dependencies)
          .nullableType(ConfigurationAnnotations.getNullableType(providesMethod))
          .wrappedMapKey(wrapOptionalInEquivalence(getMapKey(providesMethod)))
          .bindingKind(Kind.PROVISION)
          .scope(Scope.uniqueScopeOf(providesMethod))
          .build();
    }

    /** A synthetic binding of {@code Map<K, V>} that depends on {@code Map<K, Provider<V>>}. */
    ProvisionBinding syntheticMapOfValuesBinding(Key mapOfValuesKey) {
      checkNotNull(mapOfValuesKey);
      Optional<Key> mapOfProvidersKey = keyFactory.implicitMapProviderKeyFrom(mapOfValuesKey);
      checkArgument(mapOfProvidersKey.isPresent(), "%s is not a key for Map<K, V>", mapOfValuesKey);
      DependencyRequest requestForMapOfProviders =
          dependencyRequestFactory.forImplicitMapBinding(mapOfProvidersKey.get());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(mapOfValuesKey)
          .explicitDependencies(requestForMapOfProviders)
          .bindingKind(Kind.SYNTHETIC_MAP)
          .build();
    }

    /**
     * A synthetic binding that depends explicitly on a set of individual provision multibinding
     * contribution methods.
     *
     * <p>Note that these could be set multibindings or map multibindings.
     */
    ProvisionBinding syntheticMultibinding(
        Key key, Iterable<ContributionBinding> multibindingContributions) {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(key)
          .explicitDependencies(
              dependencyRequestFactory.forMultibindingContributions(multibindingContributions))
          .bindingKind(Kind.forMultibindingKey(key))
          .build();
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentDefinitionType)
          .key(keyFactory.forComponent(componentDefinitionType.asType()))
          .bindingKind(Kind.COMPONENT)
          .build();
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentMethod)
          .key(keyFactory.forComponentMethod(componentMethod))
          .nullableType(ConfigurationAnnotations.getNullableType(componentMethod))
          .bindingKind(Kind.COMPONENT_PROVISION)
          .scope(Scope.uniqueScopeOf(componentMethod))
          .build();
    }

    ProvisionBinding forBuilderBinding(BuilderRequirementMethod method) {
      ExecutableElement builderMethod = method.method();

      checkNotNull(builderMethod);
      checkArgument(builderMethod.getKind().equals(METHOD));
      checkArgument(builderMethod.getParameters().size() == 1);
      VariableElement parameterElement = Iterables.getOnlyElement(builderMethod.getParameters());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(builderMethod)
          .key(method.requirement().key().get())
          .nullableType(ConfigurationAnnotations.getNullableType(parameterElement))
          .bindingKind(Kind.BUILDER_BINDING)
          .build();
    }

    ProvisionBinding forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, TypeElement contributedBy) {
      checkNotNull(subcomponentBuilderMethod);
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
      DeclaredType declaredContainer = asDeclared(contributedBy.asType());
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(subcomponentBuilderMethod)
          .key(
              keyFactory.forSubcomponentBuilderMethod(subcomponentBuilderMethod, declaredContainer))
          .bindingKind(Kind.SUBCOMPONENT_BUILDER)
          .build();
    }

    ProvisionBinding syntheticSubcomponentBuilder(
        ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
      SubcomponentDeclaration subcomponentDeclaration = subcomponentDeclarations.iterator().next();
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(subcomponentDeclaration.key())
          .bindingKind(Kind.SUBCOMPONENT_BUILDER)
          .build();
    }

    ProvisionBinding delegate(
        DelegateDeclaration delegateDeclaration, ProvisionBinding delegate) {
      return delegateBuilder(delegateDeclaration).nullableType(delegate.nullableType()).build();
    }

    /**
     * A form of {@link #delegate(DelegateDeclaration, ProvisionBinding)} when the right-hand-side
     * of a {@link dagger.Binds} method cannot be resolved.
     */
    ProvisionBinding missingDelegate(DelegateDeclaration delegateDeclaration) {
      return delegateBuilder(delegateDeclaration).build();
    }

    private ProvisionBinding.Builder delegateBuilder(DelegateDeclaration delegateDeclaration) {
      return ProvisionBinding.builder()
          .contributionType(delegateDeclaration.contributionType())
          .bindingElement(delegateDeclaration.bindingElement().get())
          .contributingModule(delegateDeclaration.contributingModule().get())
          .key(keyFactory.forDelegateBinding(delegateDeclaration, Provider.class))
          .explicitDependencies(delegateDeclaration.delegateRequest())
          .wrappedMapKey(delegateDeclaration.wrappedMapKey())
          .bindingKind(Kind.SYNTHETIC_DELEGATE_BINDING)
          .scope(Scope.uniqueScopeOf(delegateDeclaration.bindingElement().get()));
    }

    /**
     * Returns a synthetic binding for a {@code @ForReleasableReferences(scope)
     * ReleasableReferenceManager} that provides the component-instantiated object.
     */
    ProvisionBinding provideReleasableReferenceManager(Scope scope) {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(keyFactory.forReleasableReferenceManager(scope))
          .bindingKind(Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGER)
          .build();
    }

    /**
     * Returns a synthetic binding for a {@code @ForReleasableReferences(scope)
     * TypedReleasableReferenceManager<metadataType>} that provides the component-instantiated
     * object.
     */
    ContributionBinding provideTypedReleasableReferenceManager(
        Scope scope, DeclaredType metadataType) {
      return provideReleasableReferenceManager(scope)
          .toBuilder()
          .key(keyFactory.forTypedReleasableReferenceManager(scope, metadataType))
          .build();
    }

    /** Returns a synthetic binding for {@code Set<ReleasableReferenceManager>}. */
    ProvisionBinding provideSetOfReleasableReferenceManagers() {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(keyFactory.forSetOfReleasableReferenceManagers())
          .bindingKind(Kind.SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS)
          .build();
    }

    /**
     * Returns a synthetic binding for {@code Set<TypedReleasableReferenceManager<metadataType>}.
     */
    ContributionBinding provideSetOfTypedReleasableReferenceManagers(DeclaredType metadataType) {
      return provideSetOfReleasableReferenceManagers()
          .toBuilder()
          .key(keyFactory.forSetOfTypedReleasableReferenceManagers(metadataType))
          .build();
    }

    /**
     * Returns a synthetic binding for an {@linkplain dagger.BindsOptionalOf optional binding} in a
     * component with no binding for the underlying key.
     */
    ProvisionBinding syntheticAbsentBinding(Key key) {
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(key)
          .bindingKind(Kind.SYNTHETIC_OPTIONAL_BINDING)
          .build();
    }

    /**
     * Returns a synthetic binding for an {@linkplain dagger.BindsOptionalOf optional binding} in a
     * component with a binding for the underlying key.
     */
    ProvisionBinding syntheticPresentBinding(Key key) {
      return syntheticAbsentBinding(key)
          .toBuilder()
          .explicitDependencies(
              dependencyRequestFactory.forSyntheticPresentOptionalBinding(
                  key, DependencyRequest.Kind.PROVIDER))
          .build();
    }
  }
}
