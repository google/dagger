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
package dagger.internal.codegen.kythe;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
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
import dagger.Component;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingDeclaration;
import dagger.internal.codegen.binding.BindingGraphFactory;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.javac.JavacPluginModule;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.validation.InjectBindingRegistryModule;
import java.util.Optional;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A plugin which emits nodes and edges for <a href="https://github.com/google/dagger">Dagger</a>
 * specific code.
 */
@AutoService(Plugin.class)
public class DaggerKythePlugin extends Plugin.Scanner<Void, Void> {
  // TODO(ronshapiro): use flogger
  private static final Logger logger = Logger.getLogger(DaggerKythePlugin.class.getCanonicalName());
  private FactEmitter emitter;
  @Inject ComponentDescriptor.Factory componentDescriptorFactory;
  @Inject BindingGraphFactory bindingGraphFactory;
  @Inject XProcessingEnv xProcessingEnv;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    if (tree.sym != null) {
      XTypeElement type = toXProcessing(tree.sym, xProcessingEnv);
      if (type.hasAnyAnnotation(TypeNames.COMPONENT, TypeNames.PRODUCTION_COMPONENT)) {
        addNodesForGraph(
            bindingGraphFactory.create(
                componentDescriptorFactory.rootComponentDescriptor(type), false));
      }
    }
    return super.visitClassDef(tree, p);
  }

  private void addNodesForGraph(dagger.internal.codegen.binding.BindingGraph graph) {
    addDependencyEdges(graph.topLevelBindingGraph());

    // TODO(bcorso): Convert these to use the new BindingGraph
    addModuleEdges(graph);
    addChildComponentEdges(graph);
  }

  private void addDependencyEdges(BindingGraph graph) {
    for (DependencyEdge dependencyEdge : graph.dependencyEdges()) {
      DependencyRequest dependency = dependencyEdge.dependencyRequest();
      Node node = graph.network().incidentNodes(dependencyEdge).target();
      addEdgesForDependencyRequest(dependency, (BindingNode) node, graph);
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
      DependencyRequest dependency, BindingNode bindingNode, BindingGraph graph) {
    if (!dependency.requestElement().isPresent()) {
      return;
    }
    Binding binding = bindingNode.delegate();
    if (binding.bindingElement().isPresent()) {
      addDependencyEdge(dependency, binding);
    } else {
      for (Edge outEdge : graph.network().outEdges(bindingNode)) {
        if (outEdge instanceof DependencyEdge) {
          Node outNode = graph.network().incidentNodes(outEdge).target();
          addEdgesForDependencyRequest(dependency, (BindingNode) outNode, graph);
        }
      }
    }
    for (BindingDeclaration bindingDeclaration :
        Iterables.concat(
            bindingNode.multibindingDeclarations(),
            bindingNode.optionalBindingDeclarations())) {
      addDependencyEdge(dependency, bindingDeclaration);
    }
  }

  private void addDependencyEdge(
      DependencyRequest dependency, BindingDeclaration bindingDeclaration) {
    XElement requestElement = dependency.requestElement().get().xprocessing();
    XElement bindingElement = bindingDeclaration.bindingElement().get();
    Optional<VName> requestElementNode = jvmNode(requestElement, "request element");
    Optional<VName> bindingElementNode = jvmNode(bindingElement, "binding element");
    emitEdge(requestElementNode, "/inject/satisfiedby", bindingElementNode);
    // TODO(ronshapiro): emit facts about the component that satisfies the edge
  }

  private void addModuleEdges(dagger.internal.codegen.binding.BindingGraph graph) {
    Optional<VName> componentNode = jvmNode(graph.componentTypeElement(), "component");
    for (ModuleDescriptor module : graph.componentDescriptor().modules()) {
      Optional<VName> moduleNode = jvmNode(module.moduleElement(), "module");
      emitEdge(componentNode, "/inject/installsmodule", moduleNode);
    }
    graph.subgraphs().forEach(this::addModuleEdges);
  }

  private void addChildComponentEdges(dagger.internal.codegen.binding.BindingGraph graph) {
    Optional<VName> componentNode = jvmNode(graph.componentTypeElement(), "component");
    for (dagger.internal.codegen.binding.BindingGraph subgraph : graph.subgraphs()) {
      Optional<VName> subcomponentNode =
          jvmNode(subgraph.componentTypeElement(), "child component");
      emitEdge(componentNode, "/inject/childcomponent", subcomponentNode);
    }
    graph.subgraphs().forEach(this::addChildComponentEdges);
  }

  private Optional<VName> jvmNode(XElement element, String name) {
    Optional<VName> jvmNode =
        kytheGraph.getJvmNode((Symbol) toJavac(element)).map(KytheNode::getVName);
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
          .javacPluginModule(new JavacPluginModule(kytheGraph.getJavaContext()))
          .build()
          .inject(this);
    }
    super.run(compilationUnit, entrySets, kytheGraph);
  }

  @Singleton
  @Component(modules = {InjectBindingRegistryModule.class, JavacPluginModule.class})
  interface PluginComponent {
    void inject(DaggerKythePlugin plugin);
  }
}
