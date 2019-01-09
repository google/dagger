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

import com.google.common.collect.ImmutableSet;

/**
 * A label for a binding indicating whether, and how, it may be redefined across implementations of
 * a subcomponent.
 *
 * <p>A subcomponent has multiple implementations only when generating ahead-of-time subcomponents.
 * Specifically, each subcomponent type in a component hierarchy is implemented as an abstract
 * class, and descendent components are implemented as abstract inner classes. A consequence of this
 * is that a given subcomponent has an implementation for each ancestor component. Each
 * implementation represents a different sub-binding-graph of the full subcomponent. A binding is
 * modifiable if it's definition may change depending on the characteristics of its ancestor
 * components.
 */
enum ModifiableBindingType {
  /** A binding that is not modifiable */
  NONE,

  /**
   * A binding that is missing when generating the abstract base class implementation of a
   * subcomponent.
   */
  MISSING,

  /**
   * A binding that requires an instance of a generated type. These binding are modifiable in the
   * sense that they are encapsulated in a method when they are first required, possibly in an
   * abstract implementation of a subcomponent, where, in general, no concrete instances of
   * generated types are available, and the method is satisfied in a final concrete implementation.
   */
  GENERATED_INSTANCE,

  /**
   * Multibindings may have contributions come from any ancestor component. Therefore, each
   * implementation of a subcomponent may have newly available contributions, and so the binding
   * method is reimplemented with each subcomponent implementation.
   */
  MULTIBINDING,

  /**
   * A Optional binding that may be empty when looking at a partial binding graph, but bound to a
   * value when considering the complete binding graph, thus modifiable across subcomponent
   * implementations.
   */
  OPTIONAL,

  /**
   * If a binding is defined according to an {@code @Inject} annotated constructor on the object it
   * is valid for that binding to be redefined a single time by an {@code @Provides} annotated
   * module method. It is possible that the {@code @Provides} binding isn't available in a partial
   * binding graph, but becomes available when considering a more complete binding graph, therefore
   * such bindings are modifiable across subcomponent implementations.
   */
  INJECTION,

  /**
   * A {@link dagger.Binds} method whose dependency is {@link #MISSING}.
   *
   * <p>There's not much to do for @Binds bindings if the dependency is missing - at best, if the
   * dependency is a weaker scope/unscoped, we save only a few lines that implement the scoping. But
   * it's also possible, if the dependency is the same or stronger scope, that no extra code is
   * necessary, in which case we'd be overriding a method that just returns another.
   */
  BINDS_METHOD_WITH_MISSING_DEPENDENCY,
  ;

  private static final ImmutableSet<ModifiableBindingType> TYPES_WITH_BASE_CLASS_IMPLEMENTATIONS =
      ImmutableSet.of(NONE, INJECTION, MULTIBINDING, OPTIONAL);

  boolean isModifiable() {
    return !equals(NONE);
  }

  /**
   * Returns true if the method encapsulating the modifiable binding should have a concrete
   * implementation in the abstract base class for a subcomponent.
   */
  boolean hasBaseClassImplementation() {
    return TYPES_WITH_BASE_CLASS_IMPLEMENTATIONS.contains(this);
  }
}
