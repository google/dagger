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
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.CANCELLATION_LISTENER_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.CONSTRUCTOR;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.INITIALIZE_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.COMPONENT_BUILDER;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.SUBCOMPONENT;
import static dagger.internal.codegen.ProducerNodeInstanceBindingExpression.MAY_INTERRUPT_IF_RUNNING;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.producers.internal.CancellationListener;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;

/** Builds the model for an implementation of a component or subcomponent. */
abstract class ComponentModelBuilder {
  static GeneratedComponentModel buildComponentModel(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      BindingGraphFactory bindingGraphFactory) {
    GeneratedComponentModel generatedComponentModel =
        GeneratedComponentModel.create(name, graph, keyFactory);
    OptionalFactories optionalFactories = new OptionalFactories(generatedComponentModel);
    Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel =
        GeneratedComponentBuilderModel.create(generatedComponentModel, graph, elements, types);
    ComponentRequirementFields componentRequirementFields =
        new ComponentRequirementFields(
            graph, generatedComponentModel, generatedComponentBuilderModel);
    ComponentBindingExpressions bindingExpressions =
        new ComponentBindingExpressions(
            graph,
            generatedComponentModel,
            componentRequirementFields,
            optionalFactories,
            types,
            elements,
            compilerOptions);
    if (generatedComponentModel.isAbstract()) {
      checkState(
          compilerOptions.aheadOfTimeSubcomponents(),
          "Calling 'buildComponentModel()' on %s when not generating ahead-of-time subcomponents.",
          graph.componentDescriptor().componentDefinitionType());
      return new AbstractSubcomponentModelBuilder(
              Optional.empty(), /* parent */
              types,
              elements,
              keyFactory,
              graph,
              generatedComponentModel,
              optionalFactories,
              bindingExpressions,
              componentRequirementFields,
              generatedComponentBuilderModel,
              bindingGraphFactory,
              compilerOptions)
          .build();
    } else {
      return new RootComponentModelBuilder(
              types,
              elements,
              keyFactory,
              graph,
              generatedComponentModel,
              optionalFactories,
              bindingExpressions,
              componentRequirementFields,
              generatedComponentBuilderModel,
              bindingGraphFactory,
              compilerOptions)
          .build();
    }
  }

  private final DaggerElements elements;
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final BindingGraph graph;
  private final ComponentBindingExpressions bindingExpressions;
  private final ComponentRequirementFields componentRequirementFields;
  private final GeneratedComponentModel generatedComponentModel;
  private final OptionalFactories optionalFactories;
  private final Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel;
  private final BindingGraphFactory bindingGraphFactory;
  private final CompilerOptions compilerOptions;
  private boolean done;

  private ComponentModelBuilder(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      OptionalFactories optionalFactories,
      ComponentBindingExpressions bindingExpressions,
      ComponentRequirementFields componentRequirementFields,
      Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel,
      BindingGraphFactory bindingGraphFactory,
      CompilerOptions compilerOptions) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.graph = graph;
    this.generatedComponentModel = generatedComponentModel;
    this.optionalFactories = optionalFactories;
    this.bindingExpressions = bindingExpressions;
    this.componentRequirementFields = componentRequirementFields;
    this.generatedComponentBuilderModel = generatedComponentBuilderModel;
    this.bindingGraphFactory = bindingGraphFactory;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Returns a {@link GeneratedComponentModel} for this component. This is only intended to be
   * called once (and will throw on successive invocations). If the component must be regenerated,
   * use a new instance.
   */
  protected final GeneratedComponentModel build() {
    checkState(
        !done,
        "ComponentModelBuilder has already built the GeneratedComponentModel for [%s].",
        generatedComponentModel.name());
    setSupertype();
    generatedComponentBuilderModel
        .map(GeneratedComponentBuilderModel::typeSpec)
        .ifPresent(this::addBuilderClass);

    getLocalAndInheritedMethods(
            graph.componentDescriptor().componentDefinitionType(), types, elements)
        .forEach(method -> generatedComponentModel.claimMethodName(method.getSimpleName()));

    addFactoryMethods();
    addInterfaceMethods();
    addSubcomponents();
    addConstructor();

    if (graph.componentDescriptor().kind().isProducer()) {
      addCancellationListenerImplementation();
    }

    done = true;
    return generatedComponentModel;
  }

  /** Set the supertype for this generated class. */
  private void setSupertype() {
    if (generatedComponentModel.supermodel().isPresent()) {
      generatedComponentModel.addSuperclass(generatedComponentModel.supermodel().get().name());
    } else {
      generatedComponentModel.addSupertype(graph.componentType());
    }
  }

  /**
   * Adds {@code builder} as a nested builder class. Root components and subcomponents will nest
   * this in different classes.
   */
  protected abstract void addBuilderClass(TypeSpec builder);

  /** Adds component factory methods. */
  protected abstract void addFactoryMethods();

  protected void addInterfaceMethods() {
    /* Each component method may have been declared by several supertypes. We want to implement only
     * one method for each distinct signature.*/
    ImmutableListMultimap<MethodSignature, ComponentMethodDescriptor> componentMethodsBySignature =
        Multimaps.index(graph.componentDescriptor().entryPointMethods(), this::getMethodSignature);
    for (List<ComponentMethodDescriptor> methodsWithSameSignature :
        Multimaps.asMap(componentMethodsBySignature).values()) {
      ComponentMethodDescriptor anyOneMethod = methodsWithSameSignature.stream().findAny().get();
      MethodSpec methodSpec = bindingExpressions.getComponentMethod(anyOneMethod);

      // If the binding for the component method is modifiable, register it as such.
      ModifiableBindingType modifiableBindingType =
          bindingExpressions
              .modifiableBindingExpressions()
              .registerComponentMethodIfModifiable(anyOneMethod, methodSpec);

      // If the method should be implemented in this component, implement it.
      if (modifiableBindingType.hasBaseClassImplementation()) {
        generatedComponentModel.addMethod(COMPONENT_METHOD, methodSpec);
      }
    }
  }

  private static final int STATEMENTS_PER_METHOD = 100;

  private static final String CANCELLATION_LISTENER_METHOD_NAME = "onProducerFutureCancelled";

  private void addCancellationListenerImplementation() {
    generatedComponentModel.addSupertype(elements.getTypeElement(CancellationListener.class));
    generatedComponentModel.claimMethodName(CANCELLATION_LISTENER_METHOD_NAME);

    // Reversing should order cancellations starting from entry points and going down to leaves
    // rather than the other way around. This shouldn't really matter but seems *slightly*
    // preferable because:
    // When a future that another future depends on is cancelled, that cancellation will propagate
    // up the future graph toward the entry point. Cancelling in reverse order should ensure that
    // everything that depends on a particular node has already been cancelled when that node is
    // cancelled, so there's no need to propagate. Otherwise, when we cancel a leaf node, it might
    // propagate through most of the graph, making most of the cancel calls that follow in the
    // onProducerFutureCancelled method do nothing.
    ImmutableList<CodeBlock> cancellationStatements =
        generatedComponentModel.getCancellations().reverse();
    MethodSpec.Builder methodBuilder =
        methodBuilder(CANCELLATION_LISTENER_METHOD_NAME)
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(boolean.class, MAY_INTERRUPT_IF_RUNNING);
    if (generatedComponentModel.supermodel().isPresent()) {
      methodBuilder.addStatement(
          "super.$L($L)", CANCELLATION_LISTENER_METHOD_NAME, MAY_INTERRUPT_IF_RUNNING);
    }

    if (cancellationStatements.size() < STATEMENTS_PER_METHOD) {
      methodBuilder.addCode(CodeBlocks.concat(cancellationStatements)).build();
    } else {
      List<List<CodeBlock>> partitions =
          Lists.partition(cancellationStatements, STATEMENTS_PER_METHOD);
      for (List<CodeBlock> partition : partitions) {
        String methodName = generatedComponentModel.getUniqueMethodName("cancelProducers");
        MethodSpec method =
            MethodSpec.methodBuilder(methodName)
                .addModifiers(PRIVATE)
                .addParameter(boolean.class, MAY_INTERRUPT_IF_RUNNING)
                .addCode(CodeBlocks.concat(partition))
                .build();
        methodBuilder.addStatement("$N($L)", method, MAY_INTERRUPT_IF_RUNNING);
        generatedComponentModel.addMethod(CANCELLATION_LISTENER_METHOD, method);
      }
    }

    generatedComponentModel.addMethod(CANCELLATION_LISTENER_METHOD, methodBuilder.build());
  }

  private MethodSignature getMethodSignature(ComponentMethodDescriptor method) {
    return MethodSignature.forComponentMethod(
        method, MoreTypes.asDeclared(graph.componentType().asType()), types);
  }

  private void addSubcomponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      // TODO(b/72748365): Can an abstract inner subcomponent implementation be elided if it's
      // totally empty?
      generatedComponentModel.addSubcomponent(
          subgraph.componentDescriptor(),
          generatedComponentModel.isAbstract()
              ? buildAbstractInnerSubcomponentModel(subgraph)
              : buildSubcomponentModel(subgraph));
    }
  }

  private GeneratedComponentModel buildAbstractInnerSubcomponentModel(BindingGraph childGraph) {
    GeneratedComponentModel supermodel =
        getSubcomponentSupermodel(childGraph.componentDescriptor());
    GeneratedComponentModel childModel =
        GeneratedComponentModel.forAbstractSubcomponent(
            childGraph.componentDescriptor(), supermodel, generatedComponentModel);
    Optional<GeneratedComponentBuilderModel> childBuilderModel =
        GeneratedComponentBuilderModel.create(childModel, childGraph, elements, types);
    ComponentRequirementFields childComponentRequirementFields =
        componentRequirementFields.forChildComponent(childGraph, childModel, childBuilderModel);
    ComponentBindingExpressions childBindingExpressions =
        bindingExpressions.forChildComponent(
            childGraph, childModel, childComponentRequirementFields);
    return new AbstractSubcomponentModelBuilder(
            Optional.of(this),
            types,
            elements,
            keyFactory,
            childGraph,
            childModel,
            optionalFactories,
            childBindingExpressions,
            childComponentRequirementFields,
            childBuilderModel,
            bindingGraphFactory,
            compilerOptions)
        .build();
  }

  private GeneratedComponentModel getSubcomponentSupermodel(ComponentDescriptor subcomponent) {
    // If the current model is for a subcomponent that has a defined supermodel, that supermodel
    // should contain a reference to a model for `subcomponent`
    if (generatedComponentModel.supermodel().isPresent()) {
      Optional<GeneratedComponentModel> supermodel =
          generatedComponentModel.supermodel().get().subcomponentModel(subcomponent);
      checkState(
          supermodel.isPresent(),
          "Attempting to generate an implementation of a subcomponent [%s] whose parent is a "
              + "subcomponent [%s], but whose supermodel is not present on the parent's "
              + "supermodel.",
          subcomponent.componentDefinitionType(),
          graph.componentType());
      return supermodel.get();
    }

    // Otherwise, the enclosing component is top-level, so we must generate the supermodel for the
    // subcomponent. We do so by building the model for the abstract base class for the
    // subcomponent. This is done by truncating the binding graph at the subcomponent.
    BindingGraph truncatedBindingGraph = bindingGraphFactory.create(subcomponent);
    return buildComponentModel(
        // TODO(ronshapiro): extract a factory class here so that we don't need to pass around
        // types, elements, keyFactory, etc...
        types,
        elements,
        keyFactory,
        compilerOptions,
        ComponentGenerator.componentName(truncatedBindingGraph.componentType()),
        truncatedBindingGraph,
        bindingGraphFactory);
  }

  private GeneratedComponentModel buildSubcomponentModel(BindingGraph childGraph) {
    GeneratedComponentModel childModel =
        GeneratedComponentModel.forSubcomponent(
            childGraph.componentDescriptor(), generatedComponentModel);
    Optional<GeneratedComponentBuilderModel> childBuilderModel =
        GeneratedComponentBuilderModel.create(childModel, childGraph, elements, types);
    ComponentRequirementFields childComponentRequirementFields =
        componentRequirementFields.forChildComponent(childGraph, childModel, childBuilderModel);
    ComponentBindingExpressions childBindingExpressions =
        bindingExpressions.forChildComponent(
            childGraph, childModel, childComponentRequirementFields);
    return new SubComponentModelBuilder(
            this,
            childGraph,
            childModel,
            childBindingExpressions,
            childComponentRequirementFields,
            childBuilderModel)
        .build();
  }

  private void addConstructor() {
    List<List<CodeBlock>> partitions =
        Lists.partition(generatedComponentModel.getInitializations(), STATEMENTS_PER_METHOD);

    ImmutableList<ParameterSpec> constructorParameters = constructorParameters();
    MethodSpec.Builder constructor =
        constructorBuilder()
            .addModifiers(generatedComponentModel.isAbstract() ? PROTECTED : PRIVATE)
            .addParameters(constructorParameters);

    if (generatedComponentModel.supermodel().isPresent()) {
      constructor.addStatement(
          CodeBlock.of(
              "super($L)",
              constructorParameters
                  .stream()
                  .map(param -> CodeBlock.of("$N", param))
                  .collect(toParametersCodeBlock())));
    }

    ImmutableList<ParameterSpec> initializeParameters = initializeParameters();
    CodeBlock initializeParametersCodeBlock =
        constructorParameters
            .stream()
            .map(param -> CodeBlock.of("$N", param))
            .collect(toParametersCodeBlock());

    for (List<CodeBlock> partition : partitions) {
      String methodName = generatedComponentModel.getUniqueMethodName("initialize");
      MethodSpec.Builder initializeMethod =
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              /* TODO(gak): Strictly speaking, we only need the suppression here if we are also
               * initializing a raw field in this method, but the structure of this code makes it
               * awkward to pass that bit through.  This will be cleaned up when we no longer
               * separate fields and initialization as we do now. */
              .addAnnotation(AnnotationSpecs.suppressWarnings(UNCHECKED))
              .addCode(CodeBlocks.concat(partition));
      initializeMethod.addParameters(initializeParameters);
      constructor.addStatement("$L($L)", methodName, initializeParametersCodeBlock);
      generatedComponentModel.addMethod(INITIALIZE_METHOD, initializeMethod.build());
    }
    generatedComponentModel.addMethod(CONSTRUCTOR, constructor.build());
  }

  /** Returns the list of {@link ParameterSpec}s for the initialize methods. */
  private ImmutableList<ParameterSpec> initializeParameters() {
    return constructorParameters()
        .stream()
        .map(param -> param.toBuilder().addModifiers(FINAL).build())
        .collect(toImmutableList());
  }

  /** Returns the list of {@link ParameterSpec}s for the constructor. */
  private ImmutableList<ParameterSpec> constructorParameters() {
    if (generatedComponentBuilderModel.isPresent()) {
      return ImmutableList.of(
          ParameterSpec.builder(generatedComponentBuilderModel.get().name(), "builder").build());
    } else if (graph.factoryMethod().isPresent()) {
      return getFactoryMethodParameterSpecs(graph);
    } else if (generatedComponentModel.isAbstract() && !generatedComponentModel.isNested()) {
      return ImmutableList.of();
    } else {
      throw new AssertionError(
          "Expected either a component builder or factory method but found neither.");
    }
  }

  /** Builds the model for the root component. */
  private static final class RootComponentModelBuilder extends ComponentModelBuilder {
    RootComponentModelBuilder(
        DaggerTypes types,
        DaggerElements elements,
        KeyFactory keyFactory,
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        OptionalFactories optionalFactories,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel,
        BindingGraphFactory bindingGraphFactory,
        CompilerOptions compilerOptions) {
      super(
          types,
          elements,
          keyFactory,
          graph,
          generatedComponentModel,
          optionalFactories,
          bindingExpressions,
          componentRequirementFields,
          generatedComponentBuilderModel,
          bindingGraphFactory,
          compilerOptions);
    }

    @Override
    protected void addBuilderClass(TypeSpec builder) {
      super.generatedComponentModel.addType(COMPONENT_BUILDER, builder);
    }

    @Override
    protected void addFactoryMethods() {
      // Only top-level components have the factory builder() method.
      // Mirror the user's builder API type if they had one.
      MethodSpec builderFactoryMethod =
          methodBuilder("builder")
              .addModifiers(PUBLIC, STATIC)
              .returns(
                  builderSpec().isPresent()
                      ? ClassName.get(builderSpec().get().builderDefinitionType())
                      : super.generatedComponentBuilderModel.get().name())
              .addStatement("return new $T()", super.generatedComponentBuilderModel.get().name())
              .build();
      super.generatedComponentModel.addMethod(BUILDER_METHOD, builderFactoryMethod);
      if (canInstantiateAllRequirements()) {
        CharSequence buildMethodName =
            builderSpec().isPresent() ? builderSpec().get().buildMethod().getSimpleName() : "build";
        super.generatedComponentModel.addMethod(
            BUILDER_METHOD,
            methodBuilder("create")
                .returns(ClassName.get(super.graph.componentType()))
                .addModifiers(PUBLIC, STATIC)
                .addStatement("return new Builder().$L()", buildMethodName)
                .build());
      }
    }

    private Optional<ComponentDescriptor.BuilderSpec> builderSpec() {
      return super.graph.componentDescriptor().builderSpec();
    }

    /** {@code true} if all of the graph's required dependencies can be automatically constructed */
    private boolean canInstantiateAllRequirements() {
      return !Iterables.any(
          super.graph.componentRequirements(),
          dependency -> dependency.requiresAPassedInstance(super.elements, super.types));
    }
  }

  /**
   * Builds the model for a nested subcomponent. This is used when ahead-of-time components are not
   * enabled (current default mode).
   */
  private static final class SubComponentModelBuilder extends ComponentModelBuilder {
    private final ComponentModelBuilder parent;

    SubComponentModelBuilder(
        ComponentModelBuilder parent,
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel) {
      super(
          parent.types,
          parent.elements,
          parent.keyFactory,
          graph,
          generatedComponentModel,
          parent.optionalFactories,
          bindingExpressions,
          componentRequirementFields,
          generatedComponentBuilderModel,
          parent.bindingGraphFactory,
          parent.compilerOptions);
      this.parent = parent;
    }

    @Override
    protected void addBuilderClass(TypeSpec builder) {
      parent.generatedComponentModel.addType(SUBCOMPONENT, builder);
    }

    @Override
    protected void addFactoryMethods() {
      // The parent's factory method to create this subcomponent if the
      // subcomponent was not added via {@link dagger.Module#subcomponents()}.
      super.graph.factoryMethod().ifPresent(this::createSubcomponentFactoryMethod);
    }

    private void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
      parent.generatedComponentModel.addMethod(
          COMPONENT_METHOD,
          MethodSpec.overriding(factoryMethod, parentType(), super.types)
              .addStatement(
                  "return new $T($L)",
                  super.generatedComponentModel.name(),
                  getFactoryMethodParameterSpecs(super.graph)
                      .stream()
                      .map(param -> CodeBlock.of("$N", param))
                      .collect(toParametersCodeBlock()))
              .build());
    }

    private DeclaredType parentType() {
      return asDeclared(parent.graph.componentType().asType());
    }
  }

  /** Builds the model for abstract implementations of a subcomponent. */
  private static final class AbstractSubcomponentModelBuilder extends ComponentModelBuilder {
    private final Optional<ComponentModelBuilder> parent;
    private final GeneratedComponentModel generatedComponentModel;
    private final ComponentBindingExpressions bindingExpressions;

    AbstractSubcomponentModelBuilder(
        Optional<ComponentModelBuilder> parent,
        DaggerTypes types,
        DaggerElements elements,
        KeyFactory keyFactory,
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        OptionalFactories optionalFactories,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel,
        BindingGraphFactory bindingGraphFactory,
        CompilerOptions compilerOptions) {
      super(
          types,
          elements,
          keyFactory,
          graph,
          generatedComponentModel,
          optionalFactories,
          bindingExpressions,
          componentRequirementFields,
          generatedComponentBuilderModel,
          bindingGraphFactory,
          compilerOptions);
      this.parent = parent;
      this.generatedComponentModel = generatedComponentModel;
      this.bindingExpressions = bindingExpressions;
    }

    @Override
    protected void addBuilderClass(TypeSpec builder) {
      if (parent.isPresent()) {
        // If an inner implementation of a subcomponent the builder is a peer class.
        parent.get().generatedComponentModel.addType(SUBCOMPONENT, builder);
      } else {
        generatedComponentModel.addType(SUBCOMPONENT, builder);
      }
    }

    @Override
    protected void addFactoryMethods() {
      // Only construct instances of subcomponents that have concrete implementations.
    }

    @Override
    protected void addInterfaceMethods() {
      if (generatedComponentModel.supermodel().isPresent()) {
        // Since we're overriding a subcomponent implementation we add to its implementation given
        // an expanded binding graph.

        // Override modifiable binding methods.
        for (ModifiableBindingMethod modifiableBindingMethod :
            generatedComponentModel.getModifiableBindingMethods()) {
          bindingExpressions
              .modifiableBindingExpressions()
              .getModifiableBindingMethod(modifiableBindingMethod)
              .ifPresent(
                  method -> generatedComponentModel.addImplementedModifiableBindingMethod(method));
        }
      } else {
        super.addInterfaceMethods();
      }
    }
  }

  /** Returns the list of {@link ParameterSpec}s for the corresponding graph's factory method. */
  private static ImmutableList<ParameterSpec> getFactoryMethodParameterSpecs(BindingGraph graph) {
    return graph
        .factoryMethodParameters()
        .values()
        .stream()
        .map(ParameterSpec::get)
        .collect(toImmutableList());
  }
}
