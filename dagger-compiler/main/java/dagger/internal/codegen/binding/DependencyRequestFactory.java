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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.base.RequestKinds.getRequestKind;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedParameter;
import static dagger.internal.codegen.model.RequestKind.FUTURE;
import static dagger.internal.codegen.model.RequestKind.INSTANCE;
import static dagger.internal.codegen.model.RequestKind.MEMBERS_INJECTION;
import static dagger.internal.codegen.model.RequestKind.PROVIDER;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableSet;
import dagger.Lazy;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.model.DaggerElement;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Factory for {@link DependencyRequest}s.
 *
 * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available, which
 * may mean that the type will be generated in a later round of processing.
 */
public final class DependencyRequestFactory {
  private final KeyFactory keyFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  DependencyRequestFactory(KeyFactory keyFactory, InjectionAnnotations injectionAnnotations) {
    this.keyFactory = keyFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  ImmutableSet<DependencyRequest> forRequiredResolvedVariables(
      List<? extends XVariableElement> variables, List<XType> resolvedTypes) {
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

  private RequestKind multibindingContributionRequestKind(
      Key multibindingKey, ContributionBinding multibindingContribution) {
    switch (multibindingContribution.contributionType()) {
      case MAP:
        return MapType.from(multibindingKey).valueRequestKind();
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
      XVariableElement variableElement, XType resolvedType) {
    checkNotNull(variableElement);
    checkNotNull(resolvedType);
    // Ban @Assisted parameters, they are not considered dependency requests.
    checkArgument(!isAssistedParameter(variableElement));
    Optional<XAnnotation> qualifier = injectionAnnotations.getQualifier(variableElement);
    return newDependencyRequest(variableElement, resolvedType, qualifier);
  }

  public DependencyRequest forComponentProvisionMethod(
      XMethodElement provisionMethod, XMethodType provisionMethodType) {
    checkNotNull(provisionMethod);
    checkNotNull(provisionMethodType);
    checkArgument(
        provisionMethod.getParameters().isEmpty(),
        "Component provision methods must be empty: %s",
        provisionMethod);
    Optional<XAnnotation> qualifier = injectionAnnotations.getQualifier(provisionMethod);
    return newDependencyRequest(provisionMethod, provisionMethodType.getReturnType(), qualifier);
  }

  public DependencyRequest forComponentProductionMethod(
      XMethodElement productionMethod, XMethodType productionMethodType) {
    checkNotNull(productionMethod);
    checkNotNull(productionMethodType);
    checkArgument(
        productionMethod.getParameters().isEmpty(),
        "Component production methods must be empty: %s",
        productionMethod);
    XType type = productionMethodType.getReturnType();
    Optional<XAnnotation> qualifier = injectionAnnotations.getQualifier(productionMethod);
    // Only a component production method can be a request for a ListenableFuture, so we
    // special-case it here.
    if (isTypeOf(type, XTypeNames.LISTENABLE_FUTURE)) {
      return DependencyRequest.builder()
          .kind(FUTURE)
          .key(keyFactory.forQualifiedType(qualifier, unwrapType(type)))
          .requestElement(DaggerElement.from(productionMethod))
          .build();
    } else {
      return newDependencyRequest(productionMethod, type, qualifier);
    }
  }

  DependencyRequest forComponentMembersInjectionMethod(
      XMethodElement membersInjectionMethod, XMethodType membersInjectionMethodType) {
    checkNotNull(membersInjectionMethod);
    checkNotNull(membersInjectionMethodType);
    Optional<XAnnotation> qualifier = injectionAnnotations.getQualifier(membersInjectionMethod);
    checkArgument(!qualifier.isPresent());
    XType membersInjectedType = getOnlyElement(membersInjectionMethodType.getParameterTypes());
    return DependencyRequest.builder()
        .kind(MEMBERS_INJECTION)
        .key(keyFactory.forMembersInjectedType(membersInjectedType))
        .requestElement(DaggerElement.from(membersInjectionMethod))
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
  DependencyRequest forSyntheticPresentOptionalBinding(Key requestKey) {
    Optional<Key> key = keyFactory.unwrapOptional(requestKey);
    checkArgument(key.isPresent(), "not a request for optional: %s", requestKey);
    RequestKind kind = getRequestKind(OptionalType.from(requestKey).valueType());
    return DependencyRequest.builder()
        .kind(kind)
        .key(key.get())
        .isNullable(requestKindImplicitlyAllowsNull(kind))
        .build();
  }

  private DependencyRequest newDependencyRequest(
      XElement requestElement, XType type, Optional<XAnnotation> qualifier) {
    RequestKind requestKind = getRequestKind(type);
    return DependencyRequest.builder()
        .kind(requestKind)
        .key(keyFactory.forQualifiedType(qualifier, extractKeyType(type)))
        .requestElement(DaggerElement.from(requestElement))
        .isNullable(allowsNull(requestKind, Nullability.of(requestElement)))
        .build();
  }

  /**
   * Returns {@code true} if a given request element allows null values. {@link
   * RequestKind#INSTANCE} requests must be nullable in order to allow null values. All other
   * request kinds implicitly allow null values because they are are wrapped inside {@link
   * Provider}, {@link Lazy}, etc.
   */
  private boolean allowsNull(RequestKind kind, Nullability nullability) {
    return nullability.isNullable() || requestKindImplicitlyAllowsNull(kind);
  }

  private boolean requestKindImplicitlyAllowsNull(RequestKind kind) {
    return !kind.equals(INSTANCE);
  }
}
