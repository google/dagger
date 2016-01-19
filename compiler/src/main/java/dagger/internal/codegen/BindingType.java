/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import dagger.MembersInjector;
import dagger.producers.Producer;
import javax.inject.Provider;

/**
 * Whether a binding or declaration is for provision, production, or a {@link MembersInjector}.
 */
enum BindingType {
  /** A binding with this type is a {@link ProvisionBinding}. */
  PROVISION(Provider.class),

  /** A binding with this type is a {@link MembersInjectionBinding}. */
  MEMBERS_INJECTION(MembersInjector.class),

  /** A binding with this type is a {@link ProductionBinding}. */
  PRODUCTION(Producer.class),
  ;

  /** An object that is associated with a {@link BindingType}. */
  interface HasBindingType {
    /** The binding type of this object. */
    BindingType bindingType();
  }

  private final Class<?> frameworkClass;

  private BindingType(Class<?> frameworkClass) {
    this.frameworkClass = frameworkClass;
  }

  /** The framework class associated with bindings of this type. */
  Class<?> frameworkClass() {
    return frameworkClass;
  }

  /** A predicate that passes for {@link HasBindingType}s with a given type. */
  static Predicate<HasBindingType> isOfType(BindingType type) {
    return Predicates.compose(Predicates.equalTo(type), BINDING_TYPE);
  }

  /** A function that returns {@link HasBindingType#bindingType()}. */
  static Function<HasBindingType, BindingType> BINDING_TYPE =
      new Function<HasBindingType, BindingType>() {
        @Override
        public BindingType apply(HasBindingType hasBindingType) {
          return hasBindingType.bindingType();
        }
      };
}
