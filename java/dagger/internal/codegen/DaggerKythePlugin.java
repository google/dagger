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

import static com.google.devtools.kythe.analyzers.base.EdgeKind.DEFINES_BINDING;
import static com.google.devtools.kythe.analyzers.base.EdgeKind.REF;
import static dagger.internal.codegen.KytheFormatting.formatAnnotation;
import static dagger.internal.codegen.KytheFormatting.formatKey;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.devtools.kythe.analyzers.base.CorpusPath;
import com.google.devtools.kythe.analyzers.base.EdgeKind;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.java.Plugin;
import com.google.devtools.kythe.proto.Storage.VName;
import com.google.devtools.kythe.util.Span;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import dagger.model.Key;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

/**
 * A plugin which emits nodes and edges for <a href="https://github.com/google/dagger">Dagger</a>
 * specific code.
 */
@AutoService(Plugin.class)
public class DaggerKythePlugin extends Plugin.Scanner<Void, Void> {
  private JavacTrees trees;
  private FactEmitter emitter;
  private JCCompilationUnit compilationUnit;
  private VName fileVName;
  private KytheBindingGraphFactory bindingGraphFactory;
  private CorpusPath corpusPath;
  private KeyVNameFactory keys;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    TypeElement type = MoreElements.asType(trees.getElement(trees.getPath(compilationUnit, tree)));
    bindingGraphFactory.create(type).ifPresent(this::addNodesForGraph);
    return super.visitClassDef(tree, p);
  }

  private void addNodesForGraph(BindingGraph graph) {
    for (ResolvedBindings resolvedBindings : graph.resolvedBindings()) {
      for (Binding binding : resolvedBindings.bindings()) {
        addBindingDeclarationNode(binding);

        binding.dependencies().forEach(this::addDependencyNode);
      }

      resolvedBindings.multibindingDeclarations().forEach(this::addBindingDeclarationNode);
      resolvedBindings.subcomponentDeclarations().forEach(this::addBindingDeclarationNode);
      resolvedBindings
          .optionalBindingDeclarations()
          .forEach(declaration -> addBindingDeclarationNode(declaration, resolvedBindings.key()));
    }

    for (ComponentDescriptor.ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      componentMethod.dependencyRequest().ifPresent(this::addDependencyNode);
    }
  }

  private void addBindingDeclarationNode(BindingDeclaration declaration) {
    addBindingDeclarationNode(declaration, declaration.key());
  }

  /**
   * Adds a {@code defines/binding} edge between {@code declaration}'s {@link
   * BindingDeclaration#bindingElement()} and the node for {@code key}.
   *
   * <p>{@link BindingDeclaration#key()} is not used directly, since {@link
   * OptionalBindingDeclaration}s' keys are the unwrapped {@code Optional} types and can apply to
   * either {@code java.util.Optional} or {@code com.google.common.base.Optional}.
   */
  private void addBindingDeclarationNode(BindingDeclaration declaration, Key key) {
    if (!declaration.bindingElement().isPresent()) {
      return;
    }
    EntrySet bindingAnchor =
        entrySets.newAnchorAndEmit(fileVName, bindingElementSpan(declaration), null);

    entrySets.emitEdge(bindingAnchor, DEFINES_BINDING, keyNode(key));
  }

  private Span bindingElementSpan(BindingDeclaration declaration) {
    Element bindingElement = declaration.bindingElement().get();
    Name name =
        bindingElement.getKind().equals(ElementKind.METHOD)
            ? bindingElement.getSimpleName()
            : declaration.bindingTypeElement().get().getSimpleName();
    return span(name, trees.getTree(bindingElement));
  }

  /**
   * Adds a {@code ref} edge between {@code dependencyRequest} and it's {@link
   * DependencyRequest#key() key's} node.
   */
  private void addDependencyNode(DependencyRequest dependencyRequest) {
    if (!dependencyRequest.requestElement().isPresent()) {
      return;
    }
    EntrySet dependencyRequestAnchor =
        entrySets.newAnchorAndEmit(fileVName, dependencyRequestSpan(dependencyRequest), null);
    entrySets.emitEdge(dependencyRequestAnchor, REF, keyNode(dependencyRequest.key()));
  }

  private Span dependencyRequestSpan(DependencyRequest dependency) {
    Element requestElement = dependency.requestElement().get();
    return span(requestElement.getSimpleName(), trees.getTree(requestElement));
  }

  private Span span(Name name, JCTree tree) {
    return kytheGraph.findIdentifier(name, tree.getPreferredPosition()).get();
  }

  private EntrySet keyNode(Key key) {
    EntrySet keyNode = newNode("key", formatKey(key));

    entrySets.emitEdge(keyNode.getVName(), EdgeKind.PARAM, keys.vname(key), 0);
    key.qualifier()
        .ifPresent(
            qualifier -> {
              entrySets.emitEdge(
                  keyNode.getVName(), EdgeKind.PARAM, qualifierNode(qualifier).getVName(), 1);
            });

    return keyNode;
  }

  private EntrySet qualifierNode(AnnotationMirror qualifier) {
    return newNode("qualifier", formatAnnotation(qualifier));
  }

  private EntrySet newNode(String nodeKind, String format) {
    EntrySet node = entrySets
        .newNode("inject/" + nodeKind)
        .setCorpusPath(corpusPath)
        .setSignature(String.format("inject_%s:%s", nodeKind, format))
        .build();
    node.emit(emitter);
    return node;
  }

  @Override
  public void run(
      JCCompilationUnit compilationUnit, KytheEntrySets entrySets, KytheGraph kytheGraph) {
    if (bindingGraphFactory == null) {
      Context javaContext = kytheGraph.getJavaContext();
      trees = JavacTrees.instance(javaContext);
      emitter = entrySets.getEmitter();
      bindingGraphFactory =
          new KytheBindingGraphFactory(
              JavacTypes.instance(javaContext), JavacElements.instance(javaContext));
      keys = new KeyVNameFactory(kytheGraph, entrySets, emitter);
    }
    this.compilationUnit = compilationUnit;
    fileVName = kytheGraph.getNode(compilationUnit).get().getVName();
    corpusPath = new CorpusPath(fileVName.getCorpus(), "", "");
    super.run(compilationUnit, entrySets, kytheGraph);
  }
}
