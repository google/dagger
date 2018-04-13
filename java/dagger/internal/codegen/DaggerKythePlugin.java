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

import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.java.Plugin;
import com.google.devtools.kythe.proto.Storage.VName;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import dagger.BindsInstance;
import dagger.Component;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A plugin which emits nodes and edges for <a href="https://github.com/google/dagger">Dagger</a>
 * specific code.
 */
@AutoService(Plugin.class)
public class DaggerKythePlugin extends Plugin.Scanner<Void, Void> {
  private static final Logger logger = Logger.getLogger(DaggerKythePlugin.class.getCanonicalName());
  private FactEmitter emitter;
  @Inject KytheBindingGraphFactory bindingGraphFactory;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    Optional.ofNullable(tree.sym)
        .flatMap(bindingGraphFactory::create)
        .ifPresent(this::addNodesForGraph);

    return super.visitClassDef(tree, p);
  }

  private void addNodesForGraph(BindingGraph graph) {
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

    graph.subgraphs().forEach(this::addNodesForGraph);
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
    ResolvedBindings resolvedBindings = graph.resolvedBindings(dependency.kind(), targetKey);
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
    Optional<VName> requestElementNode = jvmNode(requestElement);
    Optional<VName> bindingElementNode = jvmNode(bindingElement);
    if (requestElementNode.isPresent() && bindingElementNode.isPresent()) {
      new EntrySet.Builder(
              requestElementNode.get(), "/inject/satisfiedby", bindingElementNode.get())
          .build()
          .emit(emitter);
      // TODO(ronshapiro): emit facts about the component that satisfies the edge
    } else {
      List<String> missingNodes = new ArrayList<>();
      if (!requestElementNode.isPresent()) {
        missingNodes.add("requestElement: " + requestElement);
      }
      if (!bindingElementNode.isPresent()) {
        missingNodes.add("bindingElement: " + bindingElement);
      }

      // TODO(ronshapiro): use Flogger
      logger.warning(String.format("Missing JVM nodes: %s ", missingNodes));
    }
  }

  private Optional<VName> jvmNode(Element element) {
    return kytheGraph.getJvmNode((Symbol) element).map(KytheNode::getVName);
  }

  @Override
  public void run(
      JCCompilationUnit compilationUnit, KytheEntrySets entrySets, KytheGraph kytheGraph) {
    if (bindingGraphFactory == null) {
      Context javaContext = kytheGraph.getJavaContext();
      emitter = entrySets.getEmitter();
      DaggerDaggerKythePlugin_PluginComponent.builder()
          .types(JavacTypes.instance(javaContext))
          .elements(JavacElements.instance(javaContext))
          .build()
          .inject(this);
    }
    super.run(compilationUnit, entrySets, kytheGraph);
  }

  @Singleton
  @Component
  interface PluginComponent {
    void inject(DaggerKythePlugin plugin);

    @Component.Builder
    interface Builder {
      @BindsInstance Builder types(Types types);
      @BindsInstance Builder elements(Elements elements);
      PluginComponent build();
    }
  }
}
