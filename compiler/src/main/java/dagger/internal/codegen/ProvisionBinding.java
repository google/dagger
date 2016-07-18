/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Internal;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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
  public BindingType bindingType() {
    return BindingType.PROVISION;
  }

  @Override
  abstract Optional<ProvisionBinding> unresolved();

  @Override
  abstract Optional<Scope> scope();

  abstract boolean internal();

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
     * will return a resolved binding, with the key & type resolved to the given type (using
     * {@link Types#asMemberOf(DeclaredType, Element)}).
     */
    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement,
        Optional<TypeMirror> resolvedType) {
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
      Optional<Scope> scope = Scope.uniqueScopeOf(constructorElement.getEnclosingElement());

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());

      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          constructorElement,
          Optional.<TypeElement>absent(),
          key,
          dependencies,
          Optional.<DeclaredType>absent(),
          membersInjectionRequest,
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.INJECTION,
          hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types)
              ? Optional.of(forInjectConstructor(constructorElement, Optional.<TypeMirror>absent()))
              : Optional.<ProvisionBinding>absent(),
          scope,
          false);
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
      return Optional.absent();
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
      Optional<Scope> scope = Scope.uniqueScopeOf(providesMethod);

      boolean internal = providesMethod.getAnnotation(Internal.class) != null;

      return new AutoValue_ProvisionBinding(
          ContributionType.fromBindingMethod(providesMethod),
          providesMethod,
          Optional.of(contributedBy),
          key,
          dependencies,
          ConfigurationAnnotations.getNullableType(providesMethod),
          Optional.<DependencyRequest>absent(),
          wrapOptionalInEquivalence(getMapKey(providesMethod)),
          Kind.PROVISION,
          Optional.<ProvisionBinding>absent(),
          scope,
          internal);
    }

    /**
     * A synthetic binding of {@code Map<K, V>} that depends on {@code Map<K, Provider<V>>}.
     */
    ProvisionBinding syntheticMapOfValuesBinding(DependencyRequest requestForMapOfValues) {
      checkNotNull(requestForMapOfValues);
      Optional<Key> mapOfProvidersKey =
          keyFactory.implicitMapProviderKeyFrom(requestForMapOfValues.key());
      checkArgument(
          mapOfProvidersKey.isPresent(),
          "%s is not a request for Map<K, V>",
          requestForMapOfValues);
      DependencyRequest requestForMapOfProviders =
          dependencyRequestFactory.forImplicitMapBinding(
              requestForMapOfValues, mapOfProvidersKey.get());
      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          requestForMapOfProviders.requestElement(),
          Optional.<TypeElement>absent(),
          requestForMapOfValues.key(),
          ImmutableSet.of(requestForMapOfProviders),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          wrapOptionalInEquivalence(getMapKey(requestForMapOfProviders.requestElement())),
          Kind.SYNTHETIC_MAP,
          Optional.<ProvisionBinding>absent(),
          Scope.uniqueScopeOf(requestForMapOfProviders.requestElement()),
          false);
    }

    /**
     * A synthetic binding that depends explicitly on a set of individual provision multibinding
     * contribution methods.
     * 
     * <p>Note that these could be set multibindings or map multibindings.
     */
    ProvisionBinding syntheticMultibinding(
        DependencyRequest request, Iterable<ContributionBinding> multibindingContributions) {
      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          request.requestElement(),
          Optional.<TypeElement>absent(),
          request.key(),
          dependencyRequestFactory.forMultibindingContributions(request, multibindingContributions),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.forMultibindingRequest(request),
          Optional.<ProvisionBinding>absent(),
          Scope.uniqueScopeOf(request.requestElement()),
          false);
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          componentDefinitionType,
          Optional.<TypeElement>absent(),
          keyFactory.forComponent(componentDefinitionType.asType()),
          ImmutableSet.<DependencyRequest>of(),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.COMPONENT,
          Optional.<ProvisionBinding>absent(),
          Optional.<Scope>absent(),
          false);
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      Optional<Scope> scope = Scope.uniqueScopeOf(componentMethod);
      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          componentMethod,
          Optional.<TypeElement>absent(),
          keyFactory.forComponentMethod(componentMethod),
          ImmutableSet.<DependencyRequest>of(),
          ConfigurationAnnotations.getNullableType(componentMethod),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.COMPONENT_PROVISION,
          Optional.<ProvisionBinding>absent(),
          scope,
          false);
    }

    ProvisionBinding forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, TypeElement contributedBy) {
      checkNotNull(subcomponentBuilderMethod);
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      checkArgument(subcomponentBuilderMethod.getParameters().isEmpty());
      DeclaredType declaredContainer = asDeclared(contributedBy.asType());
      return new AutoValue_ProvisionBinding(
          ContributionType.UNIQUE,
          subcomponentBuilderMethod,
          Optional.<TypeElement>absent(),
          keyFactory.forSubcomponentBuilderMethod(subcomponentBuilderMethod, declaredContainer),
          ImmutableSet.<DependencyRequest>of(),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.SUBCOMPONENT_BUILDER,
          Optional.<ProvisionBinding>absent(),
          Optional.<Scope>absent(),
          false);
    }

    ProvisionBinding delegate(
        DelegateDeclaration delegateDeclaration, ProvisionBinding delegate) {
      Key key = keyFactory.forDelegateBinding(delegateDeclaration, Provider.class);
      return new AutoValue_ProvisionBinding(
          delegateDeclaration.contributionType(),
          delegateDeclaration.bindingElement(),
          delegateDeclaration.contributingModule(),
          key,
          ImmutableSet.of(delegateDeclaration.delegateRequest()),
          delegate.nullableType(),
          Optional.<DependencyRequest>absent(),
          delegateDeclaration.wrappedMapKey(),
          Kind.SYNTHETIC_DELEGATE_BINDING,
          Optional.<ProvisionBinding>absent(),
          Scope.uniqueScopeOf(delegateDeclaration.bindingElement()),
          delegate.internal());
    }
  }
}
