/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.spi.model;

import static com.google.common.collect.Lists.asList;

import com.google.errorprone.annotations.FormatMethod;
import dagger.spi.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.MaybeBinding;
import javax.tools.Diagnostic;

// TODO(bcorso): Move this into dagger/spi?
/**
 * An object that {@link BindingGraphPlugin}s can use to report diagnostics while visiting a {@link
 * BindingGraph}.
 *
 * <p>Note: This API is still experimental and will change.
 */
public abstract class DiagnosticReporter {
  /**
   * Reports a diagnostic for a component. For non-root components, includes information about the
   * path from the root component.
   */
  public abstract void reportComponent(
      Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String message);

  /**
   * Reports a diagnostic for a component. For non-root components, includes information about the
   * path from the root component.
   */
  @FormatMethod
  public final void reportComponent(
      Diagnostic.Kind diagnosticKind,
      ComponentNode componentNode,
      String messageFormat,
      Object firstArg,
      Object... moreArgs) {
    reportComponent(
        diagnosticKind, componentNode, formatMessage(messageFormat, firstArg, moreArgs));
  }

  /**
   * Reports a diagnostic for a binding or missing binding. Includes information about how the
   * binding is reachable from entry points.
   */
  public abstract void reportBinding(
      Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message);

  /**
   * Reports a diagnostic for a binding or missing binding. Includes information about how the
   * binding is reachable from entry points.
   */
  @FormatMethod
  public final void reportBinding(
      Diagnostic.Kind diagnosticKind,
      MaybeBinding binding,
      String messageFormat,
      Object firstArg,
      Object... moreArgs) {
    reportBinding(diagnosticKind, binding, formatMessage(messageFormat, firstArg, moreArgs));
  }

  /**
   * Reports a diagnostic for a dependency. Includes information about how the dependency is
   * reachable from entry points.
   */
  public abstract void reportDependency(
      Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message);

  /**
   * Reports a diagnostic for a dependency. Includes information about how the dependency is
   * reachable from entry points.
   */
  @FormatMethod
  public final void reportDependency(
      Diagnostic.Kind diagnosticKind,
      DependencyEdge dependencyEdge,
      String messageFormat,
      Object firstArg,
      Object... moreArgs) {
    reportDependency(
        diagnosticKind, dependencyEdge, formatMessage(messageFormat, firstArg, moreArgs));
  }

  /** Reports a diagnostic for a subcomponent factory method. */
  public abstract void reportSubcomponentFactoryMethod(
      Diagnostic.Kind diagnosticKind,
      ChildFactoryMethodEdge childFactoryMethodEdge,
      String message);

  /** Reports a diagnostic for a subcomponent factory method. */
  @FormatMethod
  public final void reportSubcomponentFactoryMethod(
      Diagnostic.Kind diagnosticKind,
      ChildFactoryMethodEdge childFactoryMethodEdge,
      String messageFormat,
      Object firstArg,
      Object... moreArgs) {
    reportSubcomponentFactoryMethod(
        diagnosticKind, childFactoryMethodEdge, formatMessage(messageFormat, firstArg, moreArgs));
  }

  private String formatMessage(String messageFormat, Object firstArg, Object[] moreArgs) {
    return String.format(messageFormat, asList(firstArg, moreArgs).toArray());
  }
}
