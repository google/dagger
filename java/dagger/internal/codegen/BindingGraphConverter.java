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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.model.BindingGraphProxies.bindingNode;
import static dagger.model.BindingGraphProxies.childFactoryMethodEdge;
import static dagger.model.BindingGraphProxies.componentNode;
import static dagger.model.BindingGraphProxies.dependencyEdge;
import static dagger.model.BindingGraphProxies.subcomponentBuilderBindingEdge;
import static dagger.model.BindingKind.SUBCOMPONENT_BUILDER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingGraphProxies;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Converts {@link dagger.internal.codegen.BindingGraph}s to {@link dagger.model.BindingGraph}s. */
final class BindingGraphConverter extends ComponentTreeTraverser {

  private final MutableNetwork<Node, Edge> network =
      NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();

  private ComponentNode parentComponent;
  private ComponentNode currentComponent;

  private BindingGraphConverter(BindingGraph graph) {
    super(graph);
  }

  /**
   * Creates the external {@link dagger.model.BindingGraph} representing the given internal root
   * {@link dagger.internal.codegen.BindingGraph}.
   */
  static dagger.model.BindingGraph convert(BindingGraph graph) {
    BindingGraphConverter converter = new BindingGraphConverter(graph);
    converter.traverseComponents();
    return BindingGraphProxies.bindingGraph(converter.network);
  }

  @Override
  protected void visitComponent(BindingGraph graph) {
    ComponentNode grandparentNode = parentComponent;
    parentComponent = currentComponent;
    currentComponent = componentNode(componentTreePath().toComponentPath());
    network.addNode(currentComponent);
    super.visitComponent(graph);
    currentComponent = parentComponent;
    parentComponent = grandparentNode;
  }

  @Override
  protected void visitSubcomponentFactoryMethod(
      BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {
    network.addEdge(parentComponent, currentComponent, childFactoryMethodEdge(factoryMethod));
    super.visitSubcomponentFactoryMethod(graph, parent, factoryMethod);
  }

  @Override
  protected BindingGraphTraverser bindingGraphTraverser(
      ComponentTreePath componentTreePath, ComponentMethodDescriptor entryPointMethod) {
    return new BindingGraphVisitor(componentTreePath, entryPointMethod);
  }

  private final class BindingGraphVisitor extends BindingGraphTraverser {

    private Node current;

    BindingGraphVisitor(
        ComponentTreePath componentTreePath, ComponentMethodDescriptor entryPointMethod) {
      super(componentTreePath, entryPointMethod);
      current = currentComponent;
      network.addNode(current);
    }

    @Override
    protected void visitBinding(Binding binding, ComponentDescriptor owningComponent) {
      // TODO(dpb): Should we visit only bindings owned by the current component, since other
      // bindings will be visited in the parent?
      Node previous = current;
      current = newBindingNode(resolvedBindings(), binding, owningComponent);
      network.addNode(current);
      if (binding instanceof ContributionBinding) {
        ContributionBinding contributionBinding = (ContributionBinding) binding;
        if (contributionBinding.kind().equals(SUBCOMPONENT_BUILDER)) {
          ImmutableSet.Builder<TypeElement> modules = ImmutableSet.builder();
          for (SubcomponentDeclaration subcomponentDeclaration :
              resolvedBindings().subcomponentDeclarations()) {
            modules.add(subcomponentDeclaration.contributingModule().get());
          }
          network.addEdge(
              current,
              subcomponentNode(contributionBinding, owningComponent),
              subcomponentBuilderBindingEdge(modules.build()));
        }
      }
      if (network
          .edgesConnecting(previous, current)
          .stream()
          .flatMap(instancesOf(DependencyEdge.class))
          .noneMatch(e -> e.dependencyRequest().equals(dependencyRequest()))) {
        network.addEdge(
            previous, current, dependencyEdge(dependencyRequest(), atEntryPoint()));
        super.visitBinding(binding, owningComponent);
      }
      current = previous;
    }

    private ComponentNode subcomponentNode(
        ContributionBinding binding, ComponentDescriptor subcomponentParent) {
      checkArgument(binding.kind().equals(SUBCOMPONENT_BUILDER));
      TypeElement builderType = asTypeElement(binding.key().type());
      TypeElement subcomponentType = asType(builderType.getEnclosingElement());
      ComponentTreePath childPath =
          componentTreePath()
              .pathFromRootToAncestor(subcomponentParent)
              .childPath(subcomponentType);
      ComponentNode childNode = componentNode(childPath.toComponentPath());
      network.addNode(childNode);
      return childNode;
    }

    private BindingNode newBindingNode(
        ResolvedBindings resolvedBindings, Binding binding, ComponentDescriptor owningComponent) {
      ImmutableList.Builder<Element> associatedDeclarations = ImmutableList.builder();
      for (BindingDeclaration declaration :
          concat(
              resolvedBindings.multibindingDeclarations(),
              resolvedBindings.optionalBindingDeclarations(),
              resolvedBindings.subcomponentDeclarations())) {
        associatedDeclarations.add(declaration.bindingElement().get());
      }
      return bindingNode(
          componentTreePath().pathFromRootToAncestor(owningComponent).toComponentPath(),
          binding,
          associatedDeclarations.build());
    }
  }
}
