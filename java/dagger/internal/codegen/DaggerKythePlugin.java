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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.devtools.kythe.analyzers.base.EdgeKind.DEFINES_BINDING;
import static com.google.devtools.kythe.analyzers.base.EdgeKind.REF;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentAnnotation;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleAnnotation;
import static dagger.internal.codegen.DaggerTypes.hasTypeVariable;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.KytheFormatting.formatAnnotation;
import static dagger.internal.codegen.KytheFormatting.formatKey;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.kythe.analyzers.base.CorpusPath;
import com.google.devtools.kythe.analyzers.base.EdgeKind;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.java.Plugin;
import com.google.devtools.kythe.proto.Storage.VName;
import com.google.devtools.kythe.util.Span;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.util.Context;
import dagger.Binds;
import dagger.BindsInstance;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.multibindings.Multibinds;
import dagger.producers.Produces;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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
  @Inject BindingFactory bindingFactory;
  @Inject DelegateDeclaration.Factory delegateDeclarationFactory;
  @Inject MultibindingDeclaration.Factory multibindingDeclarationFactory;
  @Inject SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
  @Inject KeyFactory keyFactory;
  @Inject DaggerTypes types;
  @Inject DaggerElements elements;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    TypeElement type = MoreElements.asType(getElement(tree));
    bindingGraphFactory.create(type).ifPresent(this::addNodesForGraph);

    if (getModuleAnnotation(type).isPresent()) {
      subcomponentDeclarationFactory.forModule(type).forEach(this::addBindingDeclarationEdge);
    }

    Optional<AnnotationMirror> componentAnnotation = getComponentAnnotation(type);
    if (componentAnnotation.isPresent()) {
      addBindingAndDependencyEdges(bindingFactory.componentBinding(type));
      for (TypeMirror dependency : getComponentDependencies(componentAnnotation.get())) {
        ComponentRequirement requirement = ComponentRequirement.forDependency(dependency);
        addBindingAndDependencyEdges(bindingFactory.componentDependencyBinding(requirement));
      }
    }

    MembersInjectionBinding membersInjectionBinding =
        bindingFactory.membersInjectionBinding(
            MoreTypes.asDeclared(type.asType()), Optional.empty());
    for (InjectionSite injectionSite : membersInjectionBinding.injectionSites()) {
      // ignore inherited injection sites
      if (injectionSite.element().getEnclosingElement().equals(type)) {
        injectionSite.dependencies().forEach(this::addDependencyEdge);
      }
    }

    if (!membersInjectionBinding.injectionSites().isEmpty()) {
      Key membersInjectorKey =
          Key.builder(types.wrapType(membersInjectionBinding.key().type(), MembersInjector.class))
              .build();
      addBindingDeclarationEdge(membersInjectorKey, type);
    }

    return super.visitClassDef(tree, p);
  }

  @Override
  public Void visitMethodDef(JCMethodDecl tree, Void p) {
    ExecutableElement element = MoreElements.asExecutable(getElement(tree));
    if (isAnnotationPresent(element, Inject.class) && element.getKind().equals(CONSTRUCTOR)) {
      addBindingAndDependencyEdges(bindingFactory.injectionBinding(element, Optional.empty()));
    } else {
      TypeElement enclosingType = MoreElements.asType(element.getEnclosingElement());
      if (isAnnotationPresent(element, Provides.class)){
        addBindingAndDependencyEdges(bindingFactory.providesMethodBinding(element, enclosingType));
      } else if (isAnnotationPresent(element, Produces.class)) {
        addBindingAndDependencyEdges(bindingFactory.producesMethodBinding(element, enclosingType));
      } else if (isAnnotationPresent(element, Binds.class)) {
        DelegateDeclaration delegateDeclaration =
            delegateDeclarationFactory.create(element, enclosingType);
        addBindingDeclarationEdge(delegateDeclaration);
        addDependencyEdge(delegateDeclaration.delegateRequest());
      } else if (isAnnotationPresent(element, Multibinds.class)) {
        addBindingDeclarationEdge(
            multibindingDeclarationFactory.forMultibindsMethod(element, enclosingType));
      } else if (isAnnotationPresent(element, BindsOptionalOf.class)) {
        Key key = keyFactory.forBindsOptionalOfMethod(element, enclosingType);
        for (Class<?> optionalClass : optionalClasses()) {
          Key wrappedOptionalKey =
              key.toBuilder().type(types.wrapType(key.type(), optionalClass)).build();
          addBindingDeclarationEdge(wrappedOptionalKey, element);
        }
      } else if (isAnnotationPresent(element, BindsInstance.class)) {
        VariableElement parameter = getOnlyElement(element.getParameters());
        Key key = Key.builder(parameter.asType()).qualifier(getQualifier(parameter)).build();
        addBindingDeclarationEdge(key, element);
      }
    }
    return super.visitMethodDef(tree, p);
  }

  private void addNodesForGraph(BindingGraph graph) {
    for (ComponentDescriptor.ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      componentMethod.dependencyRequest().ifPresent(this::addDependencyEdge);
    }
  }

  private void addBindingAndDependencyEdges(Binding binding) {
    addBindingDeclarationEdge(binding);
    binding.dependencies().forEach(this::addDependencyEdge);
  }

  private void addBindingDeclarationEdge(BindingDeclaration declaration) {
    addBindingDeclarationEdge(declaration.key(), declaration.bindingElement().get());
  }

  /**
   * Adds a {@code defines/binding} edge between {@code bindingElement} and the node for {@code
   * key}.
   */
  private void addBindingDeclarationEdge(Key key, Element bindingElement) {
    if (hasTypeVariable(key.type())) {
      return;
    }
    EntrySet bindingAnchor =
        entrySets.newAnchorAndEmit(fileVName, bindingElementSpan(bindingElement), null);

    entrySets.emitEdge(bindingAnchor, DEFINES_BINDING, keyNode(key));
  }

  private Span bindingElementSpan(Element bindingElement) {
    Name name =
        bindingElement.getKind().equals(ElementKind.CONSTRUCTOR)
            ? bindingElement.getEnclosingElement().getSimpleName()
            : bindingElement.getSimpleName();
    return span(name, trees.getTree(bindingElement));
  }

  /**
   * Adds a {@code ref} edge between {@code dependencyRequest} and it's {@link
   * DependencyRequest#key() key's} node.
   */
  private void addDependencyEdge(DependencyRequest dependencyRequest) {
    if (!dependencyRequest.requestElement().isPresent()
        || hasTypeVariable(dependencyRequest.key().type())) {
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

  private ImmutableSet<Class<?>> optionalClasses() {
    // TODO(user): Can the plugin infrastructure be modified to guarantee certain types are
    // always available to plugins via kytheGraph.getNode() even if they haven't been scanned yet?
    return ImmutableSet.of(java.util.Optional.class, com.google.common.base.Optional.class)
        .stream()
        .filter(this::isClassAvailable)
        .collect(toImmutableSet());
  }

  private boolean isClassAvailable(Class<?> clazz) {
    return kytheGraph
        .getNode((Symbol) elements.getTypeElement(clazz.getCanonicalName()))
        .isPresent();
  }

  private Element getElement(Tree tree) {
    return trees.getElement(trees.getPath(compilationUnit, tree));
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
      DaggerDaggerKythePlugin_PluginComponent.builder()
          .types(JavacTypes.instance(javaContext))
          .elements(JavacElements.instance(javaContext))
          .build()
          .inject(this);
      keys = new KeyVNameFactory(kytheGraph, entrySets, emitter);
    }
    this.compilationUnit = compilationUnit;
    fileVName = kytheGraph.getNode(compilationUnit).get().getVName();
    corpusPath = new CorpusPath(fileVName.getCorpus(), "", "");
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
