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
import static dagger.internal.codegen.Util.toImmutableSet;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
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
  @Memoized
  ImmutableSet<DependencyRequest> explicitDependencies() {
    return ImmutableSet.<DependencyRequest>builder()
        .addAll(provisionDependencies())
        .addAll(membersInjectionDependencies())
        .build();
  }

  /**
   * Dependencies necessary to invoke an {@code @Inject} constructor or {@code @Provides} method.
   */
  abstract ImmutableSet<DependencyRequest> provisionDependencies();

  @Memoized
  ImmutableSet<DependencyRequest> membersInjectionDependencies() {
    return injectionSites()
        .stream()
        .flatMap(i -> i.dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * {@link InjectionSite}s for all {@code @Inject} members if {@link #bindingKind()} is {@link
   * ContributionBinding.Kind#INJECTION}, otherwise empty.
   */
  abstract ImmutableSortedSet<InjectionSite> injectionSites();

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
        .provisionDependencies(ImmutableSet.of())
        .injectionSites(ImmutableSortedSet.of());
  }
  
  abstract Builder toBuilder();

  boolean shouldCheckForNull(CompilerOptions compilerOptions) {
    return !providesPrimitiveType()
        && !nullableType().isPresent()
        && compilerOptions.doCheckForNulls();
  }

  private boolean providesPrimitiveType() {
    return bindingElement().isPresent()
        && MoreElements.asExecutable(bindingElement().get())
            .getReturnType()
            .getKind()
            .isPrimitive();
  }

  @AutoValue.Builder
  @CanIgnoreReturnValue
  abstract static class Builder extends ContributionBinding.Builder<Builder> {
    abstract Builder provisionDependencies(DependencyRequest... provisionDependencies);
    abstract Builder provisionDependencies(ImmutableSet<DependencyRequest> provisionDependencies);

    abstract Builder injectionSites(ImmutableSortedSet<InjectionSite> injectionSites);

    abstract Builder unresolved(ProvisionBinding unresolved);

    abstract Builder scope(Optional<Scope> scope);

    @CheckReturnValue
    abstract ProvisionBinding build();
  }

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final MembersInjectionBinding.Factory membersInjectionBindingFactory;

    Factory(
        Types types,
        Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory,
        MembersInjectionBinding.Factory membersInjectionBindingFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.membersInjectionBindingFactory = membersInjectionBindingFactory;
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
      ImmutableSet<DependencyRequest> provisionDependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              constructorElement.getParameters(), cxtorType.getParameterTypes());
      // TODO(ronshapiro): instead of creating a MembersInjectionBinding just to retrieve the
      // injection sites, create an InjectionSite.Factory and pass that in here.
      ImmutableSortedSet<InjectionSite> injectionSites =
          membersInjectionBindingFactory
              .forInjectedType(enclosingCxtorType, Optional.empty())
              .injectionSites();

      ProvisionBinding.Builder builder =
          ProvisionBinding.builder()
              .contributionType(ContributionType.UNIQUE)
              .bindingElement(constructorElement)
              .key(key)
              .provisionDependencies(provisionDependencies)
              .injectionSites(injectionSites)
              .bindingKind(Kind.INJECTION)
              .scope(Scope.uniqueScopeOf(constructorElement.getEnclosingElement()));

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());
      if (hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)) {
        builder.unresolved(forInjectConstructor(constructorElement, Optional.empty()));
      }
      return builder.build();
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
          .provisionDependencies(dependencies)
          .nullableType(ConfigurationAnnotations.getNullableType(providesMethod))
          .wrappedMapKey(wrapOptionalInEquivalence(getMapKey(providesMethod)))
          .bindingKind(Kind.PROVISION)
          .scope(Scope.uniqueScopeOf(providesMethod))
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
          .provisionDependencies(
              dependencyRequestFactory.forMultibindingContributions(key, multibindingContributions))
          .bindingKind(Kind.forMultibindingKey(key))
          .build();
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentDefinitionType)
          .key(keyFactory.forType(componentDefinitionType.asType()))
          .bindingKind(Kind.COMPONENT)
          .build();
    }

    ProvisionBinding forComponentDependency(TypeElement dependencyType) {
      checkNotNull(dependencyType);
      return ProvisionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(dependencyType)
          .key(keyFactory.forType(dependencyType.asType()))
          .bindingKind(Kind.COMPONENT_DEPENDENCY)
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
          .provisionDependencies(delegateDeclaration.delegateRequest())
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
    ProvisionBinding provideTypedReleasableReferenceManager(
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
    ProvisionBinding provideSetOfTypedReleasableReferenceManagers(DeclaredType metadataType) {
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
    ProvisionBinding syntheticPresentBinding(Key key, DependencyRequest.Kind kind) {
      return syntheticAbsentBinding(key)
          .toBuilder()
          .provisionDependencies(
              dependencyRequestFactory.forSyntheticPresentOptionalBinding(key, kind))
          .build();
    }
  }
}
