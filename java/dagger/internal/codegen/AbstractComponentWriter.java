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
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;

/** Creates the implementation class for a component or subcomponent. */
abstract class AbstractComponentWriter {
  // TODO(dpb): Make all these fields private after refactoring is complete.
  protected final Elements elements;
  protected final DaggerTypes types;
  protected final CompilerOptions compilerOptions;
  protected final BindingGraph graph;
  protected final SubcomponentNames subcomponentNames;
  private final ComponentBindingExpressions bindingExpressions;
  protected final ComponentRequirementFields componentRequirementFields;
  protected final GeneratedComponentModel generatedComponentModel;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;
  private final MembersInjectionMethods membersInjectionMethods;
  protected final List<MethodSpec> interfaceMethods = new ArrayList<>();
  private final BindingExpression.Factory bindingExpressionFactory;
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
      ComponentRequirementFields componentRequirementFields,
      ReferenceReleasingManagerFields referenceReleasingManagerFields) {
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
    this.referenceReleasingManagerFields = referenceReleasingManagerFields;
    this.membersInjectionMethods =
        new MembersInjectionMethods(
            generatedComponentModel, bindingExpressions, graph, elements, types);
    // TODO(user): move factories into ComponentBindingExpressions.
    this.bindingExpressionFactory =
        new BindingExpression.Factory(
            compilerOptions,
            bindingExpressions,
            componentRequirementFields,
            membersInjectionMethods,
            referenceReleasingManagerFields,
            generatedComponentModel,
            subcomponentNames,
            graph,
            types,
            elements,
            optionalFactories);
    this.componentRequirementFieldFactory =
        new ComponentRequirementField.Factory(generatedComponentModel, builderFields);
  }

  protected AbstractComponentWriter(
      AbstractComponentWriter parent, ClassName name, BindingGraph graph) {
    this(
        parent.types,
        parent.elements,
        parent.compilerOptions,
        graph,
        GeneratedComponentModel.forSubcomponent(name),
        parent.subcomponentNames,
        parent.optionalFactories,
        parent.bindingExpressions.forChildComponent(),
        parent.componentRequirementFields.forChildComponent(),
        parent.referenceReleasingManagerFields);
  }

  /**
   * Creates a {@link FieldSpec.Builder} with a unique name based off of {@code name}.
   */
  protected final FieldSpec.Builder componentField(TypeName type, String name) {
    return FieldSpec.builder(type, generatedComponentModel.getUniqueFieldName(name));
  }

  /**
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  final TypeSpec.Builder write() {
    checkState(!done, "ComponentWriter has already been generated.");
    addSupertype(generatedComponentModel.component, graph.componentType());
    if (hasBuilder(graph)) {
      addBuilder();
    }

    getLocalAndInheritedMethods(
            graph.componentDescriptor().componentDefinitionType(), types, elements)
        .forEach(method -> generatedComponentModel.claimMethodName(method.getSimpleName()));

    addFactoryMethods();
    createBindingExpressions();
    createComponentRequirementFields();
    implementInterfaceMethods();
    addSubcomponents();
    writeInitializeAndInterfaceMethods();
    generatedComponentModel.addMethods(membersInjectionMethods.getAllMethods());
    generatedComponentModel.addMethod(constructor.build());
    if (graph.componentDescriptor().kind().isTopLevel()) {
      // TODO(user): pass in generatedComponentModel instead of the component.
      optionalFactories.addMembers(generatedComponentModel.component);
    }
    done = true;
    return generatedComponentModel.component;
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

  /**
   * Adds component factory methods.
   */
  protected abstract void addFactoryMethods();

  private void createBindingExpressions() {
    graph.resolvedBindings().values().forEach(this::createBindingExpression);
  }

  private void createBindingExpression(ResolvedBindings resolvedBindings) {
    // If the binding can be satisfied with a static method call without dependencies or state,
    // no field is necessary.
    // TODO(ronshapiro): can these be merged into bindingExpressionFactory.forResolvedBindings()?
    Optional<BindingExpression> staticBindingExpression =
        bindingExpressionFactory.forStaticMethod(resolvedBindings);
    if (staticBindingExpression.isPresent()) {
      bindingExpressions.addBindingExpression(staticBindingExpression.get());
      return;
    }

    // No field needed if there are no owned bindings.
    if (resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    // TODO(gak): get rid of the field for unscoped delegated bindings
    bindingExpressions.addBindingExpression(bindingExpressionFactory.forField(resolvedBindings));
  }

  private void createComponentRequirementFields() {
    builderFields
        .keySet()
        .stream()
        .map(componentRequirementFieldFactory::forBuilderField)
        .forEach(componentRequirementFields::add);
  }

  private void implementInterfaceMethods() {
    Set<MethodSignature> interfaceMethodSignatures = Sets.newHashSet();
    DeclaredType componentType = MoreTypes.asDeclared(graph.componentType().asType());
    for (ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        ExecutableElement methodElement = componentMethod.methodElement();
        ExecutableType requestType =
            MoreTypes.asExecutable(types.asMemberOf(componentType, methodElement));
        MethodSignature signature =
            MethodSignature.fromExecutableType(
                methodElement.getSimpleName().toString(), requestType);
        if (interfaceMethodSignatures.add(signature)) {
          MethodSpec.Builder interfaceMethod =
              MethodSpec.overriding(methodElement, componentType, types);
          interfaceMethod.addCode(
              bindingExpressions.getComponentMethodImplementation(
                  componentMethod, generatedComponentModel.name()));
          interfaceMethods.add(interfaceMethod.build());
        }
      }
    }
  }

  private void addSubcomponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      ComponentMethodDescriptor componentMethodDescriptor =
          graph.componentDescriptor()
              .subcomponentsByFactoryMethod()
              .inverse()
              .get(subgraph.componentDescriptor());
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, Optional.ofNullable(componentMethodDescriptor), subgraph);
      generatedComponentModel.addType(subcomponent.write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void writeInitializeAndInterfaceMethods() {
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
      generatedComponentModel.addMethod(initializeMethod.build());
    }

    generatedComponentModel.addMethods(interfaceMethods);
  }
}
