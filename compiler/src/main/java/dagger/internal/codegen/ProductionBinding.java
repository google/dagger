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

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.METHOD;

/**
 * A value object representing the mechanism by which a {@link Key} can be produced. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Jesse Beder
 * @since 2.0
 */
@AutoValue
abstract class ProductionBinding extends ContributionBinding {

  @Override
  public BindingType bindingType() {
    return BindingType.PRODUCTION;
  }

  @Override
  Optional<ProductionBinding> unresolved() {
    return Optional.absent();
  }

  @Override
  Set<DependencyRequest> implicitDependencies() {
    // Similar optimizations to ContributionBinding.implicitDependencies().
    if (!executorRequest().isPresent() && !monitorRequest().isPresent()) {
      return super.implicitDependencies();
    } else {
      return Sets.union(
          Sets.union(executorRequest().asSet(), monitorRequest().asSet()),
          super.implicitDependencies());
    }
  }

  /** Returns the list of types in the throws clause of the method. */
  abstract ImmutableList<? extends TypeMirror> thrownTypes();

  /** If this production requires an executor, this will be the corresponding request. */
  abstract Optional<DependencyRequest> executorRequest();

  /** If this production requires a monitor, this will be the corresponding request. */
  abstract Optional<DependencyRequest> monitorRequest();

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(
        Types types, Key.Factory keyFactory, DependencyRequest.Factory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProductionBinding forProducesMethod(
        ExecutableElement producesMethod, TypeElement contributedBy) {
      checkArgument(producesMethod.getKind().equals(METHOD));
      Key key = keyFactory.forProducesMethod(producesMethod, contributedBy);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), producesMethod));
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              producesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      DependencyRequest executorRequest =
          dependencyRequestFactory.forProductionImplementationExecutor();
      DependencyRequest monitorRequest =
          dependencyRequestFactory.forProductionComponentMonitorProvider();
      Kind kind = MoreTypes.isTypeOf(ListenableFuture.class, producesMethod.getReturnType())
          ? Kind.FUTURE_PRODUCTION
          : Kind.IMMEDIATE;
      return new AutoValue_ProductionBinding(
          ContributionType.fromBindingMethod(producesMethod),
          producesMethod,
          Optional.of(contributedBy),
          key,
          dependencies,
          findBindingPackage(key),
          Optional.<DeclaredType>absent(), // TODO(beder): Add nullability checking with Java 8.
          Optional.<DependencyRequest>absent(),
          wrapOptionalInEquivalence(getMapKey(producesMethod)),
          kind,
          ImmutableList.copyOf(producesMethod.getThrownTypes()),
          Optional.of(executorRequest),
          Optional.of(monitorRequest));
    }

    /**
     * A synthetic binding of {@code Map<K, V>} or {@code Map<K, Produced<V>>} that depends on
     * {@code Map<K, Producer<V>>}.
     */
    ProductionBinding syntheticMapOfValuesOrProducedBinding(
        DependencyRequest requestForMapOfValuesOrProduced) {
      checkNotNull(requestForMapOfValuesOrProduced);
      Optional<Key> mapOfProducersKey =
          keyFactory.implicitMapProducerKeyFrom(requestForMapOfValuesOrProduced.key());
      checkArgument(
          mapOfProducersKey.isPresent(),
          "%s is not for a Map<K, V>",
          requestForMapOfValuesOrProduced);
      DependencyRequest requestForMapOfProducers =
          dependencyRequestFactory.forImplicitMapBinding(
              requestForMapOfValuesOrProduced, mapOfProducersKey.get());
      return new AutoValue_ProductionBinding(
          ContributionType.UNIQUE,
          requestForMapOfProducers.requestElement(),
          Optional.<TypeElement>absent(),
          requestForMapOfValuesOrProduced.key(),
          ImmutableSet.of(requestForMapOfProducers),
          findBindingPackage(requestForMapOfValuesOrProduced.key()),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          wrapOptionalInEquivalence(getMapKey(requestForMapOfProducers.requestElement())),
          Kind.SYNTHETIC_MAP,
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }

    /**
     * A synthetic binding that depends explicitly on a set of individual provision or production
     * multibinding contribution methods.
     * 
     * <p>Note that these could be set multibindings or map multibindings.
     */
    ProductionBinding syntheticMultibinding(
        final DependencyRequest request, Iterable<ContributionBinding> multibindingContributions) {
      return new AutoValue_ProductionBinding(
          ContributionType.UNIQUE,
          request.requestElement(),
          Optional.<TypeElement>absent(),
          request.key(),
          dependencyRequestFactory.forMultibindingContributions(request, multibindingContributions),
          findBindingPackage(request.key()),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.forMultibindingRequest(request),
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProductionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      checkArgument(MoreTypes.isTypeOf(ListenableFuture.class, componentMethod.getReturnType()));
      return new AutoValue_ProductionBinding(
          ContributionType.UNIQUE,
          componentMethod,
          Optional.<TypeElement>absent(),
          keyFactory.forProductionComponentMethod(componentMethod),
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.COMPONENT_PRODUCTION,
          ImmutableList.copyOf(componentMethod.getThrownTypes()),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProductionBinding delegate(
        DelegateDeclaration delegateDeclaration, ProductionBinding delegateBinding) {
      return new AutoValue_ProductionBinding(
          delegateBinding.contributionType(),
          delegateDeclaration.bindingElement(),
          delegateDeclaration.contributingModule(),
          delegateDeclaration.key(),
          ImmutableSet.of(delegateDeclaration.delegateRequest()),
          findBindingPackage(delegateDeclaration.key()),
          delegateBinding.nullableType(),
          Optional.<DependencyRequest>absent(),
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Kind.SYNTHETIC_DELEGATE_BINDING,
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }
  }
}
