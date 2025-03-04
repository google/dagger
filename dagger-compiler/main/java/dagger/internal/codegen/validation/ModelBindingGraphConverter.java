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
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DaggerElement;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.xprocessing.XElements;
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
import java.util.Optional;
import javax.tools.Diagnostic;

/** A Utility class for converting to the {@link BindingGraph} used by external plugins. */
public final class ModelBindingGraphConverter {
  private ModelBindingGraphConverter() {}

  /** Returns a {@link DiagnosticReporter} from a {@link dagger.spi.DiagnosticReporter}. */
  public static DiagnosticReporter toModel(
      dagger.internal.codegen.model.DiagnosticReporter reporter) {
    return DiagnosticReporterImpl.create(reporter);
  }

  /** Returns a {@link BindingGraph} from a {@link dagger.internal.codegen.model.BindingGraph}. */
  public static BindingGraph toModel(dagger.internal.codegen.model.BindingGraph graph) {
    return BindingGraphImpl.create(graph);
  }

  private static ImmutableNetwork<Node, Edge> toModel(
      Network<
              dagger.internal.codegen.model.BindingGraph.Node,
              dagger.internal.codegen.model.BindingGraph.Edge>
          internalNetwork) {
    MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    ImmutableMap<dagger.internal.codegen.model.BindingGraph.Node, Node> fromInternalNodes =
        internalNetwork.nodes().stream()
            .collect(toImmutableMap(node -> node, ModelBindingGraphConverter::toModel));

    for (Node node : fromInternalNodes.values()) {
      network.addNode(node);
    }
    for (dagger.internal.codegen.model.BindingGraph.Edge edge : internalNetwork.edges()) {
      EndpointPair<dagger.internal.codegen.model.BindingGraph.Node> edgePair =
          internalNetwork.incidentNodes(edge);
      network.addEdge(
          fromInternalNodes.get(edgePair.source()),
          fromInternalNodes.get(edgePair.target()),
          toModel(edge));
    }
    return ImmutableNetwork.copyOf(network);
  }

  private static Node toModel(dagger.internal.codegen.model.BindingGraph.Node node) {
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

  private static Edge toModel(dagger.internal.codegen.model.BindingGraph.Edge edge) {
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

  private static MultibindingContributionIdentifier toModel(
      dagger.internal.codegen.model.Key.MultibindingContributionIdentifier identifier) {
    return new MultibindingContributionIdentifier(
        XElements.getSimpleName(identifier.bindingMethod().xprocessing()),
        identifier.contributingModule().xprocessing().getQualifiedName());
  }

  private static Key toModel(dagger.internal.codegen.model.Key key) {
    return Key.builder(key.type().javac())
        .qualifier(key.qualifier().map(DaggerAnnotation::javac))
        .multibindingContributionIdentifier(
            key.multibindingContributionIdentifier().isPresent()
                ? Optional.of(toModel(key.multibindingContributionIdentifier().get()))
                : Optional.empty())
        .build();
  }

  private static BindingKind toModel(dagger.internal.codegen.model.BindingKind bindingKind) {
    return BindingKind.valueOf(bindingKind.name());
  }

  private static RequestKind toModel(dagger.internal.codegen.model.RequestKind requestKind) {
    return RequestKind.valueOf(requestKind.name());
  }

  private static DependencyRequest toModel(
      dagger.internal.codegen.model.DependencyRequest request) {
    DependencyRequest.Builder builder =
        DependencyRequest.builder()
            .kind(toModel(request.kind()))
            .key(toModel(request.key()))
            .isNullable(request.isNullable());

    request.requestElement().ifPresent(e -> builder.requestElement(e.javac()));
    return builder.build();
  }

  private static Scope toModel(dagger.internal.codegen.model.Scope scope) {
    return Scope.scope(scope.scopeAnnotation().javac());
  }

  private static ComponentPath toModel(dagger.internal.codegen.model.ComponentPath path) {
    return ComponentPath.create(
        path.components().stream().map(DaggerTypeElement::javac).collect(toImmutableList()));
  }

  private static dagger.internal.codegen.model.BindingGraph.ComponentNode toInternal(
      ComponentNode componentNode) {
    return ((ComponentNodeImpl) componentNode).delegate();
  }

  private static dagger.internal.codegen.model.BindingGraph.MaybeBinding toInternal(
      MaybeBinding maybeBinding) {
    if (maybeBinding instanceof MissingBindingImpl) {
      return ((MissingBindingImpl) maybeBinding).delegate();
    } else if (maybeBinding instanceof BindingNodeImpl) {
      return ((BindingNodeImpl) maybeBinding).delegate();
    } else {
      throw new IllegalStateException("Unhandled binding type: " + maybeBinding.getClass());
    }
  }

  private static dagger.internal.codegen.model.BindingGraph.DependencyEdge toInternal(
      DependencyEdge dependencyEdge) {
    return ((DependencyEdgeImpl) dependencyEdge).delegate();
  }

  private static dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge toInternal(
      ChildFactoryMethodEdge childFactoryMethodEdge) {
    return ((ChildFactoryMethodEdgeImpl) childFactoryMethodEdge).delegate();
  }

  @AutoValue
  abstract static class ComponentNodeImpl implements ComponentNode {
    static ComponentNode create(
        dagger.internal.codegen.model.BindingGraph.ComponentNode componentNode) {
      return new AutoValue_ModelBindingGraphConverter_ComponentNodeImpl(
          toModel(componentNode.componentPath()),
          componentNode.isSubcomponent(),
          componentNode.isRealComponent(),
          componentNode.entryPoints().stream()
              .map(ModelBindingGraphConverter::toModel)
              .collect(toImmutableSet()),
          componentNode.scopes().stream()
              .map(ModelBindingGraphConverter::toModel)
              .collect(toImmutableSet()),
          componentNode);
    }

    abstract dagger.internal.codegen.model.BindingGraph.ComponentNode delegate();

    @Override
    public final String toString() {
      return delegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingNodeImpl implements Binding {
    static Binding create(dagger.internal.codegen.model.Binding binding) {
      return new AutoValue_ModelBindingGraphConverter_BindingNodeImpl(
          toModel(binding.key()),
          toModel(binding.componentPath()),
          binding.dependencies().stream()
              .map(ModelBindingGraphConverter::toModel)
              .collect(toImmutableSet()),
          binding.bindingElement().map(DaggerElement::javac),
          binding.contributingModule().map(DaggerTypeElement::javac),
          binding.requiresModuleInstance(),
          binding.scope().map(ModelBindingGraphConverter::toModel),
          binding.isNullable(),
          binding.isProduction(),
          toModel(binding.kind()),
          binding);
    }

    abstract dagger.internal.codegen.model.Binding delegate();

    @Override
    public final String toString() {
      return delegate().toString();
    }
  }

  @AutoValue
  abstract static class MissingBindingImpl extends MissingBinding {
    static MissingBinding create(
        dagger.internal.codegen.model.BindingGraph.MissingBinding missingBinding) {
      return new AutoValue_ModelBindingGraphConverter_MissingBindingImpl(
          toModel(missingBinding.componentPath()), toModel(missingBinding.key()), missingBinding);
    }

    abstract dagger.internal.codegen.model.BindingGraph.MissingBinding delegate();

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
      return new AutoValue_ModelBindingGraphConverter_DependencyEdgeImpl(
          toModel(dependencyEdge.dependencyRequest()),
          dependencyEdge.isEntryPoint(),
          dependencyEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.DependencyEdge delegate();

    @Override
    public final String toString() {
      return delegate().toString();
    }
  }

  @AutoValue
  abstract static class ChildFactoryMethodEdgeImpl implements ChildFactoryMethodEdge {
    static ChildFactoryMethodEdge create(
        dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge childFactoryMethodEdge) {
      return new AutoValue_ModelBindingGraphConverter_ChildFactoryMethodEdgeImpl(
          childFactoryMethodEdge.factoryMethod().javac(), childFactoryMethodEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge delegate();

    @Override
    public final String toString() {
      return delegate().toString();
    }
  }

  @AutoValue
  abstract static class SubcomponentCreatorBindingEdgeImpl
      implements SubcomponentCreatorBindingEdge {
    static SubcomponentCreatorBindingEdge create(
        dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge
            subcomponentCreatorBindingEdge) {
      return new AutoValue_ModelBindingGraphConverter_SubcomponentCreatorBindingEdgeImpl(
          subcomponentCreatorBindingEdge.declaringModules().stream()
              .map(DaggerTypeElement::javac)
              .collect(toImmutableSet()),
          subcomponentCreatorBindingEdge);
    }

    abstract dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge delegate();

    @Override
    public final String toString() {
      return delegate().toString();
    }
  }

  @AutoValue
  abstract static class BindingGraphImpl extends BindingGraph {
    static BindingGraph create(dagger.internal.codegen.model.BindingGraph bindingGraph) {
      BindingGraphImpl bindingGraphImpl =
          new AutoValue_ModelBindingGraphConverter_BindingGraphImpl(
              toModel(bindingGraph.network()), bindingGraph.isFullBindingGraph());

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
    @FormatMethod
    public void reportComponent(
        Diagnostic.Kind diagnosticKind,
        ComponentNode componentNode,
        String messageFormat,
        Object firstArg,
        Object... moreArgs) {
      delegate.reportComponent(
          diagnosticKind, toInternal(componentNode), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportBinding(
        Diagnostic.Kind diagnosticKind, MaybeBinding binding, String message) {
      delegate.reportBinding(diagnosticKind, toInternal(binding), message);
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
          diagnosticKind, toInternal(binding), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportDependency(
        Diagnostic.Kind diagnosticKind, DependencyEdge dependencyEdge, String message) {
      delegate.reportDependency(diagnosticKind, toInternal(dependencyEdge), message);
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
          diagnosticKind, toInternal(dependencyEdge), messageFormat, firstArg, moreArgs);
    }

    @Override
    public void reportSubcomponentFactoryMethod(
        Diagnostic.Kind diagnosticKind,
        ChildFactoryMethodEdge childFactoryMethodEdge,
        String message) {
      delegate.reportSubcomponentFactoryMethod(
          diagnosticKind, toInternal(childFactoryMethodEdge), message);
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
          diagnosticKind, toInternal(childFactoryMethodEdge), messageFormat, firstArg, moreArgs);
    }
  }
}
