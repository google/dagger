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

import static com.google.auto.common.MoreElements.asType;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerTypes.isFutureType;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;
import static dagger.model.BindingKind.COMPONENT_PRODUCTION;
import static dagger.model.BindingKind.DELEGATE;
import static dagger.model.BindingKind.OPTIONAL;
import static dagger.model.BindingKind.PRODUCTION;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

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
  abstract Optional<ProductionBinding> unresolved();

  @Override
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return Stream.of(executorRequest(), monitorRequest())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  /** What kind of object this produces method returns. */
  enum ProductionKind {
    /** A value. */
    IMMEDIATE,
    /** A {@code ListenableFuture<T>}. */
    FUTURE,
    /** A {@code Set<ListenableFuture<T>>}. */
    SET_OF_FUTURE;
  }

  /**
   * Returns the kind of object the produces method returns. All production bindings from
   * {@code @Produces} methods will have a production kind, but synthetic production bindings may
   * not.
   */
  abstract Optional<ProductionKind> productionKind();

  /** Returns the list of types in the throws clause of the method. */
  abstract ImmutableList<? extends TypeMirror> thrownTypes();

  /**
   * If this production requires an executor, this will be the corresponding request.  All
   * production bindings from {@code @Produces} methods will have an executor request, but
   * synthetic production bindings may not.
   */
  abstract Optional<DependencyRequest> executorRequest();

  /** If this production requires a monitor, this will be the corresponding request.  All
   * production bindings from {@code @Produces} methods will have a monitor request, but synthetic
   * production bindings may not.
   */
  abstract Optional<DependencyRequest> monitorRequest();

  private static Builder builder() {
    return new AutoValue_ProductionBinding.Builder()
        .explicitDependencies(ImmutableList.<DependencyRequest>of())
        .thrownTypes(ImmutableList.<TypeMirror>of());
  }

  @Override
  public final boolean isProduction() {
    return true;
  }

  @AutoValue.Builder
  @CanIgnoreReturnValue
  abstract static class Builder extends ContributionBinding.Builder<Builder> {
    abstract Builder explicitDependencies(Iterable<DependencyRequest> dependencies);

    abstract Builder explicitDependencies(DependencyRequest... dependencies);

    abstract Builder productionKind(ProductionKind productionKind);

    abstract Builder unresolved(ProductionBinding unresolved);

    abstract Builder thrownTypes(Iterable<? extends TypeMirror> thrownTypes);

    abstract Builder executorRequest(DependencyRequest executorRequest);

    abstract Builder monitorRequest(DependencyRequest monitorRequest);

    @CheckReturnValue
    abstract ProductionBinding build();
  }

  /* TODO(dpb): Combine ProvisionBinding.Factory, ProductionBinding.Factory, and
   * MembersInjectionBinding.Factory into one BindingFactory class.*/
  static final class Factory {
    private final Types types;
    private final KeyFactory keyFactory;
    private final DependencyRequestFactory dependencyRequestFactory;

    @Inject
    Factory(Types types, KeyFactory keyFactory, DependencyRequestFactory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProductionBinding forProducesMethod(
        ExecutableElement producesMethod, TypeElement contributedBy) {
      checkArgument(producesMethod.getKind().equals(METHOD));
      ContributionType contributionType = ContributionType.fromBindingMethod(producesMethod);
      Key key = keyFactory.forProducesMethod(producesMethod, contributedBy);
      ExecutableType methodType =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributedBy.asType()), producesMethod));
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              producesMethod.getParameters(), methodType.getParameterTypes());
      DependencyRequest executorRequest =
          dependencyRequestFactory.forProductionImplementationExecutor();
      DependencyRequest monitorRequest = dependencyRequestFactory.forProductionComponentMonitor();
      final ProductionKind productionKind;
      if (isFutureType(producesMethod.getReturnType())) {
        productionKind = ProductionKind.FUTURE;
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && isFutureType(SetType.from(producesMethod.getReturnType()).elementType())) {
        productionKind = ProductionKind.SET_OF_FUTURE;
      } else {
        productionKind = ProductionKind.IMMEDIATE;
      }
      // TODO(beder): Add nullability checking with Java 8.
      ProductionBinding.Builder builder =
          ProductionBinding.builder()
              .contributionType(contributionType)
              .bindingElement(producesMethod)
              .contributingModule(contributedBy)
              .key(key)
              .explicitDependencies(dependencies)
              .wrappedMapKey(wrapOptionalInEquivalence(getMapKey(producesMethod)))
              .kind(PRODUCTION)
              .productionKind(productionKind)
              .thrownTypes(producesMethod.getThrownTypes())
              .executorRequest(executorRequest)
              .monitorRequest(monitorRequest);
      if (!types.isSameType(methodType, producesMethod.asType())) {
        builder.unresolved(
            forProducesMethod(producesMethod, asType(producesMethod.getEnclosingElement())));
      }
      return builder.build();
    }

    /**
     * A synthetic binding that depends explicitly on a set of individual provision or production
     * multibinding contribution methods.
     *
     * <p>Note that these could be set multibindings or map multibindings.
     */
    ProductionBinding syntheticMultibinding(
        Key key, Iterable<ContributionBinding> multibindingContributions) {
      return ProductionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(key)
          .explicitDependencies(
              dependencyRequestFactory.forMultibindingContributions(key, multibindingContributions))
          .kind(bindingKindForMultibindingKey(key))
          .build();
    }

    ProductionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      checkArgument(isFutureType(componentMethod.getReturnType()));
      return ProductionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .bindingElement(componentMethod)
          .key(keyFactory.forProductionComponentMethod(componentMethod))
          .kind(COMPONENT_PRODUCTION)
          .thrownTypes(componentMethod.getThrownTypes())
          .build();
    }

    ProductionBinding delegate(
        DelegateDeclaration delegateDeclaration, ProductionBinding delegateBinding) {
      return ProductionBinding.builder()
          .contributionType(delegateDeclaration.contributionType())
          .bindingElement(delegateDeclaration.bindingElement().get())
          .contributingModule(delegateDeclaration.contributingModule().get())
          .key(keyFactory.forDelegateBinding(delegateDeclaration, Producer.class))
          .explicitDependencies(delegateDeclaration.delegateRequest())
          .nullableType(delegateBinding.nullableType())
          .wrappedMapKey(delegateDeclaration.wrappedMapKey())
          .kind(DELEGATE)
          .build();
    }

    /**
     * Returns a synthetic binding for an {@linkplain dagger.BindsOptionalOf optional binding} in a
     * component with a binding for the underlying key.
     */
    ProductionBinding syntheticPresentBinding(Key key, RequestKind kind) {
      return ProductionBinding.builder()
          .contributionType(ContributionType.UNIQUE)
          .key(key)
          .kind(OPTIONAL)
          .explicitDependencies(
              dependencyRequestFactory.forSyntheticPresentOptionalBinding(key, kind))
          .build();
    }
  }
}
