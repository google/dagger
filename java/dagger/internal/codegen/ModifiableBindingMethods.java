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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.MethodSpec;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Map;
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
  private final Map<KeyAndKind, ModifiableBindingMethod> methods = Maps.newHashMap();
  private final Set<KeyAndKind> finalizedMethods = Sets.newHashSet();

  /** Register a method encapsulating a modifiable binding. */
  void addMethod(
      ModifiableBindingType type, Key key, RequestKind kind, MethodSpec unimplementedMethod) {
    KeyAndKind keyAndKind = KeyAndKind.create(key, kind);
    checkState(
        !finalizedMethods.contains(keyAndKind),
        "Adding a modifiable binding method for a binding that has been marked as finalized for "
            + "the current subcomponent implementation. The binding is for a %s-%s of type %s.",
        key,
        kind,
        type);
    methods.put(keyAndKind, ModifiableBindingMethod.create(type, key, kind, unimplementedMethod));
  }

  /** Returns all {@link ModifiableBindingMethod}s. */
  ImmutableList<ModifiableBindingMethod> getMethods() {
    // We will never add a modifiable binding method and mark it as having been finalized in the
    // same instance of ModifiableBindingMethods, so there's no need to filter `methods` by
    // `finalizedMethods`.
    return ImmutableList.copyOf(methods.values());
  }

  /**
   * Mark the {@link ModifiableBindingMethod} as having been implemented, thus modifying the
   * binding. For those bindings that are finalized when modified, mark the binding as finalized,
   * meaning it should no longer be modified.
   */
  void methodImplemented(ModifiableBindingMethod method) {
    if (method.type().finalizedOnModification()) {
      KeyAndKind keyAndKind = KeyAndKind.create(method.key(), method.kind());
      checkState(
          !methods.containsKey(keyAndKind),
          "Indicating a modifiable binding method is finalized when it was registered as "
              + "modifiable for the current subcomponent implementation. The binding is for a "
              + "%s-%s of type %s.",
          method.key(),
          method.kind(),
          method.type());
      finalizedMethods.add(keyAndKind);
    }
  }

  /** Whether a given binding has been marked as finalized. */
  boolean isFinalized(ModifiableBindingMethod method) {
    return finalizedMethods.contains(KeyAndKind.create(method.key(), method.kind()));
  }

  @AutoValue
  abstract static class ModifiableBindingMethod {
    private static ModifiableBindingMethod create(
        ModifiableBindingType type, Key key, RequestKind kind, MethodSpec unimplementedMethod) {
      return new AutoValue_ModifiableBindingMethods_ModifiableBindingMethod(
          type, key, kind, unimplementedMethod);
    }

    abstract ModifiableBindingType type();

    abstract Key key();

    abstract RequestKind kind();

    abstract MethodSpec baseMethod();
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
