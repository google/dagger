/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.asList;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.transitivelyEncloses;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.validation.DiagnosticMessageGenerator;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.MaybeBinding;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DaggerProcessingEnv;
import dagger.spi.model.DiagnosticReporter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.tools.Diagnostic;

/**
 * Combines many {@link BindingGraphPlugin} implementations. This helps reduce spam by combining
 * all of the messages that are reported on the root component.
 */
final class CompositeBindingGraphPlugin implements BindingGraphPlugin {
  @AssistedFactory
  interface Factory {
    CompositeBindingGraphPlugin create(ImmutableSet<BindingGraphPlugin> plugins);
  }

  private final ImmutableSet<BindingGraphPlugin> plugins;
  private final DiagnosticMessageGenerator.Factory messageGeneratorFactory;

  @AssistedInject
  CompositeBindingGraphPlugin(
      @Assisted ImmutableSet<BindingGraphPlugin> plugins,
      DiagnosticMessageGenerator.Factory messageGeneratorFactory) {
    this.plugins = plugins;
    this.messageGeneratorFactory = messageGeneratorFactory;
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    AggregatingDiagnosticReporter aggregatingDiagnosticReporter = new AggregatingDiagnosticReporter(
        bindingGraph, diagnosticReporter, messageGeneratorFactory.create(bindingGraph));
    plugins.forEach(plugin -> {
      aggregatingDiagnosticReporter.setCurrentPlugin(plugin.pluginName());
      plugin.visitGraph(bindingGraph, aggregatingDiagnosticReporter);
    });
    aggregatingDiagnosticReporter.report();
  }

  @Override
  public void init(DaggerProcessingEnv processingEnv, Map<String, String> options) {
    plugins.forEach(plugin -> plugin.init(processingEnv, options));
  }

  @Override
  public void onPluginEnd() {
    plugins.forEach(BindingGraphPlugin::onPluginEnd);
  }

  @Override
  public Set<String> supportedOptions() {
    return plugins.stream()
        .flatMap(plugin -> plugin.supportedOptions().stream())
        .collect(toImmutableSet());
  }

  @Override
  public String pluginName() {
    return "Dagger/Validation";
  }

  // TODO(erichang): This kind of breaks some of the encapsulation by relying on or repeating
  // logic within DiagnosticReporterImpl. Hopefully if the experiment for aggregated messages
  // goes well though this can be merged with that implementation.
  private static final class AggregatingDiagnosticReporter implements DiagnosticReporter {
    private final DiagnosticReporter delegate;
    private final BindingGraph graph;
    // Initialize with a new line so the first message appears below the reported component
    private final StringBuilder messageBuilder = new StringBuilder("\n");
    private final DiagnosticMessageGenerator messageGenerator;
    private Optional<Diagnostic.Kind> mergedDiagnosticKind = Optional.empty();
    private String currentPluginName = null;

    AggregatingDiagnosticReporter(
        BindingGraph graph,
        DiagnosticReporter delegate,
        DiagnosticMessageGenerator messageGenerator) {
      this.graph = graph;
      this.delegate = delegate;
      this.messageGenerator = messageGenerator;
    }

    /** Sets the currently running aggregated plugin. Used to add a diagnostic prefix. */
    void setCurrentPlugin(String pluginName) {
      currentPluginName = pluginName;
    }

    /** Reports all of the stored diagnostics. */
    void report() {
      if (mergedDiagnosticKind.isPresent()) {
        delegate.reportComponent(
            mergedDiagnosticKind.get(),
            graph.rootComponentNode(),
            PackageNameCompressor.compressPackagesInMessage(messageBuilder.toString()));
      }
    }

    @Override
    public void reportComponent(Diagnostic.Kind diagnosticKind, ComponentNode componentNode,
        String message) {
      addMessage(diagnosticKind, message);
      messageGenerator.appendComponentPathUnlessAtRoot(messageBuilder, componentNode);
    }

    @Override
    @FormatMethod
    public void reportComponent(
        Diagnostic.Kind diagnosticKind,
        ComponentNode componentNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportComponent(
          diagnosticKind, componentNode, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportBinding(Diagnostic.Kind diagnosticKind, MaybeBinding binding,
        String message) {
      addMessage(diagnosticKind,
          String.format("%s%s", message, messageGenerator.getMessage(binding)));
    }

    @Override
    @FormatMethod
    public void reportBinding(
        Diagnostic.Kind diagnosticKind,
        MaybeBinding binding,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportBinding(diagnosticKind, binding, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      addMessage(diagnosticKind,
          String.format("%s%s", message, messageGenerator.getMessage(dependencyEdge)));
    }

    @Override
    @FormatMethod
    public void reportDependency(
        Diagnostic.Kind diagnosticKind,
        DependencyEdge dependencyEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportDependency(
          diagnosticKind, dependencyEdge, formatMessage(messageFormat, firstArg, moreArgs));
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      // TODO(erichang): This repeats some of the logic in DiagnosticReporterImpl. Remove when
      // merged.
      if (transitivelyEncloses(
          graph.rootComponentNode().componentPath().currentComponent().xprocessing(),
          childFactoryMethodEdge.factoryMethod().xprocessing())) {
        // Let this pass through since it is not an error reported on the root component
        delegate.reportSubcomponentFactoryMethod(diagnosticKind, childFactoryMethodEdge, message);
      } else {
        addMessage(
            diagnosticKind,
            String.format(
                "[%s] %s",
                elementToString(childFactoryMethodEdge.factoryMethod().xprocessing()), message));
      }
    }

    @Override
    @FormatMethod
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      reportSubcomponentFactoryMethod(
          diagnosticKind, childFactoryMethodEdge, formatMessage(messageFormat, firstArg, moreArgs));
    }

    /** Adds a message to the stored aggregated message. */
    private void addMessage(Diagnostic.Kind diagnosticKind, String message) {
      checkNotNull(diagnosticKind);
      checkNotNull(message);
      checkState(currentPluginName != null);

      // Add a separator if this isn't the first message
      if (mergedDiagnosticKind.isPresent()) {
        messageBuilder.append("\n\n");
      }

      mergeDiagnosticKind(diagnosticKind);
      // Adds brackets as well as special color strings to make the string red and bold.
      messageBuilder.append(String.format("\033[1;31m[%s]\033[0m ", currentPluginName));
      messageBuilder.append(message);
    }

    private static String formatMessage(String messageFormat, Object firstArg, Object[] moreArgs) {
      return String.format(messageFormat, asList(firstArg, moreArgs).toArray());
    }

    private void mergeDiagnosticKind(Diagnostic.Kind diagnosticKind) {
      checkArgument(diagnosticKind != Diagnostic.Kind.MANDATORY_WARNING,
          "Dagger plugins should not be issuing mandatory warnings");
      if (!mergedDiagnosticKind.isPresent()) {
        mergedDiagnosticKind = Optional.of(diagnosticKind);
        return;
      }
      Diagnostic.Kind current = mergedDiagnosticKind.get();
      if (current == Diagnostic.Kind.ERROR || diagnosticKind == Diagnostic.Kind.ERROR) {
        mergedDiagnosticKind = Optional.of(Diagnostic.Kind.ERROR);
      } else if (current == Diagnostic.Kind.WARNING || diagnosticKind == Diagnostic.Kind.WARNING) {
        mergedDiagnosticKind = Optional.of(Diagnostic.Kind.WARNING);
      } else if (current == Diagnostic.Kind.NOTE || diagnosticKind == Diagnostic.Kind.NOTE) {
        mergedDiagnosticKind = Optional.of(Diagnostic.Kind.NOTE);
      } else {
        mergedDiagnosticKind = Optional.of(Diagnostic.Kind.OTHER);
      }
    }
  }
}
