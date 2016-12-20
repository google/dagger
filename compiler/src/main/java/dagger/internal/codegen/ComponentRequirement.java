/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.requiresAPassedInstance;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** A type that a component needs an instance of. */
@AutoValue
abstract class ComponentRequirement {
  enum Kind {
    /** A type listed in the component's {@code dependencies} attribute. */
    DEPENDENCY,
    /** A type listed in the component or subcomponent's {@code modules} attribute. */
    MODULE,
    /** An object key that can be bound to an instance provided to the builder. */
    BINDING,
  }

  /** The kind of requirement. */
  abstract Kind kind();

  /**
   * The type of the instance the component must have, wrapped so that requirements can be used as
   * value types.
   */
  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

  /** The type of the instance the component must have. */
  TypeMirror type() {
    return wrappedType().get();
  }

  /** The element associated with the type of this requirement. */
  TypeElement typeElement() {
    return MoreTypes.asTypeElement(type());
  }

  /** The action a component builder should take if it {@code null} is passed. */
  enum NullPolicy {
    /** Make a new instance. */
    NEW,
    /** Throw an exception. */
    THROW,
    /** Allow use of null values. */
    ALLOW,
  }

  /**
   * An override for the requirement's null policy. If set, this is used as the null policy instead
   * of the default behavior in {@link #nullPolicy}.
   *
   * <p>Some implementations' null policy can be determined upon construction (e.g., for binding
   * instances), but others' require Elements and Types, which must wait until {@link #nullPolicy}
   * is called.
   */
  abstract Optional<NullPolicy> overrideNullPolicy();

  /** The requirement's null policy. */
  NullPolicy nullPolicy(Elements elements, Types types) {
    if (overrideNullPolicy().isPresent()) {
      return overrideNullPolicy().get();
    }
    switch (kind()) {
      case DEPENDENCY:
        return NullPolicy.THROW;
      case MODULE:
        return componentCanMakeNewInstances(typeElement())
            ? NullPolicy.NEW
            : requiresAPassedInstance(elements, types, typeElement())
                ? NullPolicy.THROW
                : NullPolicy.ALLOW;
      case BINDING:
        return NullPolicy.THROW;
    }
    throw new AssertionError();
  }

  /** The key for this requirement, if one is available. */
  abstract Optional<Key> key();

  static ComponentRequirement forDependency(TypeMirror type) {
    return new AutoValue_ComponentRequirement(
        Kind.DEPENDENCY,
        MoreTypes.equivalence().wrap(checkNotNull(type)),
        Optional.empty(),
        Optional.empty());
  }

  static ComponentRequirement forModule(TypeMirror type) {
    return new AutoValue_ComponentRequirement(
        Kind.MODULE,
        MoreTypes.equivalence().wrap(checkNotNull(type)),
        Optional.empty(),
        Optional.empty());
  }

  static ComponentRequirement forBinding(Key key, boolean nullable) {
    return new AutoValue_ComponentRequirement(
        Kind.BINDING,
        key.wrappedType(),
        nullable ? Optional.of(NullPolicy.ALLOW) : Optional.empty(),
        Optional.of(key));
  }

  static ComponentRequirement forBinding(ContributionBinding binding) {
    return forBinding(binding.key(), binding.nullableType().isPresent());
  }
}
