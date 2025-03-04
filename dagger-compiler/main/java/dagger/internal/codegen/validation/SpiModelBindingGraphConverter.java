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

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toKS;
import static androidx.room.compiler.processing.compat.XConverters.toKSResolver;
import static com.google.common.base.Preconditions.checkState;
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
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import com.google.devtools.ksp.symbol.KSType;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
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
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
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

  private static Key toSpiModel(dagger.internal.codegen.model.Key key) {
    Key.Builder builder =
        Key.builder(toSpiModel(key.type().xprocessing()))
            .qualifier(key.qualifier().map(qualifier -> toSpiModel(qualifier.xprocessing())));
    if (key.multibindingContributionIdentifier().isPresent()) {
      return builder
          .multibindingContributionIdentifier(
              toSpiModel(
                  key.multibindingContributionIdentifier()
                      .get()
                      .contributingModule()
                      .xprocessing()),
              toSpiModel(
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

    request.requestElement().ifPresent(e -> builder.requestElement(toSpiModel(e.xprocessing())));
    return builder.build();
  }

  private static Scope toSpiModel(dagger.internal.codegen.model.Scope scope) {
    return Scope.scope(toSpiModel(scope.scopeAnnotation().xprocessing()));
  }

  private static ComponentPath toSpiModel(dagger.internal.codegen.model.ComponentPath path) {
    return ComponentPath.create(
        path.components().stream()
            .map(component -> toSpiModel(component.xprocessing()))
            .collect(toImmutableList()));
  }

  private static DaggerTypeElement toSpiModel(XTypeElement typeElement) {
    return DaggerTypeElementImpl.from(typeElement);
  }

  private static DaggerType toSpiModel(XType type) {
    return DaggerTypeImpl.from(type);
  }

  static DaggerAnnotation toSpiModel(XAnnotation annotation) {
    return DaggerAnnotationImpl.from(annotation);
  }

  private static DaggerElement toSpiModel(XElement element) {
    return DaggerElementImpl.from(element);
  }

  private static DaggerExecutableElement toSpiModel(XExecutableElement executableElement) {
    return DaggerExecutableElementImpl.from(executableElement);
  }

  static DaggerProcessingEnv toSpiModel(XProcessingEnv env) {
    return DaggerProcessingEnvImpl.from(env);
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
    static Binding create(dagger.internal.codegen.model.Binding binding, XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_BindingNodeImpl(
          toSpiModel(binding.key()),
          toSpiModel(binding.componentPath()),
          binding.dependencies().stream()
              .map(SpiModelBindingGraphConverter::toSpiModel)
              .collect(toImmutableSet()),
          binding.bindingElement().map(element -> toSpiModel(element.xprocessing())),
          binding.contributingModule().map(module -> toSpiModel(module.xprocessing())),
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
        dagger.internal.codegen.model.BindingGraph.MissingBinding missingBinding,
        XProcessingEnv env) {
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
        dagger.internal.codegen.model.BindingGraph.DependencyEdge dependencyEdge,
        XProcessingEnv env) {
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
        dagger.internal.codegen.model.BindingGraph.ChildFactoryMethodEdge childFactoryMethodEdge,
        XProcessingEnv env) {
      return new AutoValue_SpiModelBindingGraphConverter_ChildFactoryMethodEdgeImpl(
          toSpiModel(childFactoryMethodEdge.factoryMethod().xprocessing()), childFactoryMethodEdge);
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
              .map(module -> toSpiModel(module.xprocessing()))
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

  @AutoValue
  abstract static class DaggerElementImpl extends DaggerElement {
    public static DaggerElement from(XElement element) {
      return new AutoValue_SpiModelBindingGraphConverter_DaggerElementImpl(element);
    }

    abstract XElement element();

    @Override
    public Element javac() {
      checkIsJavac(backend());
      return toJavac(element());
    }

    @Override
    public KSAnnotated ksp() {
      checkIsKsp(backend());
      return toKS(element());
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(getProcessingEnv(element()));
    }

    @Override
    public final String toString() {
      return XElements.toStableString(element());
    }
  }

  @AutoValue
  abstract static class DaggerTypeElementImpl extends DaggerTypeElement {
    public static DaggerTypeElement from(XTypeElement element) {
      return new AutoValue_SpiModelBindingGraphConverter_DaggerTypeElementImpl(element);
    }

    abstract XTypeElement element();

    @Override
    public TypeElement javac() {
      checkIsJavac(backend());
      return toJavac(element());
    }

    @Override
    public KSClassDeclaration ksp() {
      checkIsKsp(backend());
      return toKS(element());
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(getProcessingEnv(element()));
    }

    @Override
    public final String toString() {
      return XElements.toStableString(element());
    }
  }

  @AutoValue
  abstract static class DaggerTypeImpl extends DaggerType {
    public static DaggerType from(XType type) {
      return new AutoValue_SpiModelBindingGraphConverter_DaggerTypeImpl(
          XTypes.equivalence().wrap(type));
    }

    abstract Equivalence.Wrapper<XType> type();

    @Override
    public TypeMirror javac() {
      checkIsJavac(backend());
      return toJavac(type().get());
    }

    @Override
    public KSType ksp() {
      checkIsKsp(backend());
      return toKS(type().get());
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(getProcessingEnv(type().get()));
    }

    @Override
    public final String toString() {
      return XTypes.toStableString(type().get());
    }
  }

  @AutoValue
  abstract static class DaggerAnnotationImpl extends DaggerAnnotation {
    public static DaggerAnnotation from(XAnnotation annotation) {
      return new AutoValue_SpiModelBindingGraphConverter_DaggerAnnotationImpl(
          XAnnotations.equivalence().wrap(annotation));
    }

    abstract Equivalence.Wrapper<XAnnotation> annotation();

    @Override
    public DaggerTypeElement annotationTypeElement() {
      return DaggerTypeElementImpl.from(annotation().get().getTypeElement());
    }

    @Override
    public AnnotationMirror javac() {
      checkIsJavac(backend());
      return toJavac(annotation().get());
    }

    @Override
    public KSAnnotation ksp() {
      checkIsKsp(backend());
      return toKS(annotation().get());
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(getProcessingEnv(annotation().get()));
    }

    @Override
    public final String toString() {
      return XAnnotations.toStableString(annotation().get());
    }
  }

  @AutoValue
  abstract static class DaggerExecutableElementImpl extends DaggerExecutableElement {
    public static DaggerExecutableElement from(XExecutableElement executableElement) {
      return new AutoValue_SpiModelBindingGraphConverter_DaggerExecutableElementImpl(
          executableElement);
    }

    abstract XExecutableElement executableElement();

    @Override
    public ExecutableElement javac() {
      checkIsJavac(backend());
      return toJavac(executableElement());
    }

    @Override
    public KSDeclaration ksp() {
      checkIsKsp(backend());
      return isMethod(executableElement())
              && XElements.asMethod(executableElement()).isKotlinPropertyMethod()
          ? (KSPropertyDeclaration) toKS((XElement) executableElement())
          : toKS(executableElement());
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(getProcessingEnv(executableElement()));
    }

    @Override
    public final String toString() {
      return XElements.toStableString(executableElement());
    }
  }

  private static class DaggerProcessingEnvImpl extends DaggerProcessingEnv {
    private final XProcessingEnv env;

    public static DaggerProcessingEnv from(XProcessingEnv env) {
      return new DaggerProcessingEnvImpl(env);
    }

    DaggerProcessingEnvImpl(XProcessingEnv env) {
      this.env = env;
    }

    @Override
    public ProcessingEnvironment javac() {
      checkIsJavac(backend());
      return toJavac(env);
    }

    @Override
    public SymbolProcessorEnvironment ksp() {
      checkIsKsp(backend());
      return toKS(env);
    }

    @Override
    public Resolver resolver() {
      return toKSResolver(env);
    }

    @Override
    public DaggerProcessingEnv.Backend backend() {
      return getBackend(env);
    }
  }

  private static void checkIsJavac(DaggerProcessingEnv.Backend backend) {
    checkState(
        backend == DaggerProcessingEnv.Backend.JAVAC,
        "Expected JAVAC backend but was: %s", backend);
  }

  private static void checkIsKsp(DaggerProcessingEnv.Backend backend) {
    checkState(
        backend == DaggerProcessingEnv.Backend.KSP,
        "Expected KSP backend but was: %s", backend);
  }

  private static DaggerProcessingEnv.Backend getBackend(XProcessingEnv env) {
    switch (env.getBackend()) {
      case JAVAC:
        return DaggerProcessingEnv.Backend.JAVAC;
      case KSP:
        return DaggerProcessingEnv.Backend.KSP;
    }
    throw new AssertionError(String.format("Unexpected backend %s", env.getBackend()));
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
