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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.base.RequestKinds.frameworkClassName;
import static dagger.internal.codegen.base.RequestKinds.getRequestKind;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedParameter;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.getNullableType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;
import static dagger.spi.model.RequestKind.FUTURE;
import static dagger.spi.model.RequestKind.INSTANCE;
import static dagger.spi.model.RequestKind.MEMBERS_INJECTION;
import static dagger.spi.model.RequestKind.PRODUCER;
import static dagger.spi.model.RequestKind.PROVIDER;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XVariableElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import dagger.Lazy;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Factory for {@link DependencyRequest}s.
 *
 * <p>Any factory method may throw {@link TypeNotPresentException} if a type is not available, which
 * may mean that the type will be generated in a later round of processing.
 */
public final class DependencyRequestFactory {
  private final XProcessingEnv processingEnv;
  private final KeyFactory keyFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  DependencyRequestFactory(
      XProcessingEnv processingEnv,
      KeyFactory keyFactory,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.keyFactory = keyFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  ImmutableSet<DependencyRequest> forRequiredResolvedXVariables(
      List<? extends XVariableElement> variables, List<XType> resolvedTypes) {
    return forRequiredResolvedVariables(
        variables.stream().map(XConverters::toJavac).collect(toImmutableList()),
        resolvedTypes.stream().map(XConverters::toJavac).collect(toImmutableList()));
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
          if (mapType.valuesAreTypeOf(frameworkClassName(kind))) {
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
      XVariableElement variableElement, XType resolvedType) {
    return forRequiredResolvedVariable(toJavac(variableElement), toJavac(resolvedType));
  }

  DependencyRequest forRequiredResolvedVariable(
      VariableElement variableElement, TypeMirror resolvedType) {
    checkNotNull(variableElement);
    checkNotNull(resolvedType);
    // Ban @Assisted parameters, they are not considered dependency requests.
    checkArgument(!isAssistedParameter(toXProcessing(variableElement, processingEnv)));
    Optional<AnnotationMirror> qualifier = injectionAnnotations.getQualifier(variableElement);
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
    if (isTypeOf(type, TypeNames.LISTENABLE_FUTURE)) {
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
      XElement requestElement, XType type, Optional<XAnnotation> qualifier) {
    return newDependencyRequest(
        toJavac(requestElement), toJavac(type), qualifier.map(XConverters::toJavac));
  }

  private DependencyRequest newDependencyRequest(
      Element requestElement, TypeMirror type, Optional<AnnotationMirror> qualifier) {
    RequestKind requestKind = getRequestKind(type);
    return DependencyRequest.builder()
        .kind(requestKind)
        .key(keyFactory.forQualifiedType(qualifier, extractKeyType(type)))
        .requestElement(DaggerElement.from(toXProcessing(requestElement, processingEnv)))
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
