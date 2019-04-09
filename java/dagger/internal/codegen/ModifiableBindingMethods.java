/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static com.google.common.base.Verify.verify;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.squareup.javapoet.MethodSpec;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A registry for those methods which each wrap a binding whose definition may be modified across
 * each class in the class hierarchy implementing a subcomponent. Subcomponent implementations are
 * spread across a class hierarchy when generating ahead-of-time subcomponents. There is one
 * subcomponent implementation class for each of the subcomponent's ancestor components. An instance
 * of {@link ModifiableBindingMethod} is associated with a single class in this hierarchy. For a
 * given subcomponent implementation class we can use the {@link ModifiableBindingMethod}s of its
 * superclasses to know what binding methods to attempt to modify.
 */
final class ModifiableBindingMethods {
  private final Map<BindingRequest, ModifiableBindingMethod> methods = Maps.newLinkedHashMap();

  /** Registers a new method encapsulating a modifiable binding. */
  void addModifiableMethod(
      ModifiableBindingType type,
      BindingRequest request,
      TypeMirror returnType,
      MethodSpec method,
      boolean finalized) {
    // It's ok for the type to not be modifiable, since it could be overriding a previously
    // modifiable method (such as with addReimplementedMethod).
    addMethod(ModifiableBindingMethod.create(type, request, returnType, method, finalized));
  }

  /** Registers a reimplemented modifiable method. */
  void addReimplementedMethod(ModifiableBindingMethod method) {
    addMethod(method);
  }

  private void addMethod(ModifiableBindingMethod method) {
    ModifiableBindingMethod previousMethod = methods.put(method.request(), method);
    verify(
        previousMethod == null,
        "registering %s but %s is already registered for the same binding request",
        method,
        previousMethod);
  }

  /** Returns all {@link ModifiableBindingMethod}s that have not been marked as finalized. */
  ImmutableMap<BindingRequest, ModifiableBindingMethod> getNonFinalizedMethods() {
    return ImmutableMap.copyOf(Maps.filterValues(methods, m -> !m.finalized()));
  }

  /** Returns the {@link ModifiableBindingMethod} for the given binding if present. */
  Optional<ModifiableBindingMethod> getMethod(BindingRequest request) {
    return Optional.ofNullable(methods.get(request));
  }

  /** Returns all of the {@link ModifiableBindingMethod}s. */
  ImmutableList<ModifiableBindingMethod> allMethods() {
    return ImmutableList.copyOf(methods.values());
  }

  /** Whether a given binding has been marked as finalized. */
  // TODO(ronshapiro): possibly rename this to something that indicates that the BindingRequest for
  // `method` has been finalized in *this* component implementation?
  boolean finalized(ModifiableBindingMethod method) {
    ModifiableBindingMethod storedMethod = methods.get(method.request());
    return storedMethod != null && storedMethod.finalized();
  }

  @AutoValue
  abstract static class ModifiableBindingMethod {
    private static ModifiableBindingMethod create(
        ModifiableBindingType type,
        BindingRequest request,
        TypeMirror returnType,
        MethodSpec methodSpec,
        boolean finalized) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          type, request, MoreTypes.equivalence().wrap(returnType), methodSpec, finalized);
    }

    /** Creates a {@ModifiableBindingMethod} that reimplements the current method. */
    ModifiableBindingMethod reimplement(
        ModifiableBindingType newModifiableBindingType,
        MethodSpec newImplementation,
        boolean finalized) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          newModifiableBindingType, request(), returnTypeWrapper(), newImplementation, finalized);
    }

    abstract ModifiableBindingType type();

    abstract BindingRequest request();

    final TypeMirror returnType() {
      return returnTypeWrapper().get();
    }

    abstract Equivalence.Wrapper<TypeMirror> returnTypeWrapper();

    abstract MethodSpec methodSpec();

    abstract boolean finalized();

    /** Whether a {@link ModifiableBindingMethod} is for the same binding request. */
    boolean fulfillsSameRequestAs(ModifiableBindingMethod other) {
      return request().equals(other.request());
    }
  }
}
