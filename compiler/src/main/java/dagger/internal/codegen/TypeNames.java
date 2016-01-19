/*
 * Copyright (C) 2016 Google, Inc.
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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;
import dagger.internal.Factory;

/**
 * Common names and convenience methods for JavaPoet {@link TypeName} usage.
 */
final class TypeNames {

  static final ClassName DOUBLE_CHECK_LAZY = ClassName.get(DoubleCheckLazy.class);
  static final ClassName FACTORY = ClassName.get(Factory.class);
  static final ClassName MEMBERS_INJECTOR = ClassName.get(MembersInjector.class);

  static ParameterizedTypeName membersInjectorOf(TypeName membersInjectorType) {
    return ParameterizedTypeName.get(MEMBERS_INJECTOR, membersInjectorType);
  }

  static ParameterizedTypeName factoryOf(TypeName factoryType) {
    return ParameterizedTypeName.get(FACTORY, factoryType);
  }

  private TypeNames() {}
}
