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
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.CANCELLATION_LISTENER_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.CONSTRUCTOR;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.INITIALIZE_METHOD;
import static dagger.internal.codegen.ComponentImplementation.TypeSpecKind.COMPONENT_BUILDER;
import static dagger.internal.codegen.ComponentImplementation.TypeSpecKind.SUBCOMPONENT;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.producers.CancellationPolicy.Propagation.PROPAGATE;
import static javax.lang.model.element.Modifier.ABSTRACT;
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
import dagger.model.Key;
import dagger.producers.internal.CancellationListener;
import dagger.producers.internal.Producers;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.type.DeclaredType;

/** Builder for {@link ComponentImplementation}s. */
// TODO(dpb): Refactor into a true factory with inner "builder" classes.
abstract class ComponentImplementationBuilder {
  private static final String MAY_INTERRUPT_IF_RUNNING = "mayInterruptIfRunning";

  static ComponentImplementation createComponentImplementation(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      BindingGraphFactory bindingGraphFactory) {
    ComponentImplementation componentImplementation =
        topLevelImplementation(name, graph, keyFactory);
    OptionalFactories optionalFactories = new OptionalFactories(componentImplementation);
    Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel =
        GeneratedComponentBuilderModel.create(componentImplementation, graph, elements, types);
    ComponentRequirementFields componentRequirementFields =
        new ComponentRequirementFields(
            graph, componentImplementation, generatedComponentBuilderModel);
    ComponentBindingExpressions bindingExpressions =
        new ComponentBindingExpressions(
            graph,
            componentImplementation,
            componentRequirementFields,
            optionalFactories,
            types,
            elements,
            compilerOptions);
    if (componentImplementation.isAbstract()) {
      checkState(
          compilerOptions.aheadOfTimeSubcomponents(),
          "Calling 'componentImplementation()' on %s when not generating ahead-of-time "
              + "subcomponents.",
          graph.componentDescriptor().componentDefinitionType());
      return new SubcomponentImplementationBuilder(
              Optional.empty(), /* parent */
              types,
              elements,
              keyFactory,
              graph,
              componentImplementation,
              optionalFactories,
              bindingExpressions,
              componentRequirementFields,
              generatedComponentBuilderModel,
              bindingGraphFactory,
              compilerOptions)
          .build();
    } else {
      return new RootComponentImplementationBuilder(
              types,
              elements,
              keyFactory,
              graph,
              componentImplementation,
              optionalFactories,
              bindingExpressions,
              componentRequirementFields,
              generatedComponentBuilderModel,
              bindingGraphFactory,
              compilerOptions)
          .build();
    }
  }

  /** Creates a root component or top-level abstract subcomponent implementation. */
  static ComponentImplementation topLevelImplementation(
      ClassName name, BindingGraph graph, KeyFactory keyFactory) {
    return new ComponentImplementation(
        graph.componentDescriptor(),
        name,
        NestingKind.TOP_LEVEL,
        Optional.empty(), // superclassImplementation
        new SubcomponentNames(graph, keyFactory),
        PUBLIC,
        graph.componentDescriptor().kind().isTopLevel() ? FINAL : ABSTRACT);
  }

  private final DaggerElements elements;
  private final DaggerTypes types;
  private final KeyFactory keyFactory;
  private final BindingGraph graph;
  private final ComponentBindingExpressions bindingExpressions;
  private final ComponentRequirementFields componentRequirementFields;
  private final ComponentImplementation componentImplementation;
  private final OptionalFactories optionalFactories;
  private final Optional<GeneratedComponentBuilderModel> generatedComponentBuilderModel;
  private final BindingGraphFactory bindingGraphFactory;
  private final CompilerOptions compilerOptions;
  private boolean done;

  private ComponentImplementationBuilder(
      DaggerTypes types,
      DaggerElements elements,
      KeyFactory keyFactory,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
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
    this.componentImplementation = componentImplementation;
    this.optionalFactories = optionalFactories;
    this.bindingExpressions = bindingExpressions;
    this.componentRequirementFields = componentRequirementFields;
    this.generatedComponentBuilderModel = generatedComponentBuilderModel;
    this.bindingGraphFactory = bindingGraphFactory;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Returns a {@link ComponentImplementation} for this component. This is only intended to be
   * called once (and will throw on successive invocations). If the component must be regenerated,
   * use a new instance.
   */
  protected final ComponentImplementation build() {
    checkState(
        !done,
        "ComponentImplementationBuilder has already built the ComponentImplementation for [%s].",
        componentImplementation.name());
    setSupertype();
    generatedComponentBuilderModel
        .map(GeneratedComponentBuilderModel::typeSpec)
        .ifPresent(this::addBuilderClass);

    getLocalAndInheritedMethods(
            graph.componentDescriptor().componentDefinitionType(), types, elements)
        .forEach(method -> componentImplementation.claimMethodName(method.getSimpleName()));

    addFactoryMethods();
    addInterfaceMethods();
    addChildComponents();
    addConstructor();

    if (graph.componentDescriptor().kind().isProducer()) {
      addCancellationListenerImplementation();
    }

    done = true;
    return componentImplementation;
  }

  /** Set the supertype for this generated class. */
  private void setSupertype() {
    if (componentImplementation.superclassImplementation().isPresent()) {
      componentImplementation.addSuperclass(
          componentImplementation.superclassImplementation().get().name());
    } else {
      componentImplementation.addSupertype(graph.componentType());
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
        componentImplementation.addMethod(COMPONENT_METHOD, methodSpec);
      }
    }
  }

  private static final int STATEMENTS_PER_METHOD = 100;

  private static final String CANCELLATION_LISTENER_METHOD_NAME = "onProducerFutureCancelled";

  private void addCancellationListenerImplementation() {
    componentImplementation.addSupertype(elements.getTypeElement(CancellationListener.class));
    componentImplementation.claimMethodName(CANCELLATION_LISTENER_METHOD_NAME);

    MethodSpec.Builder methodBuilder =
        methodBuilder(CANCELLATION_LISTENER_METHOD_NAME)
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(boolean.class, MAY_INTERRUPT_IF_RUNNING);
    if (componentImplementation.superclassImplementation().isPresent()) {
      methodBuilder.addStatement(
          "super.$L($L)", CANCELLATION_LISTENER_METHOD_NAME, MAY_INTERRUPT_IF_RUNNING);
    }

    ImmutableList<CodeBlock> cancellationStatements = cancellationStatements();
    if (cancellationStatements.isEmpty()
        && componentImplementation.superclassImplementation().isPresent()) {
      // Partial child implementations that have no new cancellations don't need to override
      // the method just to call super().
      return;
    }

    if (cancellationStatements.size() < STATEMENTS_PER_METHOD) {
      methodBuilder.addCode(CodeBlocks.concat(cancellationStatements)).build();
    } else {
      List<List<CodeBlock>> partitions =
          Lists.partition(cancellationStatements, STATEMENTS_PER_METHOD);
      for (List<CodeBlock> partition : partitions) {
        String methodName = componentImplementation.getUniqueMethodName("cancelProducers");
        MethodSpec method =
            MethodSpec.methodBuilder(methodName)
                .addModifiers(PRIVATE)
                .addParameter(boolean.class, MAY_INTERRUPT_IF_RUNNING)
                .addCode(CodeBlocks.concat(partition))
                .build();
        methodBuilder.addStatement("$N($L)", method, MAY_INTERRUPT_IF_RUNNING);
        componentImplementation.addMethod(CANCELLATION_LISTENER_METHOD, method);
      }
    }

    addCancelParentStatement(methodBuilder);

    componentImplementation.addMethod(CANCELLATION_LISTENER_METHOD, methodBuilder.build());
  }

  private ImmutableList<CodeBlock> cancellationStatements() {
    // Reversing should order cancellations starting from entry points and going down to leaves
    // rather than the other way around. This shouldn't really matter but seems *slightly*
    // preferable because:
    // When a future that another future depends on is cancelled, that cancellation will propagate
    // up the future graph toward the entry point. Cancelling in reverse order should ensure that
    // everything that depends on a particular node has already been cancelled when that node is
    // cancelled, so there's no need to propagate. Otherwise, when we cancel a leaf node, it might
    // propagate through most of the graph, making most of the cancel calls that follow in the
    // onProducerFutureCancelled method do nothing.
    ImmutableList<Key> cancellationKeys =
        componentImplementation.getCancellableProducerKeys().reverse();

    ImmutableList.Builder<CodeBlock> cancellationStatements = ImmutableList.builder();
    for (Key cancellationKey : cancellationKeys) {
      cancellationStatements.add(
          CodeBlock.of(
              "$T.cancel($L, $N);",
              Producers.class,
              bindingExpressions
                  .getDependencyExpression(
                      bindingRequest(cancellationKey, FrameworkType.PRODUCER_NODE),
                      componentImplementation.name())
                  .codeBlock(),
              MAY_INTERRUPT_IF_RUNNING));
    }
    return cancellationStatements.build();
  }

  protected void addCancelParentStatement(MethodSpec.Builder methodBuilder) {
    // Does nothing by default. Overridden in subclass(es) to add a statement if and only if the
    // component being generated is a concrete subcomponent implementation with a parent that allows
    // cancellation to propagate to it from subcomponents.
  }

  private MethodSignature getMethodSignature(ComponentMethodDescriptor method) {
    return MethodSignature.forComponentMethod(
        method, MoreTypes.asDeclared(graph.componentType().asType()), types);
  }

  private void addChildComponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      // TODO(b/117833324): Can an abstract inner subcomponent implementation be elided if it's
      // totally empty?
      componentImplementation.addChild(
          subgraph.componentDescriptor(), buildChildImplementation(subgraph));
    }
  }

  private ComponentImplementation getChildSuperclassImplementation(ComponentDescriptor child) {
    // If the current component has a superclass implementation, that superclass
    // should contain a reference to the child.
    if (componentImplementation.superclassImplementation().isPresent()) {
      ComponentImplementation superclassImplementation =
          componentImplementation.superclassImplementation().get();
      Optional<ComponentImplementation> childSuperclassImplementation =
          superclassImplementation.childImplementation(child);
      checkState(
          childSuperclassImplementation.isPresent(),
          "Cannot find abstract implementation of %s within %s while generating implemention "
              + "within %s",
          child.componentDefinitionType(),
          superclassImplementation.name(),
          componentImplementation.name());
      return childSuperclassImplementation.get();
    }

    // Otherwise, the enclosing component is top-level, so we must recreate the implementation
    // object for the base implementation of the child by truncating the binding graph at the child.
    BindingGraph truncatedBindingGraph = bindingGraphFactory.create(child);
    return createComponentImplementation(
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

  private ComponentImplementation buildChildImplementation(BindingGraph childGraph) {
    ComponentImplementation childImplementation =
        compilerOptions.aheadOfTimeSubcomponents()
            ? abstractInnerSubcomponent(childGraph.componentDescriptor())
            : concreteSubcomponent(childGraph.componentDescriptor());
    Optional<GeneratedComponentBuilderModel> childBuilderModel =
        GeneratedComponentBuilderModel.create(childImplementation, childGraph, elements, types);
    ComponentRequirementFields childComponentRequirementFields =
        componentRequirementFields.forChildComponent(
            childGraph, childImplementation, childBuilderModel);
    ComponentBindingExpressions childBindingExpressions =
        bindingExpressions.forChildComponent(
            childGraph, childImplementation, childComponentRequirementFields);
    return new SubcomponentImplementationBuilder(
            Optional.of(this),
            types,
            elements,
            keyFactory,
            childGraph,
            childImplementation,
            optionalFactories,
            childBindingExpressions,
            childComponentRequirementFields,
            childBuilderModel,
            bindingGraphFactory,
            compilerOptions)
        .build();
  }

  /** Creates an inner abstract subcomponent implementation. */
  private ComponentImplementation abstractInnerSubcomponent(ComponentDescriptor child) {
    return new ComponentImplementation(
        componentImplementation,
        child,
        Optional.of(getChildSuperclassImplementation(child)),
        PUBLIC,
        componentImplementation.isAbstract() ? ABSTRACT : FINAL);
  }

  /** Creates a concrete inner subcomponent implementation. */
  private ComponentImplementation concreteSubcomponent(ComponentDescriptor child) {
    return new ComponentImplementation(
        componentImplementation,
        child,
        Optional.empty(), // superclassImplementation
        PRIVATE,
        FINAL);
  }

  private void addConstructor() {
    List<List<CodeBlock>> partitions =
        Lists.partition(componentImplementation.getInitializations(), STATEMENTS_PER_METHOD);

    ImmutableList<ParameterSpec> constructorParameters = constructorParameters();
    MethodSpec.Builder constructor =
        constructorBuilder()
            .addModifiers(componentImplementation.isAbstract() ? PROTECTED : PRIVATE)
            .addParameters(constructorParameters);
    componentImplementation.setConstructorParameters(constructorParameters);
    componentImplementation
        .superclassImplementation()
        .ifPresent(
            superclassImplementation ->
                constructor.addStatement(
                    CodeBlock.of(
                        "super($L)",
                        superclassImplementation.constructorParameters().stream()
                            .map(param -> CodeBlock.of("$N", param))
                            .collect(toParametersCodeBlock()))));

    ImmutableList<ParameterSpec> initializeParameters = initializeParameters();
    CodeBlock initializeParametersCodeBlock =
        constructorParameters
            .stream()
            .map(param -> CodeBlock.of("$N", param))
            .collect(toParametersCodeBlock());

    for (List<CodeBlock> partition : partitions) {
      String methodName = componentImplementation.getUniqueMethodName("initialize");
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
      componentImplementation.addMethod(INITIALIZE_METHOD, initializeMethod.build());
    }
    componentImplementation.addMethod(CONSTRUCTOR, constructor.build());
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
    } else if (componentImplementation.isAbstract() && componentImplementation.isNested()) {
      // If we're generating an abstract inner subcomponent, then we are not implementing module
      // instance bindings and have no need for factory method parameters.
      return ImmutableList.of();
    } else if (graph.factoryMethod().isPresent()) {
      return getFactoryMethodParameterSpecs(graph);
    } else if (componentImplementation.isAbstract()) {
      // If we're generating an abstract base implementation of a subcomponent it's acceptable to
      // have neither a builder nor factory method.
      return ImmutableList.of();
    } else {
      throw new AssertionError(
          "Expected either a component builder or factory method but found neither.");
    }
  }

  /** Builds a root component implementation. */
  private static final class RootComponentImplementationBuilder
      extends ComponentImplementationBuilder {
    RootComponentImplementationBuilder(
        DaggerTypes types,
        DaggerElements elements,
        KeyFactory keyFactory,
        BindingGraph graph,
        ComponentImplementation componentImplementation,
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
          componentImplementation,
          optionalFactories,
          bindingExpressions,
          componentRequirementFields,
          generatedComponentBuilderModel,
          bindingGraphFactory,
          compilerOptions);
    }

    @Override
    protected void addBuilderClass(TypeSpec builder) {
      super.componentImplementation.addType(COMPONENT_BUILDER, builder);
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
      super.componentImplementation.addMethod(BUILDER_METHOD, builderFactoryMethod);
      if (canInstantiateAllRequirements()) {
        CharSequence buildMethodName =
            builderSpec().isPresent() ? builderSpec().get().buildMethod().getSimpleName() : "build";
        super.componentImplementation.addMethod(
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
   * Builds a subcomponent implementation. If generating ahead-of-time subcomponents, this may be an
   * abstract base class implementation, an abstract inner implementation, or a concrete
   * implementation that extends an abstract base implementation. Otherwise it represents a private,
   * inner, concrete, final implementation of a subcomponent which extends a user defined type.
   */
  private static final class SubcomponentImplementationBuilder
      extends ComponentImplementationBuilder {
    private final Optional<ComponentImplementationBuilder> parent;
    private final ComponentImplementation componentImplementation;
    private final ComponentBindingExpressions bindingExpressions;

    SubcomponentImplementationBuilder(
        Optional<ComponentImplementationBuilder> parent,
        DaggerTypes types,
        DaggerElements elements,
        KeyFactory keyFactory,
        BindingGraph graph,
        ComponentImplementation componentImplementation,
        OptionalFactories optionalFactories,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        Optional<GeneratedComponentBuilderModel> builder,
        BindingGraphFactory bindingGraphFactory,
        CompilerOptions compilerOptions) {
      super(
          types,
          elements,
          keyFactory,
          graph,
          componentImplementation,
          optionalFactories,
          bindingExpressions,
          componentRequirementFields,
          builder,
          bindingGraphFactory,
          compilerOptions);
      this.parent = parent;
      this.componentImplementation = componentImplementation;
      this.bindingExpressions = bindingExpressions;
    }

    @Override
    protected void addBuilderClass(TypeSpec builder) {
      if (parent.isPresent()) {
        // In an inner implementation of a subcomponent the builder is a peer class.
        parent.get().componentImplementation.addType(SUBCOMPONENT, builder);
      } else {
        componentImplementation.addType(SUBCOMPONENT, builder);
      }
    }

    @Override
    protected void addFactoryMethods() {
      // Only construct instances of subcomponents that have concrete implementations.
      if (!componentImplementation.isAbstract()) {
        // Use the parent's factory method to create this subcomponent if the
        // subcomponent was not added via {@link dagger.Module#subcomponents()}.
        super.graph.factoryMethod().ifPresent(this::createSubcomponentFactoryMethod);
      }
    }

    private void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
      checkState(parent.isPresent());
      parent
          .get()
          .componentImplementation
          .addMethod(
              COMPONENT_METHOD,
              MethodSpec.overriding(factoryMethod, parentType(), super.types)
                  .addStatement(
                      "return new $T($L)",
                      componentImplementation.name(),
                      getFactoryMethodParameterSpecs(super.graph).stream()
                          .map(param -> CodeBlock.of("$N", param))
                          .collect(toParametersCodeBlock()))
                  .build());
    }

    private DeclaredType parentType() {
      return asDeclared(parent.get().graph.componentType().asType());
    }

    @Override
    protected void addInterfaceMethods() {
      if (componentImplementation.superclassImplementation().isPresent()) {
        // Since we're overriding a subcomponent implementation we add to its implementation given
        // an expanded binding graph.

        // Override modifiable binding methods.
        for (ModifiableBindingMethod modifiableBindingMethod :
            componentImplementation.getModifiableBindingMethods()) {
          bindingExpressions
              .modifiableBindingExpressions()
              .getModifiableBindingMethod(modifiableBindingMethod)
              .ifPresent(
                  method -> componentImplementation.addImplementedModifiableBindingMethod(method));
        }
      } else {
        super.addInterfaceMethods();
      }
    }

    @Override
    protected void addCancelParentStatement(MethodSpec.Builder methodBuilder) {
      if (shouldPropagateCancellationToParent()) {
        methodBuilder.addStatement(
            "$T.this.$L($L)",
            parent.get().componentImplementation.name(),
            CANCELLATION_LISTENER_METHOD_NAME,
            MAY_INTERRUPT_IF_RUNNING);
      }
    }

    private boolean shouldPropagateCancellationToParent() {
      return parent.isPresent()
          && parent
              .get()
              .componentImplementation
              .componentDescriptor()
              .cancellationPolicy()
              .map(policy -> policy.fromSubcomponents().equals(PROPAGATE))
              .orElse(false);
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
