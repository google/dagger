/*
 * Copyright (C) 2017 The Dagger Authors.
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
import dagger.internal.codegen.javapoet.Expression;
import javax.lang.model.type.TypeMirror;

/** A binding expression for a subcomponent creator that just invokes the constructor. */
final class SubcomponentCreatorBindingExpression extends SimpleInvocationBindingExpression {
  private final TypeMirror creatorType;
  private final String creatorImplementationName;

  SubcomponentCreatorBindingExpression(
      ResolvedBindings resolvedBindings, String creatorImplementationName) {
    super(resolvedBindings);
    this.creatorType = resolvedBindings.key().type();
    this.creatorImplementationName = creatorImplementationName;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return Expression.create(creatorType, "new $L()", creatorImplementationName);
  }
}
