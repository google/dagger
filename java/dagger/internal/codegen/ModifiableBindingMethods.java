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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.MethodSpec;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
  private final Map<KeyAndKind, ModifiableBindingMethod> methods = Maps.newLinkedHashMap();
  private final Set<KeyAndKind> finalizedMethods = Sets.newHashSet();

  /** Register a method encapsulating a modifiable binding. */
  void addMethod(
      ModifiableBindingType type, Key key, RequestKind kind, MethodSpec method, boolean finalized) {
    checkArgument(type.isModifiable());
    KeyAndKind keyAndKind = KeyAndKind.create(key, kind);
    if (finalized) {
      finalizedMethods.add(keyAndKind);
    }
    methods.put(keyAndKind, ModifiableBindingMethod.create(type, key, kind, method, finalized));
  }

  /** Returns all {@link ModifiableBindingMethod}s that have not been marked as finalized. */
  ImmutableList<ModifiableBindingMethod> getNonFinalizedMethods() {
    return methods.values().stream().filter(m -> !m.finalized()).collect(toImmutableList());
  }

  /** Returns the {@link ModifiableBindingMethod} for the given binding if present. */
  Optional<ModifiableBindingMethod> getMethod(Key key, RequestKind kind) {
    return Optional.ofNullable(methods.get(KeyAndKind.create(key, kind)));
  }

  /**
   * Mark the {@link ModifiableBindingMethod} as having been implemented, thus modifying the
   * binding.
   */
  void methodImplemented(ModifiableBindingMethod method) {
    if (method.finalized()) {
      checkState(
          finalizedMethods.add(KeyAndKind.create(method.key(), method.kind())),
          "Implementing and finalizing a modifiable binding method that has been marked as "
              + "finalized in the current subcomponent implementation. The binding is for a %s-%s "
              + "of type %s.",
          method.key(),
          method.kind(),
          method.type());
    }
  }

  /** Whether a given binding has been marked as finalized. */
  boolean finalized(ModifiableBindingMethod method) {
    return finalizedMethods.contains(KeyAndKind.create(method.key(), method.kind()));
  }

  @AutoValue
  abstract static class ModifiableBindingMethod {
    private static ModifiableBindingMethod create(
        ModifiableBindingType type,
        Key key,
        RequestKind kind,
        MethodSpec methodSpec,
        boolean finalized) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          type, key, kind, methodSpec, finalized);
    }

    /** Create a {@ModifiableBindingMethod} representing an implementation of an existing method. */
    static ModifiableBindingMethod implement(
        ModifiableBindingMethod unimplementedMethod, MethodSpec methodSpec, boolean finalized) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          unimplementedMethod.type(),
          unimplementedMethod.key(),
          unimplementedMethod.kind(),
          methodSpec,
          finalized);
    }

    abstract ModifiableBindingType type();

    abstract Key key();

    abstract RequestKind kind();

    abstract MethodSpec methodSpec();

    abstract boolean finalized();
  }

  @AutoValue
  abstract static class KeyAndKind {
    private static KeyAndKind create(Key key, RequestKind kind) {
      return new AutoValue_ModifiableBindingMethods_KeyAndKind(key, kind);
    }

    abstract Key key();

    abstract RequestKind kind();
  }
}
