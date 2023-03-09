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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toKS;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.xprocessing.XElements;
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
import dagger.spi.model.DaggerProcessingEnv;
import dagger.spi.model.DaggerProcessingEnv.Backend;
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

  public static BindingGraph toSpiModel(
      dagger.internal.codegen.model.BindingGraph graph, XProcessingEnv env) {
    return BindingGraphImpl.create(graph, env);
  }

  private static ImmutableNetwork<Node, Edge> toSpiModel(
      Network<
              dagger.internal.codegen.model.BindingGraph.Node,
              dagger.internal.codegen.model.BindingGraph.Edge>
          internalNetwork,
      XProcessingEnv env) {
    MutableNetwork<Node, Edge> network =
        NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

    ImmutableMap<dagger.internal.codegen.model.BindingGraph.Node, Node> fromInternalNodes =
        internalNetwork.nodes().stream()
            .collect(
                toImmutableMap(
                    node -> node, node -> SpiModelBindingGraphConverter.toSpiModel(node, env)));

    for (Node node : fromInternalNodes.values()) {
      network.addNode(node);
    }
    for (dagger.internal.codegen.model.BindingGraph.Edge edge : internalNetwork.edges()) {
      EndpointPair<dagger.internal.codegen.model.BindingGraph.Node> edgePair =
          internalNetwork.incidentNodes(edge);
      network.addEdge(
          fromInternalNodes.get(edgePair.source()),
          fromInternalNodes.get(edgePair.target()),
          toSpiModel(edge, env));
    }
    return ImmutableNetwork.copyOf(network);
  }

  private static Node toSpiModel(
      dagger.internal.codegen.model.BindingGraph.Node node, XProcessingEnv env) {
    if (node instanceof dagger.internal.codegen.model.Binding) {
      return BindingNodeImpl.create((dagger.internal.codegen.model.Binding) node, env);
    } else if (node instanceof dagger.internal.codegen.model.BindingGraph.ComponentNode) {
      return ComponentNodeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.ComponentNode) node, env);
    } else if (node instanceof dagger.internal.codegen.model.BindingGraph.MissingBinding) {
      return MissingBindingImpl.create(
          (dagger.internal.codegen.model.BindingGraph.MissingBinding) node, env);
    } else {
      throw new IllegalStateException("Unhandled node type: " + node.getClass());
    }
  }

  private static Edge toSpiModel(
      dagger.internal.codegen.model.BindingGraph.Edge edge, XProcessingEnv env) {
    if (edge instanceof dagger.internal.codegen.model.BindingGraph.DependencyEdge) {
      return DependencyEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.DependencyEdge) edge, env);
    } else if (edge instanceof dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge) {
      return ChildFactoryMethodEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge) edge, env);
    } else if (edge
        instanceof dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge) {
      return SubcomponentCreatorBindingEdgeImpl.create(
          (dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge) edge, env);
    } else {
      throw new IllegalStateException("Unhandled edge type: " + edge.getClass());
    }
  }

  private static Key toSpiModel(dagger.internal.codegen.model.Key key, XProcessingEnv env) {
    Key.Builder builder =
        Key.builder(toSpiModel(key.type().xprocessing(), env))
            .qualifier(key.qualifier().map(qualifier -> toSpiModel(qualifier.xprocessing(), env)));
    if (key.multibindingContributionIdentifier().isPresent()) {
      return builder
          .multibindingContributionIdentifier(
              toSpiModel(
                  key.multibindingContributionIdentifier().get().contributingModule().xprocessing(),
                  env),
              toSpiModel(
                  key.multibindingContributionIdentifier().get().bindingMethod().xprocessing(),
                  env))
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
      dagger.internal.codegen.model.DependencyRequest request, XProcessingEnv env) {
    DependencyRequest.Builder builder =
        DependencyRequest.builder()
            .kind(toSpiModel(request.kind()))
            .key(toSpiModel(request.key(), env))
            .isNullable(request.isNullable());

    request
        .requestElement()
        .ifPresent(e -> builder.requestElement(toSpiModel(e.xprocessing(), env)));
    return builder.build();
  }

  private static Scope toSpiModel(dagger.internal.codegen.model.Scope scope, XProcessingEnv env) {
    return Scope.scope(toSpiModel(scope.scopeAnnotation().xprocessing(), env));
  }

  private static ComponentPath toSpiModel(
      dagger.internal.codegen.model.ComponentPath path, XProcessingEnv env) {
    return ComponentPath.create(
        path.components().stream()
            .map(component -> toSpiModel(component.xprocessing(), env))
            .collect(toImmutableList()));
  }

  private static DaggerTypeElement toSpiModel(XTypeElement typeElement, XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerTypeElement.fromJavac(toJavac(typeElement));
      case KSP:
        return DaggerTypeElement.fromKsp(toKS(typeElement));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
  }

  private static DaggerType toSpiModel(XType type, XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerType.fromJavac(toJavac(type));
      case KSP:
        return DaggerType.fromKsp(toKS(type));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
  }

  static DaggerAnnotation toSpiModel(XAnnotation annotation, XProcessingEnv env) {
    DaggerTypeElement typeElement = toSpiModel(annotation.getTypeElement(), env);

    switch (env.getBackend()) {
      case JAVAC:
        return DaggerAnnotation.fromJavac(typeElement, toJavac(annotation));
      case KSP:
        return DaggerAnnotation.fromKsp(typeElement, toKS(annotation));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
  }

  private static DaggerElement toSpiModel(XElement element, XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerElement.fromJavac(toJavac(element));
      case KSP:
        return DaggerElement.fromKsp(XElements.toKSAnnotated(element));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
  }

  private static DaggerExecutableElement toSpiModel(
      XExecutableElement executableElement, XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerExecutableElement.fromJava(toJavac(executableElement));
      case KSP:
        return DaggerExecutableElement.fromKsp(toKS(executableElement));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
  }

  static DaggerProcessingEnv toSpiModel(XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerProcessingEnv.fromJavac(toJavac(env));
      case KSP:
        return DaggerProcessingEnv.fromKsp(toKS(env));
    }
    throw new IllegalStateException(
        String.format("Backend %s is not supported yet.", env.getBackend()));
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
        dagger.internal.codegen.model.BindingGraph.ComponentNode componentNode,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_ComponentNodeImpl(
          toSpiModel(componentNode.componentPath(), env),
          componentNode.isSubcomponent(),
          componentNode.isRealComponent(),
          componentNode.entryPoints().stream()
              .map(request -> SpiModelBindingGraphConverter.toSpiModel(request, env))
              .collect(toImmutableSet()),
          componentNode.scopes().stream()
              .map(request -> SpiModelBindingGraphConverter.toSpiModel(request, env))
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
    static Binding create(dagger.internal.codegen.model.Binding binding, XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_BindingNodeImpl(
          toSpiModel(binding.key(), env),
          toSpiModel(binding.componentPath(), env),
          binding.dependencies().stream()
              .map(request -> SpiModelBindingGraphConverter.toSpiModel(request, env))
              .collect(toImmutableSet()),
          binding.bindingElement().map(element -> toSpiModel(element.xprocessing(), env)),
          binding.contributingModule().map(module -> toSpiModel(module.xprocessing(), env)),
          binding.requiresModuleInstance(),
          binding.scope().map(scope -> SpiModelBindingGraphConverter.toSpiModel(scope, env)),
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
        dagger.internal.codegen.model.BindingGraph.MissingBinding missingBinding,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_MissingBindingImpl(
          toSpiModel(missingBinding.componentPath(), env),
          toSpiModel(missingBinding.key(), env),
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
        dagger.internal.codegen.model.BindingGraph.DependencyEdge dependencyEdge,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_DependencyEdgeImpl(
          toSpiModel(dependencyEdge.dependencyRequest(), env),
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
        dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge childFactoryMethodEdge,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_ChildFactoryMethodEdgeImpl(
          toSpiModel(childFactoryMethodEdge.factoryMethod().xprocessing(), env),
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
            subcomponentCreatorBindingEdge,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_SubcomponentCreatorBindingEdgeImpl(
          subcomponentCreatorBindingEdge.declaringModules().stream()
              .map(module -> toSpiModel(module.xprocessing(), env))
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
    static BindingGraph create(
        dagger.internal.codegen.model.BindingGraph bindingGraph, XProcessingEnv env) {
      BindingGraphImpl bindingGraphImpl =
          new AutoValue_SpiModelBindingGraphConverter_BindingGraphImpl(
              toSpiModel(bindingGraph.network(), env),
              bindingGraph.isFullBindingGraph(),
              Backend.valueOf(env.getBackend().name()));

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
