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
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;

/** The model of the component being generated. */
interface GeneratedComponentModel {

  /** Adds the given field to the component. */
  void addField(FieldSpec fieldSpec);

  /** Adds the given method to the component. */
  void addMethod(MethodSpec methodSpec);

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock);

  /** Adds the given type to the component. */
  void addType(TypeSpec typeSpec);

  /** Returns a new, unique field name for the component based on the given name. */
  String getUniqueFieldName(String name);

  /** Returns a new, unique method name for the component based on the given name. */
  String getUniqueMethodName(String name);

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

  /**
   * Returns {@code true} if {@code scope} is in {@link
   * BindingGraph#scopesRequiringReleasableReferenceManagers()} for the root graph.
   */
  boolean requiresReleasableReferences(Scope scope);
}
