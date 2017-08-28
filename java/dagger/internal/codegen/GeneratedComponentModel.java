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

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;

/** The model of the component being generated. */
interface GeneratedComponentModel {

  /** Adds the given field to the component. */
  void addField(FieldSpec fieldSpec);

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock);

  /** Adds the given type to the component. */
  void addType(TypeSpec typeSpec);

  /** Returns the corresponding subcomponent name for the given subcomponent descriptor. */
  String getSubcomponentName(ComponentDescriptor subcomponentDescriptor);

  /**
   * Returns the {@code private} members injection method that injects objects with the {@code key}.
   */
  MethodSpec getMembersInjectionMethod(Key key);

  /**
   * Maybe wraps the given creation code block in single/double check or reference releasing
   * providers.
   */
  CodeBlock decorateForScope(CodeBlock factoryCreate, Optional<Scope> maybeScope);

  /**
   * The member-select expression for the {@link dagger.internal.ReferenceReleasingProviderManager}
   * object for a scope.
   */
  CodeBlock getReferenceReleasingProviderManagerExpression(Scope scope);

  // TODO(user): this and getDependencyArguments should go on ComponentBindingExpressions
  // once producerFromProvider fields are pushed into their corresponding binding expressions.
  // This cannot be done currently due to these expressions being created lazily.
  /** Returns a code block referencing the given dependency. */
  CodeBlock getDependencyExpression(FrameworkDependency frameworkDependency);

  /** Returns a list of code blocks for referencing all of the given binding's dependencies. */
  ImmutableList<CodeBlock> getBindingDependencyExpressions(Binding binding);
}
