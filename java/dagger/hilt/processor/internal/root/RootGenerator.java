/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.hilt.processor.internal.Processors.toClassNames;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Generates components and any other classes needed for a root. */
final class RootGenerator {

  static void generate(
      ComponentTreeDepsMetadata componentTreeDepsMetadata,
      RootMetadata metadata,
      ComponentNames componentNames,
      ProcessingEnvironment env)
      throws IOException {
    new RootGenerator(
            componentTreeDepsMetadata,
            RootMetadata.copyWithNewTree(metadata, filterDescriptors(metadata.componentTree())),
            componentNames,
            env)
        .generateComponents();
  }

  private final TypeElement originatingElement;
  private final RootMetadata metadata;
  private final ProcessingEnvironment env;
  private final Root root;
  private final Map<String, Integer> simpleComponentNamesToDedupeSuffix = new HashMap<>();
  private final Map<ComponentDescriptor, ClassName> componentNameMap = new HashMap<>();
  private final ComponentNames componentNames;

  private RootGenerator(
      ComponentTreeDepsMetadata componentTreeDepsMetadata,
      RootMetadata metadata,
      ComponentNames componentNames,
      ProcessingEnvironment env) {
    this.originatingElement =
        checkNotNull(
            env.getElementUtils().getTypeElement(componentTreeDepsMetadata.name().toString()));
    this.metadata = metadata;
    this.componentNames = componentNames;
    this.env = env;
    this.root = metadata.root();
  }

  private void generateComponents() throws IOException {

    // TODO(bcorso): Consider moving all of this logic into ComponentGenerator?
    ClassName componentsWrapperClassName = getComponentsWrapperClassName();
    TypeSpec.Builder componentsWrapper =
        TypeSpec.classBuilder(componentsWrapperClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    Processors.addGeneratedAnnotation(componentsWrapper, env, ClassNames.ROOT_PROCESSOR.toString());

    ImmutableMap<ComponentDescriptor, ClassName> subcomponentBuilderModules =
        subcomponentBuilderModules(componentsWrapper);

    ComponentTree componentTree = metadata.componentTree();
    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      ImmutableSet<ClassName> modules =
          ImmutableSet.<ClassName>builder()
              .addAll(toClassNames(metadata.modules(componentDescriptor.component())))
              .addAll(
                  componentTree.childrenOf(componentDescriptor).stream()
                      .map(subcomponentBuilderModules::get)
                      .collect(toImmutableSet()))
              .build();

      componentsWrapper.addType(
          new ComponentGenerator(
                  env,
                  getComponentClassName(componentDescriptor),
                  Optional.empty(),
                  modules,
                  metadata.entryPoints(componentDescriptor.component()),
                  metadata.scopes(componentDescriptor.component()),
                  ImmutableList.of(),
                  componentAnnotation(componentDescriptor),
                  componentBuilder(componentDescriptor))
              .typeSpecBuilder()
              .addModifiers(Modifier.STATIC)
              .build());
    }

    RootFileFormatter.write(
        JavaFile.builder(componentsWrapperClassName.packageName(), componentsWrapper.build())
            .build(),
        env.getFiler());
  }

  private static ComponentTree filterDescriptors(ComponentTree componentTree) {
    MutableGraph<ComponentDescriptor> graph =
        GraphBuilder.from(componentTree.graph()).build();

    componentTree.graph().nodes().forEach(graph::addNode);
    componentTree.graph().edges().forEach(graph::putEdge);

    // Remove components that do not have builders (besides the root component) since if
    // we didn't find any builder class, then we don't need to generate the component
    // since it would be inaccessible.
    componentTree.getComponentDescriptors().stream()
        .filter(descriptor -> !descriptor.isRoot() && !descriptor.creator().isPresent())
        .forEach(graph::removeNode);

    // The graph may still have nodes that are children of components that don't have builders,
    // so we need to find reachable nodes from the root and create a new graph to remove those.
    // We reuse the root from the original tree since it should not have been removed.
    return ComponentTree.from(Graphs.reachableNodes(graph, componentTree.root()));
  }

  private ImmutableMap<ComponentDescriptor, ClassName> subcomponentBuilderModules(
      TypeSpec.Builder componentsWrapper) {
    ImmutableMap.Builder<ComponentDescriptor, ClassName> modules = ImmutableMap.builder();
    for (ComponentDescriptor descriptor : metadata.componentTree().getComponentDescriptors()) {
      // Root component builders don't have subcomponent builder modules
      if (!descriptor.isRoot() && descriptor.creator().isPresent()) {
        ClassName component = getComponentClassName(descriptor);
        ClassName builder = descriptor.creator().get();
        ClassName module = component.peerClass(component.simpleName() + "BuilderModule");
        componentsWrapper.addType(subcomponentBuilderModule(component, builder, module));
        modules.put(descriptor, module);
      }
    }
    return modules.build();
  }

  // Generates:
  // @Module(subcomponents = FooSubcomponent.class)
  // interface FooSubcomponentBuilderModule {
  //   @Binds FooSubcomponentInterfaceBuilder bind(FooSubcomponent.Builder builder);
  // }
  private TypeSpec subcomponentBuilderModule(
      ClassName componentName, ClassName builderName, ClassName moduleName) {
    TypeSpec.Builder subcomponentBuilderModule =
        TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(originatingElement)
            .addModifiers(ABSTRACT)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.MODULE)
                    .addMember("subcomponents", "$T.class", componentName)
                    .build())
            .addAnnotation(ClassNames.DISABLE_INSTALL_IN_CHECK)
            .addMethod(
                MethodSpec.methodBuilder("bind")
                    .addModifiers(ABSTRACT, PUBLIC)
                    .addAnnotation(ClassNames.BINDS)
                    .returns(builderName)
                    .addParameter(componentName.nestedClass("Builder"), "builder")
                    .build());

    Processors.addGeneratedAnnotation(
        subcomponentBuilderModule, env, ClassNames.ROOT_PROCESSOR.toString());

    return subcomponentBuilderModule.build();
  }

  private Optional<TypeSpec> componentBuilder(ComponentDescriptor descriptor) {
    return descriptor
        .creator()
        .map(
            creator ->
                TypeSpec.interfaceBuilder("Builder")
                    .addOriginatingElement(originatingElement)
                    .addModifiers(STATIC, ABSTRACT)
                    .addSuperinterface(creator)
                    .addAnnotation(componentBuilderAnnotation(descriptor))
                    .build());
  }

  private ClassName componentAnnotation(ComponentDescriptor componentDescriptor) {
    if (!componentDescriptor.isRoot()
        ) {
      return ClassNames.SUBCOMPONENT;
    } else {
      return ClassNames.COMPONENT;
    }
  }

  private ClassName componentBuilderAnnotation(ComponentDescriptor componentDescriptor) {
    if (componentDescriptor.isRoot()) {
      return ClassNames.COMPONENT_BUILDER;
    } else {
      return ClassNames.SUBCOMPONENT_BUILDER;
    }
  }

  private ClassName getPartialRootModuleClassName() {
    return getComponentsWrapperClassName().nestedClass("PartialRootModule");
  }

  private ClassName getComponentsWrapperClassName() {
    return componentNames.generatedComponentsWrapper(root.originatingRootClassname());
  }

  private ClassName getComponentClassName(ComponentDescriptor componentDescriptor) {
    if (componentNameMap.containsKey(componentDescriptor)) {
      return componentNameMap.get(componentDescriptor);
    }

    // Disallow any component names with the same name as our SingletonComponent because we treat
    // that component specially and things may break.
    checkState(
        componentDescriptor.component().equals(ClassNames.SINGLETON_COMPONENT)
        || !componentDescriptor.component().simpleName().equals(
            ClassNames.SINGLETON_COMPONENT.simpleName()),
        "Cannot have a component with the same simple name as the reserved %s: %s",
        ClassNames.SINGLETON_COMPONENT.simpleName(),
        componentDescriptor.component());

    ClassName generatedComponent =
        componentNames.generatedComponent(
            root.originatingRootClassname(), componentDescriptor.component());

    Integer suffix = simpleComponentNamesToDedupeSuffix.get(generatedComponent.simpleName());
    if (suffix != null) {
      // If an entry exists, use the suffix in the map and the replace it with the value incremented
      generatedComponent = Processors.append(generatedComponent, String.valueOf(suffix));
      simpleComponentNamesToDedupeSuffix.put(generatedComponent.simpleName(), suffix + 1);
    } else {
      // Otherwise, just add an entry for any possible future duplicates
      simpleComponentNamesToDedupeSuffix.put(generatedComponent.simpleName(), 2);
    }

    componentNameMap.put(componentDescriptor, generatedComponent);
    return generatedComponent;
  }
}
