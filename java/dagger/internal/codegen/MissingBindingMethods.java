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
 * A registry for those methods which each wrap a binding that is unsatisfiable by a subcomponent in
 * isolation, but can be satisfied by an ancestor component. This is useful when generating
 * ahead-of-time subcomponents: An instance of this class is associated with a single subcomponent
 * implementation. We generate an implementation of a given subcomponent once for each of it's
 * ancestor components and for any one implementation the {@link MissingBindingMethod}s of it's
 * superclasses tell us what missing bindings have yet to be satisfied so we can attempt to satisfy
 * them.
 */
final class MissingBindingMethods {
  private final Map<KeyAndKind, MissingBindingMethod> missingBindingMethods = Maps.newHashMap();
  private final Set<KeyAndKind> implementedMissingBindingMethods = Sets.newHashSet();

  /** Record an unimplemented method encapsulating a missing binding. */
  void addUnimplementedMethod(Key key, RequestKind kind, MethodSpec unimplementedMethod) {
    KeyAndKind keyAndKind = KeyAndKind.create(key, kind);
    checkState(
        !implementedMissingBindingMethods.contains(keyAndKind),
        "Adding an missing binding method for a method marked as implemented for the current "
            + "subcomponent implementation. The binding is for a %s-%s.",
        key,
        kind);
    missingBindingMethods.put(
        keyAndKind, MissingBindingMethod.create(key, kind, unimplementedMethod));
  }

  /** Returns all unimplemented {@link MissingBindingMethod}s */
  ImmutableList<MissingBindingMethod> getUnimplementedMethods() {
    // We will never register a binding as missing and also as implemented for the same instance of
    // MissingBindingMethods, so there's no need to filter missingBindingMethods.
    return ImmutableList.copyOf(missingBindingMethods.values());
  }

  /** Mark the {@link MissingBindingMethod} as having been implemented. */
  void methodImplemented(MissingBindingMethod method) {
    KeyAndKind keyAndKind = KeyAndKind.create(method.key(), method.kind());
    checkState(
        !missingBindingMethods.containsKey(keyAndKind),
        "Indicating a missing binding method as implemented when it was registered as missing for "
            + "the current subcomponent implementation. The binding is for a %s-%s.",
        method.key(),
        method.kind());
    implementedMissingBindingMethods.add(keyAndKind);
  }

  /** Whether a given binding has been marked as implemented. */
  boolean isMethodImplemented(MissingBindingMethod method) {
    return implementedMissingBindingMethods.contains(
        KeyAndKind.create(method.key(), method.kind()));
  }

  @AutoValue
  abstract static class MissingBindingMethod {
    private static MissingBindingMethod create(
        Key key, RequestKind kind, MethodSpec unimplementedMethod) {
      return new AutoValue_MissingBindingMethods_MissingBindingMethod(
          key, kind, unimplementedMethod);
    }

    abstract Key key();

    abstract RequestKind kind();

    abstract MethodSpec unimplementedMethod();
  }

  @AutoValue
  abstract static class KeyAndKind {
    private static KeyAndKind create(Key key, RequestKind kind) {
      return new AutoValue_MissingBindingMethods_KeyAndKind(key, kind);
    }

    abstract Key key();

    abstract RequestKind kind();
  }
}
