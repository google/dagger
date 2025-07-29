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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.xprocessing.XElements.transitivelyEncloses;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.MaybeBinding;
import dagger.internal.codegen.model.DiagnosticReporter;
import javax.inject.Inject;
import javax.tools.Diagnostic;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/** A factory for {@link DiagnosticReporter}s. */
// TODO(ronshapiro): If multiple plugins print errors on the same node/edge, should we condense the
// messages and only print the dependency trace once?
final class DiagnosticReporterFactory {
  private final XMessager messager;
  private final DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory;

  @Inject
  DiagnosticReporterFactory(
      XMessager messager, DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory) {
    this.messager = messager;
    this.diagnosticMessageGeneratorFactory = diagnosticMessageGeneratorFactory;
  }

  /** Creates a reporter for a binding graph and a plugin. */
  DiagnosticReporterImpl reporter(BindingGraph graph, String pluginName) {
    return new DiagnosticReporterImpl(graph, pluginName, /* reportErrorsAsWarnings= */ false);
  }

  /** Creates a reporter for a binding graph and a plugin that treats errors as warnings. */
  DiagnosticReporterImpl reporterWithErrorAsWarnings(BindingGraph graph, String pluginName) {
    return new DiagnosticReporterImpl(graph, pluginName, /* reportErrorsAsWarnings= */ true);
  }

  /**
   * A {@link DiagnosticReporter} that keeps track of which {@linkplain Diagnostic.Kind kinds} of
   * diagnostics were reported.
   */
  final class DiagnosticReporterImpl extends DiagnosticReporter {
    private final String plugin;
    private final XTypeElement rootComponent;
    private final boolean reportErrorsAsWarnings;
    private final ImmutableSet.Builder<Diagnostic.Kind> reportedDiagnosticKinds =
        ImmutableSet.builder();
    private final DiagnosticMessageGenerator diagnosticMessageGenerator;

    DiagnosticReporterImpl(BindingGraph graph, String plugin, boolean reportErrorsAsWarnings) {
      this.plugin = plugin;
      this.reportErrorsAsWarnings = reportErrorsAsWarnings;
      this.rootComponent =
          graph.rootComponentNode().componentPath().currentComponent().xprocessing();
      this.diagnosticMessageGenerator = diagnosticMessageGeneratorFactory.create(graph);
    }

    /** Returns which {@linkplain Diagnostic.Kind kinds} of diagnostics were reported. */
    ImmutableSet<Diagnostic.Kind> reportedDiagnosticKinds() {
      return reportedDiagnosticKinds.build();
    }

    @Override
    public void reportComponent(
        Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String messageFormat) {
      StringBuilder message = new StringBuilder(messageFormat);
      diagnosticMessageGenerator.appendComponentPathUnlessAtRoot(message, componentNode);
      // TODO(dpb): Report at the component node component.
      printMessage(diagnosticKind, message, rootComponent);
    }

    // TODO(ronshapiro): should this also include the binding element?
    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message) {
      printMessage(
          diagnosticKind, message + diagnosticMessageGenerator.getMessage(binding), rootComponent);
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      printMessage(
          diagnosticKind,
          message + diagnosticMessageGenerator.getMessage(dependencyEdge),
          rootComponent);
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      printMessage(diagnosticKind, message, childFactoryMethodEdge.factoryMethod().xprocessing());
    }

    private void printMessage(
        Diagnostic.Kind diagnosticKind,
        CharSequence message,
        @NullableDecl XElement elementToReport) {
      if (diagnosticKind.equals(ERROR) && reportErrorsAsWarnings) {
        diagnosticKind = Diagnostic.Kind.WARNING;
      }
      reportedDiagnosticKinds.add(diagnosticKind);
      StringBuilder fullMessage = new StringBuilder();
      appendBracketPrefix(fullMessage, plugin);

      if (elementToReport == null) {
        messager.printMessage(diagnosticKind, fullMessage.append(message).toString());
      } else {
        if (!transitivelyEncloses(rootComponent, elementToReport)) {
          appendBracketPrefix(fullMessage, elementToString(elementToReport));
          elementToReport = rootComponent;
        }
        messager.printMessage(
            diagnosticKind, fullMessage.append(message).toString(), elementToReport);
      }
    }

    private void appendBracketPrefix(StringBuilder message, String prefix) {
      message.append(String.format("[%s] ", prefix));
    }
  }
}
