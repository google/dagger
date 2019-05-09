/*
 * Copyright (C) 2017 The Dagger Authors.
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

// This must be in the dagger.internal.codegen package since Dagger doesn't expose its APIs publicly
// https://github.com/google/dagger/issues/773 could present an opportunity to put this somewhere in
// the regular kythe/java tree.
package dagger.internal.codegen;

import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;

import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.java.Plugin;
import com.google.devtools.kythe.proto.Storage.VName;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import dagger.BindsInstance;
import dagger.Component;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.producers.ProductionComponent;
import java.util.Optional;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;

/**
 * A plugin which emits nodes and edges for <a href="https://github.com/google/dagger">Dagger</a>
 * specific code.
 */
@AutoService(Plugin.class)
public class DaggerKythePlugin extends Plugin.Scanner<Void, Void> {
  // TODO(ronshapiro): use flogger
  private static final Logger logger = Logger.getLogger(DaggerKythePlugin.class.getCanonicalName());
  private FactEmitter emitter;
  @Inject ComponentDescriptorFactory componentDescriptorFactory;
  @Inject BindingGraphFactory bindingGraphFactory;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    if (tree.sym != null
        && isAnyAnnotationPresent(tree.sym, Component.class, ProductionComponent.class)) {
      addNodesForGraph(
          bindingGraphFactory.create(
              componentDescriptorFactory.rootComponentDescriptor(tree.sym), false));
    }
    return super.visitClassDef(tree, p);
  }

  private void addNodesForGraph(BindingGraph graph) {
    addDependencyEdges(graph);
    addModuleEdges(graph);
    addChildComponentEdges(graph);

    graph.subgraphs().forEach(this::addNodesForGraph);
  }

  private void addDependencyEdges(BindingGraph graph) {
    for (ResolvedBindings resolvedBinding : graph.resolvedBindings()) {
      for (Binding binding : resolvedBinding.bindings()) {
        for (DependencyRequest dependency : binding.explicitDependencies()) {
          addEdgesForDependencyRequest(dependency, dependency.key(), graph);
        }
      }
    }

    for (ComponentDescriptor.ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      componentMethod
          .dependencyRequest()
          .ifPresent(request -> addEdgesForDependencyRequest(request, request.key(), graph));
    }
  }

  /**
   * Add {@code /inject/satisfiedby} edges from {@code dependency}'s {@link
   * DependencyRequest#requestElement()} to any {@link BindingDeclaration#bindingElement() binding
   * elements} that satisfy the request.
   *
   * <p>This collapses requests for synthetic bindings so that a request for a multibound key
   * points to all of the contributions for the multibound object. It does so by recursively calling
   * this method, with each dependency's key as the {@code targetKey}.
   */
  private void addEdgesForDependencyRequest(
      DependencyRequest dependency, Key targetKey, BindingGraph graph) {
    if (!dependency.requestElement().isPresent()) {
      return;
    }
    BindingRequest request = bindingRequest(targetKey, dependency.kind());
    ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
    for (Binding binding : resolvedBindings.bindings()) {
      if (binding.bindingElement().isPresent()) {
        addDependencyEdge(dependency, binding);
      } else {
        for (DependencyRequest subsequentDependency : binding.explicitDependencies()) {
          addEdgesForDependencyRequest(dependency, subsequentDependency.key(), graph);
        }
      }
    }
    for (BindingDeclaration bindingDeclaration :
        Iterables.concat(
            resolvedBindings.multibindingDeclarations(),
            resolvedBindings.optionalBindingDeclarations())) {
      addDependencyEdge(dependency, bindingDeclaration);
    }
  }

  private void addDependencyEdge(
      DependencyRequest dependency, BindingDeclaration bindingDeclaration) {
    Element requestElement = dependency.requestElement().get();
    Element bindingElement = bindingDeclaration.bindingElement().get();
    Optional<VName> requestElementNode = jvmNode(requestElement, "request element");
    Optional<VName> bindingElementNode = jvmNode(bindingElement, "binding element");
    emitEdge(requestElementNode, "/inject/satisfiedby", bindingElementNode);
    // TODO(ronshapiro): emit facts about the component that satisfies the edge
  }

  private void addModuleEdges(BindingGraph graph) {
    Optional<VName> componentNode = jvmNode(graph.componentTypeElement(), "component");
    for (ModuleDescriptor module : graph.componentDescriptor().modules()) {
      Optional<VName> moduleNode = jvmNode(module.moduleElement(), "module");
      emitEdge(componentNode, "/inject/installsmodule", moduleNode);
    }
  }

  private void addChildComponentEdges(BindingGraph graph) {
    Optional<VName> componentNode = jvmNode(graph.componentTypeElement(), "component");
    for (BindingGraph subgraph : graph.subgraphs()) {
      Optional<VName> subcomponentNode =
          jvmNode(subgraph.componentTypeElement(), "child component");
      emitEdge(componentNode, "/inject/childcomponent", subcomponentNode);
    }
  }

  private Optional<VName> jvmNode(Element element, String name) {
    Optional<VName> jvmNode = kytheGraph.getJvmNode((Symbol) element).map(KytheNode::getVName);
    if (!jvmNode.isPresent()) {
      logger.warning(String.format("Missing JVM node for %s: %s", name, element));
    }
    return jvmNode;
  }

  private void emitEdge(Optional<VName> source, String edgeName, Optional<VName> target) {
    source.ifPresent(
        s -> target.ifPresent(t -> new EntrySet.Builder(s, edgeName, t).build().emit(emitter)));
  }

  @Override
  public void run(
      JCCompilationUnit compilationUnit, KytheEntrySets entrySets, KytheGraph kytheGraph) {
    if (bindingGraphFactory == null) {
      emitter = entrySets.getEmitter();
      DaggerDaggerKythePlugin_PluginComponent.builder()
          .context(kytheGraph.getJavaContext())
          .build()
          .inject(this);
    }
    super.run(compilationUnit, entrySets, kytheGraph);
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface PluginComponent {
    void inject(DaggerKythePlugin plugin);

    @Component.Builder
    interface Builder {
      @BindsInstance
      Builder context(Context context);

      PluginComponent build();
    }
  }
}
