/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.CONSTRUCTOR;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.INITIALIZE_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.SUBCOMPONENT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.List;
import javax.lang.model.util.Elements;

/** Creates the implementation class for a component or subcomponent. */
abstract class AbstractComponentWriter {
  // TODO(dpb): Make all these fields private after refactoring is complete.
  protected final Elements elements;
  protected final DaggerTypes types;
  private final CompilerOptions compilerOptions;
  protected final BindingGraph graph;
  protected final SubcomponentNames subcomponentNames;
  private final ComponentBindingExpressions bindingExpressions;
  protected final ComponentRequirementFields componentRequirementFields;
  protected final GeneratedComponentModel generatedComponentModel;
  private final ComponentRequirementField.Factory componentRequirementFieldFactory;
  protected final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  private final OptionalFactories optionalFactories;
  private ComponentBuilder builder;
  private boolean done;

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

  AbstractComponentWriter(
      DaggerTypes types,
      Elements elements,
      CompilerOptions compilerOptions,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      SubcomponentNames subcomponentNames,
      OptionalFactories optionalFactories,
      ComponentBindingExpressions bindingExpressions,
      ComponentRequirementFields componentRequirementFields) {
    this.types = types;
    this.elements = elements;
    this.compilerOptions = compilerOptions;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
    this.generatedComponentModel = generatedComponentModel;
    this.optionalFactories = optionalFactories;
    this.bindingExpressions = bindingExpressions;
    // TODO(dpb): Allow ComponentBuilder.create to return a no-op object
    if (hasBuilder(graph)) {
      builder =
          ComponentBuilder.create(
              generatedComponentModel.name(), graph, subcomponentNames, elements, types);
      builderFields = builder.builderFields();
    } else {
      builderFields = ImmutableMap.of();
    }
    this.componentRequirementFields = componentRequirementFields;
    this.componentRequirementFieldFactory =
        new ComponentRequirementField.Factory(generatedComponentModel, builderFields);
  }

  protected AbstractComponentWriter(
      AbstractComponentWriter parent,
      GeneratedComponentModel generatedComponentModel,
      BindingGraph graph,
      ComponentRequirementFields componentRequirementFields) {
    this(
        parent.types,
        parent.elements,
        parent.compilerOptions,
        graph,
        generatedComponentModel,
        parent.subcomponentNames,
        parent.optionalFactories,
        parent.bindingExpressions.forChildComponent(
            graph, generatedComponentModel, componentRequirementFields),
        componentRequirementFields);
  }

  /**
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  final TypeSpec.Builder write() {
    checkState(!done, "ComponentWriter has already been generated.");
    generatedComponentModel.addSupertype(graph.componentType());
    if (hasBuilder(graph)) {
      addBuilder();
    }

    getLocalAndInheritedMethods(
            graph.componentDescriptor().componentDefinitionType(), types, elements)
        .forEach(method -> generatedComponentModel.claimMethodName(method.getSimpleName()));

    addFactoryMethods();
    createComponentRequirementFields();
    addInterfaceMethods();
    addSubcomponents();
    addInitializeMethods();
    generatedComponentModel.addMethod(CONSTRUCTOR, constructor.build());
    if (graph.componentDescriptor().kind().isTopLevel()) {
      optionalFactories.addMembers(generatedComponentModel);
    }
    done = true;
    return generatedComponentModel.generate();
  }

  private static boolean hasBuilder(BindingGraph graph) {
    ComponentDescriptor component = graph.componentDescriptor();
    return component.kind().isTopLevel() || component.builderSpec().isPresent();
  }

  /**
   * Adds a builder type.
   */
  private void addBuilder() {
    addBuilderClass(builder.typeSpec());

    constructor.addParameter(builderName(), "builder");
  }

  /**
   * Adds {@code builder} as a nested builder class. Root components and subcomponents will nest
   * this in different classes.
   */
  protected abstract void addBuilderClass(TypeSpec builder);

  protected final ClassName builderName() {
    return builder.name();
  }

  /** Adds component factory methods. */
  protected abstract void addFactoryMethods();

  private void createComponentRequirementFields() {
    builderFields
        .keySet()
        .stream()
        .map(componentRequirementFieldFactory::forBuilderField)
        .forEach(componentRequirementFields::add);
  }

  private void addInterfaceMethods() {
    /* Each component method may have been declared by several supertypes. We want to implement only
     * one method for each distinct signature.*/
    ImmutableListMultimap<MethodSignature, ComponentMethodDescriptor> componentMethodsBySignature =
        Multimaps.index(graph.componentDescriptor().entryPointMethods(), this::getMethodSignature);
    for (List<ComponentMethodDescriptor> methodsWithSameSignature :
        Multimaps.asMap(componentMethodsBySignature).values()) {
      ComponentMethodDescriptor anyOneMethod = methodsWithSameSignature.stream().findAny().get();
      generatedComponentModel.addMethod(
          COMPONENT_METHOD, bindingExpressions.getComponentMethod(anyOneMethod));
    }
  }

  private MethodSignature getMethodSignature(ComponentMethodDescriptor method) {
    return MethodSignature.forComponentMethod(
        method, MoreTypes.asDeclared(graph.componentType().asType()), types);
  }

  private void addSubcomponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      generatedComponentModel.addType(
          SUBCOMPONENT, new SubcomponentWriter(this, subgraph).write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void addInitializeMethods() {
    List<List<CodeBlock>> partitions =
        Lists.partition(
            generatedComponentModel.getInitializations(), INITIALIZATIONS_PER_INITIALIZE_METHOD);

    UniqueNameSet methodNames = new UniqueNameSet();
    for (List<CodeBlock> partition : partitions) {
      String methodName = methodNames.getUniqueName("initialize");
      MethodSpec.Builder initializeMethod =
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              /* TODO(gak): Strictly speaking, we only need the suppression here if we are also
               * initializing a raw field in this method, but the structure of this code makes it
               * awkward to pass that bit through.  This will be cleaned up when we no longer
               * separate fields and initilization as we do now. */
              .addAnnotation(AnnotationSpecs.suppressWarnings(UNCHECKED))
              .addCode(CodeBlocks.concat(partition));
      if (hasBuilder(graph)) {
        initializeMethod.addParameter(builderName(), "builder", FINAL);
        constructor.addStatement("$L(builder)", methodName);
      } else {
        constructor.addStatement("$L()", methodName);
      }
      generatedComponentModel.addMethod(INITIALIZE_METHOD, initializeMethod.build());
    }
  }
}
