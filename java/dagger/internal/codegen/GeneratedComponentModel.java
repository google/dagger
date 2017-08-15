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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;

/** The model of the component being generated. */
interface GeneratedComponentModel {

  /** Returns the expression used to initialize a binding expression field. */
  // TODO(user): Move this method onto FrameworkInstanceBindingExpression and subtypes.
  CodeBlock getFieldInitialization(FrameworkInstanceBindingExpression bindingExpression);

  /** Adds the given field to the component. */
  void addField(FieldSpec fieldSpec);

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock);

  /**
   * Returns the {@code private} members injection method that injects objects with the {@code key}.
   */
  MethodSpec getMembersInjectionMethod(Key key);
}
