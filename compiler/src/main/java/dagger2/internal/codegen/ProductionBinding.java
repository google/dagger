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
package dagger2.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger2.producers.Producer;
import dagger2.producers.Produces;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
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
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return dependencies();
  }

  enum Kind {
    /** Represents a binding configured by {@link Produces} that doesn't return a future. */
    IMMEDIATE,
    /** Represents a binding configured by {@link Produces} that returns a future. */
    FUTURE_PRODUCTION,
    /**
     * Represents a binding that is not explicitly tied to code, but generated implicitly by the
     * framework.
     */
    SYNTHETIC_PRODUCTION,
    /**
     * Represents a binding from a production method on a component dependency that returns a
     * future. Methods that return immediate values are considered provision bindings.
     */
    COMPONENT_PRODUCTION,
  }

  /**
   * The type of binding (whether the {@link Produces} method returns a future). For the particular
   * type of production, use {@link #productionType}.
   */
  abstract Kind bindingKind();

  /** Returns provision type that was used to bind the key. */
  abstract Produces.Type productionType();

  /** Returns the list of types in the throws clause of the method. */
  abstract ImmutableList<? extends TypeMirror> thrownTypes();

  @Override
  BindingType bindingType() {
    switch (productionType()) {
      case SET:
      case SET_VALUES:
        return BindingType.SET;
      case MAP:
        return BindingType.MAP;
      case UNIQUE:
        return BindingType.UNIQUE;
      default:
        throw new IllegalStateException("Unknown production type: " + productionType());
    }
  }

  @Override
  boolean isSyntheticBinding() {
    return bindingKind().equals(Kind.SYNTHETIC_PRODUCTION);
  }

  @Override
  Class<?> frameworkClass() {
    return Producer.class;
  }

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Types types,
        Key.Factory keyFactory,
        DependencyRequest.Factory
        dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProductionBinding forProducesMethod(
        ExecutableElement producesMethod, TypeMirror contributedBy) {
      checkNotNull(producesMethod);
      checkArgument(producesMethod.getKind().equals(METHOD));
      checkArgument(contributedBy.getKind().equals(TypeKind.DECLARED));
      Produces producesAnnotation = producesMethod.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      DeclaredType declaredContainer = MoreTypes.asDeclared(contributedBy);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(types.asMemberOf(declaredContainer, producesMethod));
      Key key = keyFactory.forProducesMethod(resolvedMethod, producesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              declaredContainer,
              producesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      Kind kind = MoreTypes.isTypeOf(ListenableFuture.class, producesMethod.getReturnType())
          ? Kind.FUTURE_PRODUCTION
          : Kind.IMMEDIATE;
      return new AutoValue_ProductionBinding(
          key,
          producesMethod,
          dependencies,
          findBindingPackage(key),
          false,
          ConfigurationAnnotations.getNullableType(producesMethod),
          Optional.of(MoreTypes.asTypeElement(declaredContainer)),
          kind,
          producesAnnotation.type(),
          ImmutableList.copyOf(producesMethod.getThrownTypes()));
    }

    ProductionBinding forImplicitMapBinding(DependencyRequest explicitRequest,
        DependencyRequest implicitRequest) {
      checkNotNull(explicitRequest);
      checkNotNull(implicitRequest);
      ImmutableSet<DependencyRequest> dependencies = ImmutableSet.of(implicitRequest);
      return new AutoValue_ProductionBinding(
          explicitRequest.key(),
          implicitRequest.requestElement(),
          dependencies,
          findBindingPackage(explicitRequest.key()),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Kind.SYNTHETIC_PRODUCTION,
          Produces.Type.MAP,
          ImmutableList.<TypeMirror>of());
    }

    ProductionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      checkArgument(MoreTypes.isTypeOf(ListenableFuture.class, componentMethod.getReturnType()));
      return new AutoValue_ProductionBinding(
          keyFactory.forProductionComponentMethod(componentMethod),
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Kind.COMPONENT_PRODUCTION,
          Produces.Type.UNIQUE,
          ImmutableList.copyOf(componentMethod.getThrownTypes()));
    }
  }
}
