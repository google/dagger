/*
 * Copyright (C) 2015 The Dagger Authors.
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
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
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

  boolean isOfType(HasBindingType hasBindingType) {
    return this.equals(hasBindingType.bindingType());
  }

  static final ImmutableSet<BindingType> CONTRIBUTION_TYPES =
      Sets.immutableEnumSet(PROVISION, PRODUCTION);

  /** An object that is associated with a {@link BindingType}. */
  interface HasBindingType {
    /** The binding type of this object. */
    BindingType bindingType();
  }

  private final Class<?> frameworkClass;

  BindingType(Class<?> frameworkClass) {
    this.frameworkClass = frameworkClass;
  }

  /** The framework class associated with bindings of this type. */
  Class<?> frameworkClass() {
    return frameworkClass;
  }

  /** Returns the {@link #frameworkClass()} parameterized with a type. */
  ParameterizedTypeName frameworkClassOf(TypeName valueType) {
    return ParameterizedTypeName.get(ClassName.get(frameworkClass()), valueType);
  }
}
