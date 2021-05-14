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

package dagger.internal.codegen.componentgenerator;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind.COMPONENT_CREATOR;
import static dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind.SUBCOMPONENT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentCreatorKind;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentBindingExpressions;
import dagger.internal.codegen.writing.ComponentCreatorImplementation;
import dagger.internal.codegen.writing.ComponentImplementation;
import dagger.internal.codegen.writing.ComponentRequirementExpressions;
import dagger.internal.codegen.writing.ParentComponent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;

/** A builder of {@link ComponentImplementation}s. */
// This only needs to be public because it's referenced in an entry point.
public final class ComponentImplementationBuilder {
  private final Optional<ComponentImplementationBuilder> parent;
  private final BindingGraph graph;
  private final ComponentBindingExpressions bindingExpressions;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final ComponentImplementation componentImplementation;
  private final ComponentCreatorImplementationFactory componentCreatorImplementationFactory;
  private final TopLevelImplementationComponent topLevelImplementationComponent;
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final KotlinMetadataUtil metadataUtil;
  private boolean done;

  @Inject
  ComponentImplementationBuilder(
      @ParentComponent Optional<ComponentImplementationBuilder> parent,
      BindingGraph graph,
      ComponentBindingExpressions bindingExpressions,
      ComponentRequirementExpressions componentRequirementExpressions,
      ComponentImplementation componentImplementation,
      ComponentCreatorImplementationFactory componentCreatorImplementationFactory,
      TopLevelImplementationComponent topLevelImplementationComponent,
      DaggerTypes types,
      DaggerElements elements,
      KotlinMetadataUtil metadataUtil) {
    this.parent = parent;
    this.graph = graph;
    this.bindingExpressions = bindingExpressions;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.componentImplementation = componentImplementation;
    this.componentCreatorImplementationFactory = componentCreatorImplementationFactory;
    this.types = types;
    this.elements = elements;
    this.topLevelImplementationComponent = topLevelImplementationComponent;
    this.metadataUtil = metadataUtil;
  }

  /**
   * Returns a {@link ComponentImplementation} for this component. This is only intended to be
   * called once (and will throw on successive invocations). If the component must be regenerated,
   * use a new instance.
   */
  ComponentImplementation build() {
    checkState(
        !done,
        "ComponentImplementationBuilder has already built the ComponentImplementation for [%s].",
        componentImplementation.name());

    componentCreatorImplementationFactory.create()
        .map(ComponentCreatorImplementation::spec)
        .ifPresent(this::addCreatorClass);

    elements
        .getLocalAndInheritedMethods(graph.componentTypeElement())
        .forEach(method -> componentImplementation.claimMethodName(method.getSimpleName()));

    addFactoryMethods();
    addInterfaceMethods();
    addChildComponents();

    done = true;
    return componentImplementation;
  }

  private void addCreatorClass(TypeSpec creator) {
    if (parent.isPresent()) {
      // In an inner implementation of a subcomponent the creator is a peer class.
      parent.get().componentImplementation.addType(SUBCOMPONENT, creator);
    } else {
      componentImplementation.addType(COMPONENT_CREATOR, creator);
    }
  }

  private void addFactoryMethods() {
    if (parent.isPresent()) {
      graph.factoryMethod().ifPresent(this::createSubcomponentFactoryMethod);
    } else {
      createRootComponentFactoryMethod();
    }
  }

  private void addInterfaceMethods() {
    // Each component method may have been declared by several supertypes. We want to implement
    // only one method for each distinct signature.
    ImmutableListMultimap<MethodSignature, ComponentMethodDescriptor> componentMethodsBySignature =
        Multimaps.index(graph.componentDescriptor().entryPointMethods(), this::getMethodSignature);
    for (List<ComponentMethodDescriptor> methodsWithSameSignature :
        Multimaps.asMap(componentMethodsBySignature).values()) {
      ComponentMethodDescriptor anyOneMethod = methodsWithSameSignature.stream().findAny().get();
      MethodSpec methodSpec = bindingExpressions.getComponentMethod(anyOneMethod);

      componentImplementation.addMethod(COMPONENT_METHOD, methodSpec);
    }
  }

  private MethodSignature getMethodSignature(ComponentMethodDescriptor method) {
    return MethodSignature.forComponentMethod(
        method, MoreTypes.asDeclared(graph.componentTypeElement().asType()), types);
  }

  private void addChildComponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      componentImplementation.addType(SUBCOMPONENT, childComponent(subgraph));
    }
  }

  private TypeSpec childComponent(BindingGraph childGraph) {
    return topLevelImplementationComponent
        .currentImplementationSubcomponentBuilder()
        .componentImplementation(subcomponent(childGraph))
        .bindingGraph(childGraph)
        .parentBuilder(Optional.of(this))
        .parentBindingExpressions(Optional.of(bindingExpressions))
        .parentRequirementExpressions(Optional.of(componentRequirementExpressions))
        .build()
        .componentImplementationBuilder()
        .build()
        .generate()
        .build();
  }

  /** Creates an inner subcomponent implementation. */
  private ComponentImplementation subcomponent(BindingGraph childGraph) {
    return componentImplementation.childComponentImplementation(childGraph);
  }
  private void createRootComponentFactoryMethod() {
    checkState(!parent.isPresent());
    // Top-level components have a static method that returns a builder or factory for the
    // component. If the user defined a @Component.Builder or @Component.Factory, an
    // implementation of their type is returned. Otherwise, an autogenerated Builder type is
    // returned.
    // TODO(cgdecker): Replace this abomination with a small class?
    // Better yet, change things so that an autogenerated builder type has a descriptor of sorts
    // just like a user-defined creator type.
    ComponentCreatorKind creatorKind;
    ClassName creatorType;
    String factoryMethodName;
    boolean noArgFactoryMethod;
    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        graph.componentDescriptor().creatorDescriptor();
    if (creatorDescriptor.isPresent()) {
      ComponentCreatorDescriptor descriptor = creatorDescriptor.get();
      creatorKind = descriptor.kind();
      creatorType = ClassName.get(descriptor.typeElement());
      factoryMethodName = descriptor.factoryMethod().getSimpleName().toString();
      noArgFactoryMethod = descriptor.factoryParameters().isEmpty();
    } else {
      creatorKind = BUILDER;
      creatorType = componentImplementation.getCreatorName();
      factoryMethodName = "build";
      noArgFactoryMethod = true;
    }

    MethodSpec creatorFactoryMethod =
        methodBuilder(creatorKind.methodName())
            .addModifiers(PUBLIC, STATIC)
            .returns(creatorType)
            .addStatement("return new $T()", componentImplementation.getCreatorName())
            .build();
    componentImplementation.addMethod(BUILDER_METHOD, creatorFactoryMethod);
    if (noArgFactoryMethod && canInstantiateAllRequirements()) {
      componentImplementation.addMethod(
          BUILDER_METHOD,
          methodBuilder("create")
              .returns(ClassName.get(graph.componentTypeElement()))
              .addModifiers(PUBLIC, STATIC)
              .addStatement("return new $L().$L()", creatorKind.typeName(), factoryMethodName)
              .build());
    }
  }

  /** {@code true} if all of the graph's required dependencies can be automatically constructed */
  private boolean canInstantiateAllRequirements() {
    return !Iterables.any(
        graph.componentRequirements(),
        dependency -> dependency.requiresAPassedInstance(elements, metadataUtil));
  }

  private void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
    checkState(parent.isPresent());
    Collection<ParameterSpec> params = getFactoryMethodParameters(graph).values();
    MethodSpec.Builder method = MethodSpec.overriding(factoryMethod, parentType(), types);
    params.forEach(
        param -> method.addStatement("$T.checkNotNull($N)", Preconditions.class, param));
    method.addStatement(
        "return new $T($L)", componentImplementation.name(), parameterNames(params));

    parent.get().componentImplementation.addMethod(COMPONENT_METHOD, method.build());
  }

  private DeclaredType parentType() {
    return asDeclared(parent.get().graph.componentTypeElement().asType());
  }
  /**
   * Returns the map of {@link ComponentRequirement}s to {@link ParameterSpec}s for the given
   * graph's factory method.
   */
  private static Map<ComponentRequirement, ParameterSpec> getFactoryMethodParameters(
      BindingGraph graph) {
    return Maps.transformValues(graph.factoryMethodParameters(), ParameterSpec::get);
  }
}
