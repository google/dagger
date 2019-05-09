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

import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.RequestKinds.extractKeyType;
import static dagger.internal.codegen.RequestKinds.frameworkClass;
import static dagger.internal.codegen.RequestKinds.getRequestKind;
import static dagger.model.RequestKind.FUTURE;
import static dagger.model.RequestKind.INSTANCE;
import static dagger.model.RequestKind.MEMBERS_INJECTION;
import static dagger.model.RequestKind.PRODUCER;
import static dagger.model.RequestKind.PROVIDER;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Lazy;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * Factory for {@link DependencyRequest}s.
 *
 * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available, which
 * may mean that the type will be generated in a later round of processing.
 */
final class DependencyRequestFactory {
  private final KeyFactory keyFactory;
  private final DaggerTypes types;

  @Inject
  DependencyRequestFactory(KeyFactory keyFactory, DaggerTypes types) {
    this.keyFactory = keyFactory;
    this.types = types;
  }

  ImmutableSet<DependencyRequest> forRequiredResolvedVariables(
      List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
    checkState(resolvedTypes.size() == variables.size());
    ImmutableSet.Builder<DependencyRequest> builder = ImmutableSet.builder();
    for (int i = 0; i < variables.size(); i++) {
      builder.add(forRequiredResolvedVariable(variables.get(i), resolvedTypes.get(i)));
    }
    return builder.build();
  }

  /**
   * Creates synthetic dependency requests for each individual multibinding contribution in {@code
   * multibindingContributions}.
   */
  ImmutableSet<DependencyRequest> forMultibindingContributions(
      Key multibindingKey, Iterable<ContributionBinding> multibindingContributions) {
    ImmutableSet.Builder<DependencyRequest> requests = ImmutableSet.builder();
    for (ContributionBinding multibindingContribution : multibindingContributions) {
      requests.add(forMultibindingContribution(multibindingKey, multibindingContribution));
    }
    return requests.build();
  }

  /** Creates a synthetic dependency request for one individual {@code multibindingContribution}. */
  private DependencyRequest forMultibindingContribution(
      Key multibindingKey, ContributionBinding multibindingContribution) {
    checkArgument(
        multibindingContribution.key().multibindingContributionIdentifier().isPresent(),
        "multibindingContribution's key must have a multibinding contribution identifier: %s",
        multibindingContribution);
    return DependencyRequest.builder()
        .kind(multibindingContributionRequestKind(multibindingKey, multibindingContribution))
        .key(multibindingContribution.key())
        .build();
  }

  // TODO(b/28555349): support PROVIDER_OF_LAZY here too
  private static final ImmutableSet<RequestKind> WRAPPING_MAP_VALUE_FRAMEWORK_TYPES =
      ImmutableSet.of(PROVIDER, PRODUCER);

  private RequestKind multibindingContributionRequestKind(
      Key multibindingKey, ContributionBinding multibindingContribution) {
    switch (multibindingContribution.contributionType()) {
      case MAP:
        MapType mapType = MapType.from(multibindingKey);
        for (RequestKind kind : WRAPPING_MAP_VALUE_FRAMEWORK_TYPES) {
          if (mapType.valuesAreTypeOf(frameworkClass(kind))) {
            return kind;
          }
        }
        // fall through
      case SET:
      case SET_VALUES:
        return INSTANCE;
      case UNIQUE:
        throw new IllegalArgumentException(
            "multibindingContribution must be a multibinding: " + multibindingContribution);
    }
    throw new AssertionError(multibindingContribution.toString());
  }

  DependencyRequest forRequiredResolvedVariable(
      VariableElement variableElement, TypeMirror resolvedType) {
    checkNotNull(variableElement);
    checkNotNull(resolvedType);
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
    return newDependencyRequest(variableElement, resolvedType, qualifier);
  }

  DependencyRequest forComponentProvisionMethod(
      ExecutableElement provisionMethod, ExecutableType provisionMethodType) {
    checkNotNull(provisionMethod);
    checkNotNull(provisionMethodType);
    checkArgument(
        provisionMethod.getParameters().isEmpty(),
        "Component provision methods must be empty: %s",
        provisionMethod);
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
    return newDependencyRequest(provisionMethod, provisionMethodType.getReturnType(), qualifier);
  }

  DependencyRequest forComponentProductionMethod(
      ExecutableElement productionMethod, ExecutableType productionMethodType) {
    checkNotNull(productionMethod);
    checkNotNull(productionMethodType);
    checkArgument(
        productionMethod.getParameters().isEmpty(),
        "Component production methods must be empty: %s",
        productionMethod);
    TypeMirror type = productionMethodType.getReturnType();
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(productionMethod);
    // Only a component production method can be a request for a ListenableFuture, so we
    // special-case it here.
    if (isTypeOf(ListenableFuture.class, type)) {
      return DependencyRequest.builder()
          .kind(FUTURE)
          .key(keyFactory.forQualifiedType(qualifier, types.unwrapType(type)))
          .requestElement(productionMethod)
          .build();
    } else {
      return newDependencyRequest(productionMethod, type, qualifier);
    }
  }

  DependencyRequest forComponentMembersInjectionMethod(
      ExecutableElement membersInjectionMethod, ExecutableType membersInjectionMethodType) {
    checkNotNull(membersInjectionMethod);
    checkNotNull(membersInjectionMethodType);
    Optional<AnnotationMirror> qualifier =
        InjectionAnnotations.getQualifier(membersInjectionMethod);
    checkArgument(!qualifier.isPresent());
    TypeMirror membersInjectedType = getOnlyElement(membersInjectionMethodType.getParameterTypes());
    return DependencyRequest.builder()
        .kind(MEMBERS_INJECTION)
        .key(keyFactory.forMembersInjectedType(membersInjectedType))
        .requestElement(membersInjectionMethod)
        .build();
  }

  DependencyRequest forProductionImplementationExecutor() {
    return DependencyRequest.builder()
        .kind(PROVIDER)
        .key(keyFactory.forProductionImplementationExecutor())
        .build();
  }

  DependencyRequest forProductionComponentMonitor() {
    return DependencyRequest.builder()
        .kind(PROVIDER)
        .key(keyFactory.forProductionComponentMonitor())
        .build();
  }

  /**
   * Returns a synthetic request for the present value of an optional binding generated from a
   * {@link dagger.BindsOptionalOf} declaration.
   */
  DependencyRequest forSyntheticPresentOptionalBinding(Key requestKey, RequestKind kind) {
    Optional<Key> key = keyFactory.unwrapOptional(requestKey);
    checkArgument(key.isPresent(), "not a request for optional: %s", requestKey);
    return DependencyRequest.builder()
        .kind(kind)
        .key(key.get())
        .isNullable(
            allowsNull(getRequestKind(OptionalType.from(requestKey).valueType()), Optional.empty()))
        .build();
  }

  private DependencyRequest newDependencyRequest(
      Element requestElement, TypeMirror type, Optional<AnnotationMirror> qualifier) {
    RequestKind requestKind = getRequestKind(type);
    return DependencyRequest.builder()
        .kind(requestKind)
        .key(keyFactory.forQualifiedType(qualifier, extractKeyType(requestKind, type)))
        .requestElement(requestElement)
        .isNullable(allowsNull(requestKind, getNullableType(requestElement)))
        .build();
  }

  /**
   * Returns {@code true} if a given request element allows null values. {@link
   * RequestKind#INSTANCE} requests must be annotated with {@code @Nullable} in order to allow null
   * values. All other request kinds implicitly allow null values because they are are wrapped
   * inside {@link Provider}, {@link Lazy}, etc.
   */
  private boolean allowsNull(RequestKind kind, Optional<DeclaredType> nullableType) {
    return nullableType.isPresent() || !kind.equals(INSTANCE);
  }
}
