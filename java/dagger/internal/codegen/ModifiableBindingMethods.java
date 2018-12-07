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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.MethodSpec;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final Set<BindingRequest> finalizedMethods = Sets.newHashSet();

  /** Register a method encapsulating a modifiable binding. */
  void addMethod(
      ModifiableBindingType type,
      BindingRequest request,
      TypeMirror returnType,
      MethodSpec method,
      boolean finalized) {
    checkArgument(type.isModifiable());
    if (finalized) {
      finalizedMethods.add(request);
    }
    methods.put(
        request, ModifiableBindingMethod.create(type, request, returnType, method, finalized));
  }

  /** Returns all {@link ModifiableBindingMethod}s that have not been marked as finalized. */
  ImmutableList<ModifiableBindingMethod> getNonFinalizedMethods() {
    return methods.values().stream().filter(m -> !m.finalized()).collect(toImmutableList());
  }

  /** Returns the {@link ModifiableBindingMethod} for the given binding if present. */
  Optional<ModifiableBindingMethod> getMethod(BindingRequest request) {
    return Optional.ofNullable(methods.get(request));
  }

  /** Returns all of the {@link ModifiableBindingMethod}s. */
  ImmutableList<ModifiableBindingMethod> allMethods() {
    return ImmutableList.copyOf(methods.values());
  }

  /**
   * Mark the {@link ModifiableBindingMethod} as having been implemented, thus modifying the
   * binding.
   */
  void methodImplemented(ModifiableBindingMethod method) {
    if (method.finalized()) {
      checkState(
          finalizedMethods.add(method.request()),
          "Implementing and finalizing a modifiable binding method that has been marked as "
              + "finalized in the current subcomponent implementation. The binding is for a %s "
              + "of type %s.",
          method.request(),
          method.type());
    }
  }

  /** Whether a given binding has been marked as finalized. */
  boolean finalized(ModifiableBindingMethod method) {
    return finalizedMethods.contains(method.request());
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

    /** Create a {@ModifiableBindingMethod} representing an implementation of an existing method. */
    static ModifiableBindingMethod implement(
        ModifiableBindingMethod unimplementedMethod, MethodSpec methodSpec, boolean finalized) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          unimplementedMethod.type(),
          unimplementedMethod.request(),
          unimplementedMethod.returnTypeWrapper(),
          methodSpec,
          finalized);
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
