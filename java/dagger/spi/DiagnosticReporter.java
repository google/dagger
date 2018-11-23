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

package dagger.spi;

import com.google.errorprone.annotations.FormatMethod;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.MaybeBinding;
import javax.tools.Diagnostic;

/**
 * An object that {@link BindingGraphPlugin}s can use to report diagnostics while visiting a {@link
 * BindingGraph}.
 *
 * <p>Note: This API is still experimental and will change.
 */
public interface DiagnosticReporter {
  /**
   * Reports a diagnostic for a component. For non-root components, includes information about the
   * path from the root component.
   */
  void reportComponent(Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String message);

  /**
   * Reports a diagnostic for a component. For non-root components, includes information about the
   * path from the root component.
   */
  @FormatMethod
  void reportComponent(
      Diagnostic.Kind diagnosticKind,
      ComponentNode componentNode,
      String messageFormat,
      Object firstArg,
      Object... moreArgs);

  /**
   * Reports a diagnostic for a binding or missing binding. Includes information about how the
   * binding is reachable from entry points.
   */
  void reportBinding(Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message);

  /**
   * Reports a diagnostic for a binding or missing binding. Includes information about how the
   * binding is reachable from entry points.
   */
  @FormatMethod
  void reportBinding(
      Diagnostic.Kind diagnosticKind,
      MaybeBinding binding,
      String messageFormat,
      Object firstArg,
      Object... moreArgs);

  /**
   * Reports a diagnostic for a dependency. Includes information about how the dependency is
   * reachable from entry points.
   */
  void reportDependency(
      Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message);

  /**
   * Reports a diagnostic for a dependency. Includes information about how the dependency is
   * reachable from entry points.
   */
  @FormatMethod
  void reportDependency(
      Diagnostic.Kind diagnosticKind,
      DependencyEdge dependencyEdge,
      String messageFormat,
      Object firstArg,
      Object... moreArgs);

  /** Reports a diagnostic for a subcomponent factory method. */
  void reportSubcomponentFactoryMethod(
      Diagnostic.Kind diagnosticKind,
      ChildFactoryMethodEdge childFactoryMethodEdge,
      String message);

  /** Reports a diagnostic for a subcomponent factory method. */
  @FormatMethod
  void reportSubcomponentFactoryMethod(
      Diagnostic.Kind diagnosticKind,
      ChildFactoryMethodEdge childFactoryMethodEdge,
      String messageFormat,
      Object firstArg,
      Object... moreArgs);
}
