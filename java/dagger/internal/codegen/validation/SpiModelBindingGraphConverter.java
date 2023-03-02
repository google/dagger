/*
 * Copyright (C) 2023 The Dagger Authors.
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
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraph.DependencyEdge;
import dagger.spi.model.BindingGraph.Edge;
import dagger.spi.model.BindingGraph.MaybeBinding;
import dagger.spi.model.BindingGraph.MissingBinding;
import dagger.spi.model.BindingGraph.Node;
import dagger.spi.model.BindingGraph.SubcomponentCreatorBindingEdge;
import dagger.spi.model.BindingKind;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerExecutableElement;
import dagger.spi.model.DaggerType;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import dagger.spi.model.Scope;
import java.util.Optional;
import javax.tools.Diagnostic;

/** A Utility class for converting to the {@link BindingGraph} used by external plugins. */
public final class SpiModelBindingGraphConverter {
  private SpiModelBindingGraphConverter() {}

  public static DiagnosticReporter toSpiModel(
      dagger.internal.codegen.model.DiagnosticReporter reporter) {
    return DiagnosticReporterImpl.create(reporter);
  }

  public static BindingGraph toSpiModel(dagger.internal.codegen.model.BindingGraph graph) {
    return BindingGraphImpl.create(graph);
  }

  private static ImmutableNetwork<Node, Edge> toSpiModel(
      Network<
              dagger.internal.codegen.model.BindingGraph.Node,
              dagger.internal.codegen.model.BindingGraph.Edge>
          internalNetwork) {
    MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    ImmutableMap<dagger.internal.codegen.model.BindingGraph.Node, Node> fromInternalNodes =
        internalNetwork.nodes().stream()
            .collect(toImmutableMap(node -> node, SpiModelBindingGraphConverter::toSpiModel));

    for (Node node : fromInternalNodes.values()) {
      network.addNode(node);
    }
    for (dagger.internal.codegen.model.BindingGraph.Edge edge : internalNetwork.edges()) {
      EndpointPair<dagger.internal.codegen.model.BindingGraph.Node> edgePair =
          internalNetwork.incidentNodes(edge);
      network.addEdge(
          fromInternalNodes.get(edgePair.source()),
          fromInternalNodes.get(edgePair.target()),
          toSpiModel(edge));
    }
    return ImmutableNetwork.copyOf(network);
  }

  private static Node toSpiModel(dagger.internal.codegen.model.BindingGraph.Node node) {
    if (node instanceof dagger.internal.codegen.model.Binding) {
      return BindingNodeImpl.create((dagger.internal.codegen.model.Binding) node);
    } else if (node instanceof dagger.internal.codegen.model.BindingGraph.ComponentNode) {
      return ComponentNodeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.ComponentNode) node);
    } else if (node instanceof dagger.internal.codegen.model.BindingGraph.MissingBinding) {
      return MissingBindingImpl.create(
          (dagger.internal.codegen.model.BindingGraph.MissingBinding) node);
    } else {
      throw new IllegalStateException("Unhandled node type: " + node.getClass());
    }
  }

  private static Edge toSpiModel(dagger.internal.codegen.model.BindingGraph.Edge edge) {
    if (edge instanceof dagger.internal.codegen.model.BindingGraph.DependencyEdge) {
      return DependencyEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.DependencyEdge) edge);
    } else if (edge instanceof dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge) {
      return ChildFactoryMethodEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge) edge);
    } else if (edge
        instanceof dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge) {
      return SubcomponentCreatorBindingEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge) edge);
    } else {
      throw new IllegalStateException("Unhandled edge type: " + edge.getClass());
    }
  }

  private static Key toSpiModel(dagger.internal.codegen.model.Key key) {
    Key.Builder builder =
        Key.builder(DaggerType.from(key.type().xprocessing()))
            .qualifier(
                key.qualifier().map(qualifier -> DaggerAnnotation.from(qualifier.xprocessing())));
    if (key.multibindingContributionIdentifier().isPresent()) {
      return builder
          .multibindingContributionIdentifier(
              DaggerTypeElement.from(
                  key.multibindingContributionIdentifier()
                      .get()
                      .contributingModule()
                      .xprocessing()),
              DaggerExecutableElement.from(
                  key.multibindingContributionIdentifier().get().bindingMethod().xprocessing()))
          .build();
    }
    return builder.build().withoutMultibindingContributionIdentifier();
  }

  private static BindingKind toSpiModel(dagger.internal.codegen.model.BindingKind bindingKind) {
    return BindingKind.valueOf(bindingKind.name());
  }

  private static RequestKind toSpiModel(dagger.internal.codegen.model.RequestKind requestKind) {
    return RequestKind.valueOf(requestKind.name());
  }

  @SuppressWarnings("CheckReturnValue")
  private static DependencyRequest toSpiModel(
      dagger.internal.codegen.model.DependencyRequest request) {
    DependencyRequest.Builder builder =
        DependencyRequest.builder()
            .kind(toSpiModel(request.kind()))
            .key(toSpiModel(request.key()))
            .isNullable(request.isNullable());

    request
        .requestElement()
        .ifPresent(e -> builder.requestElement(DaggerElement.from(e.xprocessing())));
    return builder.build();
  }

  private static Scope toSpiModel(dagger.internal.codegen.model.Scope scope) {
    return Scope.scope(DaggerAnnotation.from(scope.scopeAnnotation().xprocessing()));
  }

  private static ComponentPath toSpiModel(dagger.internal.codegen.model.ComponentPath path) {
    return ComponentPath.create(
        path.components().stream()
            .map(component -> DaggerTypeElement.from(component.xprocessing()))
            .collect(toImmutableList()));
  }

  private static dagger.internal.codegen.model.BindingGraph.ComponentNode toInternal(
      ComponentNode componentNode) {
    return ((ComponentNodeImpl) componentNode).internalDelegate();
  }

  private static dagger.internal.codegen.model.BindingGraph.MaybeBinding toInternal(
      MaybeBinding maybeBinding) {
    if (maybeBinding instanceof MissingBindingImpl) {
      return ((MissingBindingImpl) maybeBinding).internalDelegate();
    } else if (maybeBinding instanceof BindingNodeImpl) {
      return ((BindingNodeImpl) maybeBinding).internalDelegate();
    } else {
      throw new IllegalStateException("Unhandled binding type: " + maybeBinding.getClass());
    }
  }

  private static dagger.internal.codegen.model.BindingGraph.DependencyEdge toInternal(
      DependencyEdge dependencyEdge) {
    return ((DependencyEdgeImpl) dependencyEdge).internalDelegate();
  }

  private static dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge toInternal(
      ChildFactoryMethodEdge childFactoryMethodEdge) {
    return ((ChildFactoryMethodEdgeImpl) childFactoryMethodEdge).internalDelegate();
  }

  @AutoValue
  abstract static class ComponentNodeImpl implements ComponentNode {
    static ComponentNode create(
        dagger.internal.codegen.model.BindingGraph.ComponentNode componentNode) {
      return new AutoValue_SpiModelBindingGraphConverter_ComponentNodeImpl(
          toSpiModel(componentNode.componentPath()),
          componentNode.isSubcomponent(),
          componentNode.isRealComponent(),
          componentNode.entryPoints().stream()
              .map(SpiModelBindingGraphConverter::toSpiModel)
              .collect(toImmutableSet()),
          componentNode.scopes().stream()
              .map(SpiModelBindingGraphConverter::toSpiModel)
              .collect(toImmutableSet()),
          componentNode);
    }

    abstract dagger.internal.codegen.model.BindingGraph.ComponentNode internalDelegate();

    @Override
    public final String toString() {
      return internalDelegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingNodeImpl implements Binding {
    static Binding create(dagger.internal.codegen.model.Binding binding) {
      return new AutoValue_SpiModelBindingGraphConverter_BindingNodeImpl(
          toSpiModel(binding.key()),
          toSpiModel(binding.componentPath()),
          binding.dependencies().stream()
              .map(SpiModelBindingGraphConverter::toSpiModel)
              .collect(toImmutableSet()),
          binding.bindingElement().map(element -> DaggerElement.from(element.xprocessing())),
          binding.contributingModule().map(module -> DaggerTypeElement.from(module.xprocessing())),
          binding.requiresModuleInstance(),
          binding.scope().map(SpiModelBindingGraphConverter::toSpiModel),
          binding.isNullable(),
          binding.isProduction(),
          toSpiModel(binding.kind()),
          binding);
    }

    abstract dagger.internal.codegen.model.Binding internalDelegate();

    @Override
    public final String toString() {
      return internalDelegate().toString();
    }
  }

  @AutoValue
  abstract static class MissingBindingImpl extends MissingBinding {
    static MissingBinding create(
        dagger.internal.codegen.model.BindingGraph.MissingBinding missingBinding) {
      return new AutoValue_SpiModelBindingGraphConverter_MissingBindingImpl(
          toSpiModel(missingBinding.componentPath()),
          toSpiModel(missingBinding.key()),
          missingBinding);
    }

    abstract dagger.internal.codegen.model.BindingGraph.MissingBinding internalDelegate();

    @Memoized
    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object o);
  }

  @AutoValue
  abstract static class DependencyEdgeImpl implements DependencyEdge {
    static DependencyEdge create(
        dagger.internal.codegen.model.BindingGraph.DependencyEdge dependencyEdge) {
      return new AutoValue_SpiModelBindingGraphConverter_DependencyEdgeImpl(
          toSpiModel(dependencyEdge.dependencyRequest()),
          dependencyEdge.isEntryPoint(),
          dependencyEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.DependencyEdge internalDelegate();

    @Override
    public final String toString() {
      return internalDelegate().toString();
    }
  }

  @AutoValue
  abstract static class ChildFactoryMethodEdgeImpl implements ChildFactoryMethodEdge {
    static ChildFactoryMethodEdge create(
        dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge childFactoryMethodEdge) {
      return new AutoValue_SpiModelBindingGraphConverter_ChildFactoryMethodEdgeImpl(
          DaggerExecutableElement.from(childFactoryMethodEdge.factoryMethod().xprocessing()),
          childFactoryMethodEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge internalDelegate();

    @Override
    public final String toString() {
      return internalDelegate().toString();
    }
  }

  @AutoValue
  abstract static class SubcomponentCreatorBindingEdgeImpl
      implements SubcomponentCreatorBindingEdge {
    static SubcomponentCreatorBindingEdge create(
        dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge
            subcomponentCreatorBindingEdge) {
      return new AutoValue_SpiModelBindingGraphConverter_SubcomponentCreatorBindingEdgeImpl(
          subcomponentCreatorBindingEdge.declaringModules().stream()
              .map(module -> DaggerTypeElement.from(module.xprocessing()))
              .collect(toImmutableSet()),
          subcomponentCreatorBindingEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge
        internalDelegate();

    @Override
    public final String toString() {
      return internalDelegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingGraphImpl extends BindingGraph {
    static BindingGraph create(dagger.internal.codegen.model.BindingGraph bindingGraph) {
      BindingGraphImpl bindingGraphImpl =
          new AutoValue_SpiModelBindingGraphConverter_BindingGraphImpl(
              toSpiModel(bindingGraph.network()), bindingGraph.isFullBindingGraph());

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

  private static final class DiagnosticReporterImpl extends DiagnosticReporter {
    static DiagnosticReporterImpl create(
        dagger.internal.codegen.model.DiagnosticReporter reporter) {
      return new DiagnosticReporterImpl(reporter);
    }

    private final dagger.internal.codegen.model.DiagnosticReporter delegate;

    DiagnosticReporterImpl(dagger.internal.codegen.model.DiagnosticReporter delegate) {
      this.delegate = delegate;
    }

    @Override
    public void reportComponent(
        Diagnostic.Kind diagnosticKind, ComponentNode componentNode, String message) {
      delegate.reportComponent(diagnosticKind, toInternal(componentNode), message);
    }

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message) {
      delegate.reportBinding(diagnosticKind, toInternal(binding), message);
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      delegate.reportDependency(diagnosticKind, toInternal(dependencyEdge), message);
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      delegate.reportSubcomponentFactoryMethod(
          diagnosticKind, toInternal(childFactoryMethodEdge), message);
    }
  }
}
