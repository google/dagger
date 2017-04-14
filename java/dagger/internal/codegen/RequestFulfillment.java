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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;

/**
 * A strategy interface for generating a {@link CodeBlock} that represents how a {@link Binding} is
 * used to satisfy a given {@link DependencyRequest}.
 */
abstract class RequestFulfillment {
  private final BindingKey bindingKey;

  RequestFulfillment(BindingKey bindingKey) {
    this.bindingKey = checkNotNull(bindingKey);
  }

  /** The key for which this instance can fulfill requests. */
  final BindingKey bindingKey() {
    return bindingKey;
  }

  /**
   * Returns the {@link CodeBlock} that implements the operation represented by the {@link
   * DependencyRequest request} from the {@code requestingClass}.
   */
  abstract CodeBlock getSnippetForDependencyRequest(
      DependencyRequest request, ClassName requestingClass);

  /**
   * Returns the {@link CodeBlock} that references the {@link FrameworkDependency} as accessed from
   * the {@code requestingClass}.
   */
  abstract CodeBlock getSnippetForFrameworkDependency(
      FrameworkDependency frameworkDependency, ClassName requestingClass);
}
