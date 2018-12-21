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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.CodeBlocks.parameterNames;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.ComponentGenerator.componentName;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.CANCELLATION_LISTENER_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.CONSTRUCTOR;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.INITIALIZE_METHOD;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.MODIFIABLE_BINDING_METHOD;
import static dagger.internal.codegen.ComponentImplementation.TypeSpecKind.COMPONENT_CREATOR;
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
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.Key;
import dagger.producers.internal.CancellationListener;
import dagger.producers.internal.Producers;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.type.DeclaredType;

/** Factory for {@link ComponentImplementation}s. */
final class ComponentImplementationFactory {
  private static final String MAY_INTERRUPT_IF_RUNNING = "mayInterruptIfRunning";

  /**
   * How many statements per {@code initialize()} or {@code onProducerFutureCancelled()} method
   * before they get partitioned.
   */
  private static final int STATEMENTS_PER_METHOD = 100;

  private static final String CANCELLATION_LISTENER_METHOD_NAME = "onProducerFutureCancelled";

  private final KeyFactory keyFactory;
  private final CompilerOptions compilerOptions;
  private final TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder;

  @Inject
  ComponentImplementationFactory(
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder) {
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.topLevelImplementationComponentBuilder = topLevelImplementationComponentBuilder;
  }

  /**
   * Returns a top-level (non-nested) component implementation for a binding graph.
   *
   * @throws IllegalStateException if the binding graph is for a subcomponent and
   *     ahead-of-time-subcomponents mode is not enabled
   */
  ComponentImplementation createComponentImplementation(BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        topLevelImplementation(componentName(bindingGraph.componentTypeElement()), bindingGraph);
    // TODO(dpb): explore using optional bindings for the "parent" bindings
    CurrentImplementationSubcomponent currentImplementationSubcomponent =
        topLevelImplementationComponentBuilder
            .topLevelComponent(componentImplementation)
            .build()
            .currentImplementationSubcomponentBuilder()
            .componentImplementation(componentImplementation)
            .bindingGraph(bindingGraph)
            .parentBuilder(Optional.empty())
            .parentBindingExpressions(Optional.empty())
            .parentRequirementExpressions(Optional.empty())
            .build();

    if (componentImplementation.isAbstract()) {
      checkState(
          compilerOptions.aheadOfTimeSubcomponents(),
          "Calling 'componentImplementation()' on %s when not generating ahead-of-time "
              + "subcomponents.",
          bindingGraph.componentTypeElement());
      return currentImplementationSubcomponent.subcomponentBuilder().build();
    } else {
      return currentImplementationSubcomponent.rootComponentBuilder().build();
    }
  }

  /** Creates a root component or top-level abstract subcomponent implementation. */
  ComponentImplementation topLevelImplementation(ClassName name, BindingGraph graph) {
    return new ComponentImplementation(
        graph.componentDescriptor(),
        name,
        NestingKind.TOP_LEVEL,
        Optional.empty(), // superclassImplementation
        new SubcomponentNames(graph, keyFactory),
        PUBLIC,
        graph.componentDescriptor().kind().isTopLevel() ? FINAL : ABSTRACT);
  }

  abstract static class ComponentImplementationBuilder {
    // TODO(ronshapiro): replace this with composition instead of inheritance so we don't have 
    // non-final fields
    @Inject BindingGraph graph;
    @Inject ComponentBindingExpressions bindingExpressions;
    @Inject ComponentRequirementExpressions componentRequirementExpressions;
    @Inject ComponentImplementation componentImplementation;
    @Inject BindingGraphFactory bindingGraphFactory;
    @Inject DaggerTypes types;
    @Inject DaggerElements elements;
    @Inject CompilerOptions compilerOptions;
    @Inject ComponentImplementationFactory componentImplementationFactory;
    @Inject TopLevelImplementationComponent topLevelImplementationComponent;
    boolean done;

    /**
     * Returns a {@link ComponentImplementation} for this component. This is only intended to be
     * called once (and will throw on successive invocations). If the component must be regenerated,
     * use a new instance.
     */
    final ComponentImplementation build() {
      checkState(
          !done,
          "ComponentImplementationBuilder has already built the ComponentImplementation for [%s].",
          componentImplementation.name());
      setSupertype();
      componentImplementation
          .creatorImplementation()
          .map(ComponentCreatorImplementation::componentCreatorClass)
          .ifPresent(this::addCreatorClass);

      getLocalAndInheritedMethods(graph.componentTypeElement(), types, elements)
          .forEach(method -> componentImplementation.claimMethodName(method.getSimpleName()));
      componentImplementation
          .superclassImplementation()
          .ifPresent(
              superclassImplementation -> {
                superclassImplementation
                    .getAllModifiableMethodNames()
                    .forEach(componentImplementation::claimMethodName);
              });

      addFactoryMethods();
      addInterfaceMethods();
      addChildComponents();
      implementModifiableModuleMethods();

      addConstructor();

      if (graph.componentDescriptor().kind().isProducer()) {
        addCancellationListenerImplementation();
      }

      done = true;
      return componentImplementation;
    }

    /** Set the supertype for this generated class. */
    final void setSupertype() {
      if (componentImplementation.superclassImplementation().isPresent()) {
        componentImplementation.addSuperclass(
            componentImplementation.superclassImplementation().get().name());
      } else {
        componentImplementation.addSupertype(graph.componentTypeElement());
      }
    }

    /**
     * Adds {@code creator} as a nested creator class. Root components and subcomponents will nest
     * this in different classes.
     */
    abstract void addCreatorClass(TypeSpec creator);

    /** Adds component factory methods. */
    abstract void addFactoryMethods();

    void addInterfaceMethods() {
      // Each component method may have been declared by several supertypes. We want to implement
      // only one method for each distinct signature.
      ImmutableListMultimap<MethodSignature, ComponentMethodDescriptor>
          componentMethodsBySignature =
              Multimaps.index(
                  graph.componentDescriptor().entryPointMethods(), this::getMethodSignature);
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

    final void addCancellationListenerImplementation() {
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

      if (cancellationStatements.size() < STATEMENTS_PER_METHOD) {
        methodBuilder.addCode(CodeBlocks.concat(cancellationStatements)).build();
      } else {
        List<List<CodeBlock>> partitions =
            Lists.partition(cancellationStatements, STATEMENTS_PER_METHOD);
        for (List<CodeBlock> partition : partitions) {
          String methodName = componentImplementation.getUniqueMethodName("cancelProducers");
          MethodSpec method =
              methodBuilder(methodName)
                  .addModifiers(PRIVATE)
                  .addParameter(boolean.class, MAY_INTERRUPT_IF_RUNNING)
                  .addCode(CodeBlocks.concat(partition))
                  .build();
          methodBuilder.addStatement("$N($L)", method, MAY_INTERRUPT_IF_RUNNING);
          componentImplementation.addMethod(CANCELLATION_LISTENER_METHOD, method);
        }
      }

      Optional<CodeBlock> cancelParentStatement = cancelParentStatement();
      cancelParentStatement.ifPresent(methodBuilder::addCode);

      if (cancellationStatements.isEmpty()
          && !cancelParentStatement.isPresent()
          && componentImplementation.superclassImplementation().isPresent()) {
        // Partial child implementations that have no new cancellations don't need to override
        // the method just to call super().
        return;
      }

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

    Optional<CodeBlock> cancelParentStatement() {
      // Returns empty by default. Overridden in subclass(es) to add a statement if and only if the
      // component being generated is a concrete subcomponent implementation with a parent that
      // allows cancellation to propagate to it from subcomponents.
      return Optional.empty();
    }

    /**
     * For final components, reimplements all modifiable module methods that may have been modified.
     */
    private void implementModifiableModuleMethods() {
      if (componentImplementation.isAbstract()) {
        return;
      }
      componentImplementation
          .getAllModifiableModuleMethods()
          .forEach(this::implementModifiableModuleMethod);
    }

    private void implementModifiableModuleMethod(ComponentRequirement module, String methodName) {
      // TODO(b/117833324): only reimplement methods for modules that were abstract or were repeated
      // by an ancestor component.
      componentImplementation.addMethod(
          MODIFIABLE_BINDING_METHOD,
          methodBuilder(methodName)
              .addAnnotation(Override.class)
              .addModifiers(PROTECTED)
              .returns(TypeName.get(module.type()))
              .addStatement(
                  componentRequirementExpressions
                      .getExpression(module)
                      .getModifiableModuleMethodExpression(componentImplementation.name()))
              .build());
    }

    final MethodSignature getMethodSignature(ComponentMethodDescriptor method) {
      return MethodSignature.forComponentMethod(
          method, MoreTypes.asDeclared(graph.componentTypeElement().asType()), types);
    }

    final void addChildComponents() {
      for (BindingGraph subgraph : graph.subgraphs()) {
        // TODO(b/117833324): Can an abstract inner subcomponent implementation be elided if it's
        // totally empty?
        componentImplementation.addChild(
            subgraph.componentDescriptor(), buildChildImplementation(subgraph));
      }
    }

    final ComponentImplementation getChildSuperclassImplementation(ComponentDescriptor child) {
      // If the current component has superclass implementations, a superclass may contain a
      // reference to the child. Traverse this component's superimplementation hierarchy looking for
      // the child's implementation. The child superclass implementation may not be present in the
      // direct superclass implementations if the subcomponent builder was previously a pruned
      // binding.
      Optional<ComponentImplementation> currentSuperclassImplementation =
          componentImplementation.superclassImplementation();
      while (currentSuperclassImplementation.isPresent()) {
        Optional<ComponentImplementation> childSuperclassImplementation =
            currentSuperclassImplementation.get().childImplementation(child);
        if (childSuperclassImplementation.isPresent()) {
          return childSuperclassImplementation.get();
        }
        currentSuperclassImplementation =
            currentSuperclassImplementation.get().superclassImplementation();
      }

      // Otherwise, the superclass implementation is top-level, so we must recreate the
      // implementation object for the base implementation of the child by truncating the binding
      // graph at the child.
      BindingGraph truncatedBindingGraph = bindingGraphFactory.create(child);
      return componentImplementationFactory.createComponentImplementation(truncatedBindingGraph);
    }

    final ComponentImplementation buildChildImplementation(BindingGraph childGraph) {
      ComponentImplementation childImplementation =
          compilerOptions.aheadOfTimeSubcomponents()
              ? abstractInnerSubcomponent(childGraph.componentDescriptor())
              : concreteSubcomponent(childGraph.componentDescriptor());
      return topLevelImplementationComponent
            .currentImplementationSubcomponentBuilder()
            .componentImplementation(childImplementation)
            .bindingGraph(childGraph)
            .parentBuilder(Optional.of(this))
            .parentBindingExpressions(Optional.of(bindingExpressions))
            .parentRequirementExpressions(Optional.of(componentRequirementExpressions))
            .build()
            .subcomponentBuilder()
            .build();
    }

    /** Creates an inner abstract subcomponent implementation. */
    final ComponentImplementation abstractInnerSubcomponent(ComponentDescriptor child) {
      return new ComponentImplementation(
          componentImplementation,
          child,
          Optional.of(getChildSuperclassImplementation(child)),
          PROTECTED,
          componentImplementation.isAbstract() ? ABSTRACT : FINAL);
    }

    /** Creates a concrete inner subcomponent implementation. */
    final ComponentImplementation concreteSubcomponent(ComponentDescriptor child) {
      return new ComponentImplementation(
          componentImplementation,
          child,
          Optional.empty(), // superclassImplementation
          PRIVATE,
          FINAL);
    }

    final void addConstructor() {
      List<List<CodeBlock>> partitions =
          Lists.partition(componentImplementation.getInitializations(), STATEMENTS_PER_METHOD);
      ImmutableList<CodeBlock> componentRequirementInitializations =
          componentImplementation.getComponentRequirementInitializations();

      ImmutableList<ParameterSpec> constructorParameters = constructorParameters();
      MethodSpec.Builder constructor =
          constructorBuilder()
              .addModifiers(componentImplementation.isAbstract() ? PROTECTED : PRIVATE);

      if (!componentImplementation.isAbstract()) {
        constructor.addParameters(constructorParameters);
      }

      Optional<MethodSpec.Builder> configureInitialization =
          (partitions.isEmpty() && componentRequirementInitializations.isEmpty())
                  || !componentImplementation.isAbstract()
              ? Optional.empty()
              : Optional.of(configureInitializationMethodBuilder(constructorParameters));

      configureInitialization
          .orElse(constructor)
          .addCode(CodeBlocks.concat(componentRequirementInitializations));

      if (componentImplementation.superConfigureInitializationMethod().isPresent()) {
        MethodSpec superConfigureInitializationMethod =
            componentImplementation.superConfigureInitializationMethod().get();
        CodeBlock superInvocation =
            CodeBlock.of(
                "$N($L)",
                superConfigureInitializationMethod,
                parameterNames(superConfigureInitializationMethod.parameters));
        if (configureInitialization.isPresent()) {
          configureInitialization.get().addStatement("super.$L", superInvocation);
        } else if (!componentImplementation.isAbstract()) {
          constructor.addStatement(superInvocation);
        }
      }

      ImmutableList<ParameterSpec> initializeParameters = initializeParameters();
      CodeBlock initializeParametersCodeBlock = parameterNames(constructorParameters);

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
        configureInitialization
            .orElse(constructor)
            .addStatement("$L($L)", methodName, initializeParametersCodeBlock);
        componentImplementation.addMethod(INITIALIZE_METHOD, initializeMethod.build());
      }
      componentImplementation.addMethod(CONSTRUCTOR, constructor.build());
      configureInitialization.ifPresent(
          method -> componentImplementation.setConfigureInitializationMethod(method.build()));
    }

    /**
     * Returns a {@link MethodSpec.Builder} for the {@link
     * ComponentImplementation#configureInitializationMethod()}.
     */
    private MethodSpec.Builder configureInitializationMethodBuilder(
        ImmutableList<ParameterSpec> initializationMethodParameters) {
      String methodName = componentImplementation.getUniqueMethodName("configureInitialization");
      MethodSpec.Builder configureInitialization =
          methodBuilder(methodName)
              .addModifiers(PROTECTED)
              .addParameters(initializationMethodParameters);

      // Checks all super configureInitialization() methods to see if they have the same signature
      // as this one, and if so, adds as an @Override annotation
      for (Optional<ComponentImplementation> currentSuperImplementation =
              componentImplementation.superclassImplementation();
          currentSuperImplementation.isPresent();
          currentSuperImplementation =
              currentSuperImplementation.get().superclassImplementation()) {
        Optional<MethodSpec> superConfigureInitializationMethod =
            currentSuperImplementation.get().configureInitializationMethod();
        if (superConfigureInitializationMethod
            .filter(superMethod -> superMethod.name.equals(methodName))
            .filter(superMethod -> superMethod.parameters.equals(initializationMethodParameters))
            .isPresent()) {
          configureInitialization.addAnnotation(Override.class);
          break;
        }
      }

      return configureInitialization;
    }

    /** Returns the list of {@link ParameterSpec}s for the initialize methods. */
    final ImmutableList<ParameterSpec> initializeParameters() {
      return constructorParameters().stream()
          .map(param -> param.toBuilder().addModifiers(FINAL).build())
          .collect(toImmutableList());
    }

    /** Returns the list of {@link ParameterSpec}s for the constructor. */
    final ImmutableList<ParameterSpec> constructorParameters() {
      Optional<ClassName> componentCreatorName;
      if (componentImplementation.creatorImplementation().isPresent()) {
        componentCreatorName =
            componentImplementation.creatorImplementation().map(creator -> creator.name());
      } else {
        componentCreatorName =
            componentImplementation
                .baseImplementation()
                .filter(component -> component.componentDescriptor().hasCreator())
                .map(ComponentImplementation::getCreatorName);
      }

      if (componentCreatorName.isPresent()) {
        return ImmutableList.of(
            ParameterSpec.builder(componentCreatorName.get(), "builder").build());
      } else if (componentImplementation.isAbstract() && componentImplementation.isNested()) {
        // If we're generating an abstract inner subcomponent, then we are not implementing module
        // instance bindings and have no need for factory method parameters.
        return ImmutableList.of();
      } else if (graph.factoryMethod().isPresent()) {
        return getFactoryMethodParameterSpecs(graph);
      } else if (componentImplementation.isAbstract()) {
        // If we're generating an abstract base implementation of a subcomponent it's acceptable to
        // have neither a creator nor factory method.
        return ImmutableList.of();
      } else {
        throw new AssertionError(
            "Expected either a component creator or factory method but found neither.");
      }
    }
  }

  /** Builds a root component implementation. */
  // TODO(ronshapiro): rename this as TopLevelComponentImplementationBuilder, since it may not
  // always be a root component
  static final class RootComponentImplementationBuilder extends ComponentImplementationBuilder {
    private final ClassName componentCreatorName;

    @Inject
    RootComponentImplementationBuilder(ComponentImplementation componentImplementation) {
      checkArgument(!componentImplementation.superclassImplementation().isPresent());
      this.componentCreatorName = componentImplementation.creatorImplementation().get().name();
    }

    @Override
    void addCreatorClass(TypeSpec creator) {
      componentImplementation.addType(COMPONENT_CREATOR, creator);
    }

    @Override
    void addFactoryMethods() {
      // Only top-level components have the factory builder() method.
      // Mirror the user's creator API type if they had one.
      MethodSpec creatorFactoryMethod =
          methodBuilder("builder")
              .addModifiers(PUBLIC, STATIC)
              .returns(
                  creatorDescriptor()
                      .map(creatorDescriptor -> ClassName.get(creatorDescriptor.typeElement()))
                      .orElse(componentCreatorName))
              .addStatement("return new $T()", componentCreatorName)
              .build();
      componentImplementation.addMethod(BUILDER_METHOD, creatorFactoryMethod);
      if (canInstantiateAllRequirements()) {
        CharSequence buildMethodName =
            creatorDescriptor().isPresent()
                ? creatorDescriptor().get().factoryMethod().getSimpleName()
                : "build";
        componentImplementation.addMethod(
            BUILDER_METHOD,
            methodBuilder("create")
                .returns(ClassName.get(super.graph.componentTypeElement()))
                .addModifiers(PUBLIC, STATIC)
                .addStatement("return new Builder().$L()", buildMethodName)
                .build());
      }
    }

    private Optional<ComponentCreatorDescriptor> creatorDescriptor() {
      return graph.componentDescriptor().creatorDescriptor();
    }

    /** {@code true} if all of the graph's required dependencies can be automatically constructed */
    boolean canInstantiateAllRequirements() {
      return !Iterables.any(
          graph.componentRequirements(),
          dependency -> dependency.requiresAPassedInstance(elements, types));
    }
  }

  /**
   * Builds a subcomponent implementation. If generating ahead-of-time subcomponents, this may be an
   * abstract base class implementation, an abstract inner implementation, or a concrete
   * implementation that extends an abstract base implementation. Otherwise it represents a private,
   * inner, concrete, final implementation of a subcomponent which extends a user defined type.
   */
  static final class SubcomponentImplementationBuilder extends ComponentImplementationBuilder {
    final Optional<ComponentImplementationBuilder> parent;

    @Inject
    SubcomponentImplementationBuilder(
        @ParentComponent Optional<ComponentImplementationBuilder> parent) {
      this.parent = parent;
    }

    @Override
    void addCreatorClass(TypeSpec creator) {
      if (parent.isPresent()) {
        // In an inner implementation of a subcomponent the creator is a peer class.
        parent.get().componentImplementation.addType(SUBCOMPONENT, creator);
      } else {
        componentImplementation.addType(SUBCOMPONENT, creator);
      }
    }

    @Override
    void addFactoryMethods() {
      // Only construct instances of subcomponents that have concrete implementations.
      if (!componentImplementation.isAbstract()) {
        // Use the parent's factory method to create this subcomponent if the
        // subcomponent was not added via {@link dagger.Module#subcomponents()}.
        graph.factoryMethod().ifPresent(this::createSubcomponentFactoryMethod);
      }
    }

    void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
      checkState(parent.isPresent());
      parent
          .get()
          .componentImplementation
          .addMethod(
              COMPONENT_METHOD,
              MethodSpec.overriding(factoryMethod, parentType(), types)
                  .addStatement(
                      "return new $T($L)",
                      componentImplementation.name(),
                      getFactoryMethodParameterSpecs(graph).stream()
                          .map(param -> CodeBlock.of("$N", param))
                          .collect(toParametersCodeBlock()))
                  .build());
    }

    DeclaredType parentType() {
      return asDeclared(parent.get().graph.componentTypeElement().asType());
    }

    @Override
    void addInterfaceMethods() {
      if (componentImplementation.superclassImplementation().isPresent()) {
        // Since we're overriding a subcomponent implementation we add to its implementation given
        // an expanded binding graph.

        ComponentImplementation superclassImplementation =
            componentImplementation.superclassImplementation().get();
        for (ModifiableBindingMethod superclassModifiableBindingMethod :
            superclassImplementation.getModifiableBindingMethods()) {
          bindingExpressions
              .modifiableBindingExpressions()
              .reimplementedModifiableBindingMethod(superclassModifiableBindingMethod)
              .ifPresent(componentImplementation::addImplementedModifiableBindingMethod);
        }
      } else {
        super.addInterfaceMethods();
      }
    }

    @Override
    Optional<CodeBlock> cancelParentStatement() {
      if (!shouldPropagateCancellationToParent()){
        return Optional.empty();
      }
      return Optional.of(
          CodeBlock.builder()
              .addStatement(
                  "$T.this.$N($N)",
                  parent.get().componentImplementation.name(),
                  CANCELLATION_LISTENER_METHOD_NAME,
                  MAY_INTERRUPT_IF_RUNNING)
              .build());
    }

    boolean shouldPropagateCancellationToParent() {
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
