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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import com.google.errorprone.annotations.FormatMethod;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MaybeBinding;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingGraph.SubcomponentCreatorBindingEdge;
import dagger.model.BindingKind;
import dagger.model.ComponentPath;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.Key.MultibindingContributionIdentifier;
import dagger.model.RequestKind;
import dagger.model.Scope;
import dagger.spi.DiagnosticReporter;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerTypeElement;
import java.util.Optional;
import javax.tools.Diagnostic;

/** A Utility class for converting to the {@link BindingGraph} used by external plugins. */
public final class ExternalBindingGraphConverter {
  private ExternalBindingGraphConverter() {}

  /** Returns a {@link DiagnosticReporter} from a {@link dagger.spi.DiagnosticReporter}. */
  public static DiagnosticReporter fromSpiModel(dagger.spi.model.DiagnosticReporter reporter) {
    return DiagnosticReporterImpl.create(reporter);
  }

  /** Returns a {@link BindingGraph} from a {@link dagger.spi.model.BindingGraph}. */
  public static BindingGraph fromSpiModel(dagger.spi.model.BindingGraph graph) {
    return BindingGraphImpl.create(graph);
  }

  private static ImmutableNetwork<Node, Edge> fromSpiModel(
      Network<dagger.spi.model.BindingGraph.Node, dagger.spi.model.BindingGraph.Edge> spiNetwork) {
    MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    ImmutableMap<dagger.spi.model.BindingGraph.Node, Node> fromSpiNodes =
        spiNetwork.nodes().stream()
            .collect(
                toImmutableMap(
                    spiNode -> spiNode,
                    ExternalBindingGraphConverter::fromSpiModel));

    for (Node node : fromSpiNodes.values()) {
      network.addNode(node);
    }
    for (dagger.spi.model.BindingGraph.Edge edge : spiNetwork.edges()) {
      EndpointPair<dagger.spi.model.BindingGraph.Node> edgePair = spiNetwork.incidentNodes(edge);
      network.addEdge(
          fromSpiNodes.get(edgePair.source()),
          fromSpiNodes.get(edgePair.target()),
          fromSpiModel(edge));
    }
    return ImmutableNetwork.copyOf(network);
  }

  private static Node fromSpiModel(dagger.spi.model.BindingGraph.Node node) {
    if (node instanceof dagger.spi.model.Binding) {
      return BindingNodeImpl.create((dagger.spi.model.Binding) node);
    } else if (node instanceof dagger.spi.model.BindingGraph.ComponentNode) {
      return ComponentNodeImpl.create((dagger.spi.model.BindingGraph.ComponentNode) node);
    } else if (node instanceof dagger.spi.model.BindingGraph.MissingBinding) {
      return MissingBindingImpl.create((dagger.spi.model.BindingGraph.MissingBinding) node);
    } else {
      throw new IllegalStateException("Unhandled node type: " + node.getClass());
    }
  }

  private static Edge fromSpiModel(dagger.spi.model.BindingGraph.Edge edge) {
    if (edge instanceof dagger.spi.model.BindingGraph.DependencyEdge) {
      return DependencyEdgeImpl.create((dagger.spi.model.BindingGraph.DependencyEdge) edge);
    } else if (edge instanceof dagger.spi.model.BindingGraph.ChildFactoryMethodEdge) {
      return ChildFactoryMethodEdgeImpl.create(
          (dagger.spi.model.BindingGraph.ChildFactoryMethodEdge) edge);
    } else if (edge instanceof dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge) {
      return SubcomponentCreatorBindingEdgeImpl.create(
          (dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge) edge);
    } else {
      throw new IllegalStateException("Unhandled edge type: " + edge.getClass());
    }
  }

  private static MultibindingContributionIdentifier fromSpiModel(
      dagger.spi.model.Key.MultibindingContributionIdentifier identifier) {
    return new MultibindingContributionIdentifier(
        identifier.bindingMethod(), identifier.contributingModule());
  }

  private static Key fromSpiModel(dagger.spi.model.Key key) {
    return Key.builder(key.type().java())
        .qualifier(key.qualifier().map(DaggerAnnotation::java))
        .multibindingContributionIdentifier(
            key.multibindingContributionIdentifier().isPresent()
                ? Optional.of(fromSpiModel(key.multibindingContributionIdentifier().get()))
                : Optional.empty())
        .build();
  }

  private static BindingKind fromSpiModel(dagger.spi.model.BindingKind bindingKind) {
    return BindingKind.valueOf(bindingKind.name());
  }

  private static RequestKind fromSpiModel(dagger.spi.model.RequestKind requestKind) {
    return RequestKind.valueOf(requestKind.name());
  }

  private static DependencyRequest fromSpiModel(dagger.spi.model.DependencyRequest request) {
    DependencyRequest.Builder builder =
        DependencyRequest.builder()
            .kind(fromSpiModel(request.kind()))
            .key(fromSpiModel(request.key()))
            .isNullable(request.isNullable());

    request.requestElement().ifPresent(e -> builder.requestElement(e.java()));
    return builder.build();
  }

  private static Scope fromSpiModel(dagger.spi.model.Scope scope) {
    return Scope.scope(scope.scopeAnnotation().java());
  }

  private static ComponentPath fromSpiModel(dagger.spi.model.ComponentPath path) {
    return ComponentPath.create(
        path.components().stream().map(DaggerTypeElement::java).collect(toImmutableList()));
  }

  private static dagger.spi.model.BindingGraph.ComponentNode toSpiModel(
      ComponentNode componentNode) {
    return ((ComponentNodeImpl) componentNode).spiDelegate();
  }

  private static dagger.spi.model.BindingGraph.MaybeBinding toSpiModel(MaybeBinding maybeBinding) {
    if (maybeBinding instanceof MissingBindingImpl) {
      return ((MissingBindingImpl) maybeBinding).spiDelegate();
    } else if (maybeBinding instanceof BindingNodeImpl) {
      return ((BindingNodeImpl) maybeBinding).spiDelegate();
    } else {
      throw new IllegalStateException("Unhandled binding type: " + maybeBinding.getClass());
    }
  }

  private static dagger.spi.model.BindingGraph.DependencyEdge toSpiModel(
      DependencyEdge dependencyEdge) {
    return ((DependencyEdgeImpl) dependencyEdge).spiDelegate();
  }

  private static dagger.spi.model.BindingGraph.ChildFactoryMethodEdge toSpiModel(
      ChildFactoryMethodEdge childFactoryMethodEdge) {
    return ((ChildFactoryMethodEdgeImpl) childFactoryMethodEdge).spiDelegate();
  }

  @AutoValue
  abstract static class ComponentNodeImpl implements ComponentNode {
    static ComponentNode create(dagger.spi.model.BindingGraph.ComponentNode componentNode) {
      return new AutoValue_ExternalBindingGraphConverter_ComponentNodeImpl(
          fromSpiModel(componentNode.componentPath()),
          componentNode.isSubcomponent(),
          componentNode.isRealComponent(),
          componentNode.entryPoints().stream()
              .map(ExternalBindingGraphConverter::fromSpiModel)
              .collect(toImmutableSet()),
          componentNode.scopes().stream()
              .map(ExternalBindingGraphConverter::fromSpiModel)
              .collect(toImmutableSet()),
          componentNode);
    }

    abstract dagger.spi.model.BindingGraph.ComponentNode spiDelegate();

    @Override
    public final String toString() {
      return spiDelegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingNodeImpl implements Binding {
    static Binding create(dagger.spi.model.Binding binding) {
      return new AutoValue_ExternalBindingGraphConverter_BindingNodeImpl(
          fromSpiModel(binding.key()),
          fromSpiModel(binding.componentPath()),
          binding.dependencies().stream()
              .map(ExternalBindingGraphConverter::fromSpiModel)
              .collect(toImmutableSet()),
          binding.bindingElement().map(DaggerElement::java),
          binding.contributingModule().map(DaggerTypeElement::java),
          binding.requiresModuleInstance(),
          binding.scope().map(ExternalBindingGraphConverter::fromSpiModel),
          binding.isNullable(),
          binding.isProduction(),
          fromSpiModel(binding.kind()),
          binding);
    }

    abstract dagger.spi.model.Binding spiDelegate();

    @Override
    public final String toString() {
      return spiDelegate().toString();
    }
  }

  @AutoValue
  abstract static class MissingBindingImpl extends MissingBinding {
    static MissingBinding create(dagger.spi.model.BindingGraph.MissingBinding missingBinding) {
      return new AutoValue_ExternalBindingGraphConverter_MissingBindingImpl(
          fromSpiModel(missingBinding.componentPath()),
          fromSpiModel(missingBinding.key()),
          missingBinding);
    }

    abstract dagger.spi.model.BindingGraph.MissingBinding spiDelegate();

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object o);
  }

  @AutoValue
  abstract static class DependencyEdgeImpl implements DependencyEdge {
    static DependencyEdge create(dagger.spi.model.BindingGraph.DependencyEdge dependencyEdge) {
      return new AutoValue_ExternalBindingGraphConverter_DependencyEdgeImpl(
          fromSpiModel(dependencyEdge.dependencyRequest()),
          dependencyEdge.isEntryPoint(),
          dependencyEdge);
    }

    abstract dagger.spi.model.BindingGraph.DependencyEdge spiDelegate();

    @Override
    public final String toString() {
      return spiDelegate().toString();
    }
  }

  @AutoValue
  abstract static class ChildFactoryMethodEdgeImpl implements ChildFactoryMethodEdge {
    static ChildFactoryMethodEdge create(
        dagger.spi.model.BindingGraph.ChildFactoryMethodEdge childFactoryMethodEdge) {
      return new AutoValue_ExternalBindingGraphConverter_ChildFactoryMethodEdgeImpl(
          childFactoryMethodEdge.factoryMethod().java(), childFactoryMethodEdge);
    }

    abstract dagger.spi.model.BindingGraph.ChildFactoryMethodEdge spiDelegate();

    @Override
    public final String toString() {
      return spiDelegate().toString();
    }
  }

  @AutoValue
  abstract static class SubcomponentCreatorBindingEdgeImpl
      implements SubcomponentCreatorBindingEdge {
    static SubcomponentCreatorBindingEdge create(
        dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge
            subcomponentCreatorBindingEdge) {
      return new AutoValue_ExternalBindingGraphConverter_SubcomponentCreatorBindingEdgeImpl(
          subcomponentCreatorBindingEdge.declaringModules().stream()
              .map(DaggerTypeElement::java)
              .collect(toImmutableSet()),
          subcomponentCreatorBindingEdge);
    }

    abstract dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge spiDelegate();

    @Override
    public final String toString() {
      return spiDelegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingGraphImpl extends BindingGraph {
    static BindingGraph create(dagger.spi.model.BindingGraph bindingGraph) {
      BindingGraphImpl bindingGraphImpl =
          new AutoValue_ExternalBindingGraphConverter_BindingGraphImpl(
              fromSpiModel(bindingGraph.network()), bindingGraph.isFullBindingGraph());

      bindingGraphImpl.componentNodesByPath =
          bindingGraphImpl.componentNodes().stream()
              .collect(toImmutableMap(ComponentNode::componentPath, node -> node));

      return bindingGraphImpl;
    }

    private ImmutableMap<ComponentPath, ComponentNode> componentNodesByPath;

    // This overrides dagger.model.BindingGraph with a more efficient implementation.
    @Override
    public Optional<ComponentNode> componentNode(ComponentPath componentPath) {
      return componentNodesByPath.containsKey(componentPath)
          ? Optional.of(componentNodesByPath.get(componentPath))
          : Optional.empty();
    }

    // This overrides dagger.model.BindingGraph to memoize the output.
    @Override
    @Memoized
    public ImmutableSetMultimap<Class<? extends Node>, ? extends Node> nodesByClass() {
      return super.nodesByClass();
    }
  }

  private static final class DiagnosticReporterImpl implements DiagnosticReporter {
    static DiagnosticReporterImpl create(dagger.spi.model.DiagnosticReporter reporter) {
      return new DiagnosticReporterImpl(reporter);
    }

    private final dagger.spi.model.DiagnosticReporter delegate;

    DiagnosticReporterImpl(dagger.spi.model.DiagnosticReporter delegate) {
      this.delegate = delegate;
    }

    @Override
    public void reportComponent(
        Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String message) {
      delegate.reportComponent(diagnosticKind, toSpiModel(componentNode), message);
    }

    @Override
    @FormatMethod
    public void reportComponent(
        Diagnostic.Kind diagnosticKind,
        ComponentNode componentNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      delegate.reportComponent(
          diagnosticKind, toSpiModel(componentNode), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message) {
      delegate.reportBinding(diagnosticKind, toSpiModel(binding), message);
    }

    @Override
    @FormatMethod
    public void reportBinding(
        Diagnostic.Kind diagnosticKind,
        MaybeBinding binding,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      delegate.reportBinding(
          diagnosticKind, toSpiModel(binding), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      delegate.reportDependency(diagnosticKind, toSpiModel(dependencyEdge), message);
    }

    @Override
    @FormatMethod
    public void reportDependency(
        Diagnostic.Kind diagnosticKind,
        DependencyEdge dependencyEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      delegate.reportDependency(
          diagnosticKind, toSpiModel(dependencyEdge), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      delegate.reportSubcomponentFactoryMethod(
          diagnosticKind, toSpiModel(childFactoryMethodEdge), message);
    }

    @Override
    @FormatMethod
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      delegate.reportSubcomponentFactoryMethod(
          diagnosticKind, toSpiModel(childFactoryMethodEdge), messageFormat, firstArg, moreArgs);
    }
  }
}
