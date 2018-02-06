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
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.COMPONENT_REQUIREMENT_FIELD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.BUILDER_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.COMPONENT_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.CONSTRUCTOR;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.INITIALIZE_METHOD;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.COMPONENT_BUILDER;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.SUBCOMPONENT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

/** Creates the implementation class for a component or subcomponent. */
abstract class ComponentWriter {
  static TypeSpec.Builder writeComponent(
      DaggerTypes types,
      Elements elements,
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph) {
    GeneratedComponentModel generatedComponentModel = GeneratedComponentModel.forComponent(name);
    SubcomponentNames subcomponentNames = new SubcomponentNames(graph, keyFactory);
    OptionalFactories optionalFactories = new OptionalFactories();
    ComponentRequirementFields componentRequirementFields = new ComponentRequirementFields();
    ComponentBindingExpressions bindingExpressions =
        new ComponentBindingExpressions(
            graph,
            generatedComponentModel,
            subcomponentNames,
            componentRequirementFields,
            optionalFactories,
            types,
            elements,
            compilerOptions);
    return new RootComponentWriter(
            types,
            elements,
            compilerOptions,
            graph,
            generatedComponentModel,
            subcomponentNames,
            optionalFactories,
            bindingExpressions,
            componentRequirementFields)
        .write();
  }

  private static TypeSpec writeSubcomponent(ComponentWriter parent, BindingGraph childGraph) {
    ClassName parentName = parent.generatedComponentModel.name();
    ClassName childName =
        parentName.nestedClass(
            parent.subcomponentNames.get(childGraph.componentDescriptor()) + "Impl");
    GeneratedComponentModel childGeneratedComponentModel =
        GeneratedComponentModel.forSubcomponent(childName);
    ComponentRequirementFields childComponentRequirementFields =
        parent.componentRequirementFields.forChildComponent();
    ComponentBindingExpressions childBindingExpressions =
        parent.bindingExpressions.forChildComponent(
            childGraph, childGeneratedComponentModel, childComponentRequirementFields);
    return new SubcomponentWriter(
            parent,
            childGraph,
            childGeneratedComponentModel,
            childBindingExpressions,
            childComponentRequirementFields)
        .write()
        .build();
  }

  private final Elements elements;
  private final DaggerTypes types;
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final SubcomponentNames subcomponentNames;
  private final ComponentBindingExpressions bindingExpressions;
  private final ComponentRequirementFields componentRequirementFields;
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentRequirementField.Factory componentRequirementFieldFactory;
  private final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  private final OptionalFactories optionalFactories;
  private ComponentBuilder builder;
  private boolean done;

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

  private ComponentWriter(
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

  /**
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  protected final TypeSpec.Builder write() {
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

    constructor.addParameter(builder.name(), "builder");
  }

  /**
   * Adds {@code builder} as a nested builder class. Root components and subcomponents will nest
   * this in different classes.
   */
  protected abstract void addBuilderClass(TypeSpec builder);

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
      generatedComponentModel.addType(SUBCOMPONENT, writeSubcomponent(this, subgraph));
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
        initializeMethod.addParameter(builder.name(), "builder", FINAL);
        constructor.addStatement("$L(builder)", methodName);
      } else {
        constructor.addStatement("$L()", methodName);
      }
      generatedComponentModel.addMethod(INITIALIZE_METHOD, initializeMethod.build());
    }
  }

  /**
   * Creates the implementation class for the root component.
   */
  private static final class RootComponentWriter extends ComponentWriter {
    RootComponentWriter(
        DaggerTypes types,
        Elements elements,
        CompilerOptions compilerOptions,
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        SubcomponentNames subcomponentNames,
        OptionalFactories optionalFactories,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields) {
      super(
          types,
          elements,
          compilerOptions,
          graph,
          generatedComponentModel,
          subcomponentNames,
          optionalFactories,
          bindingExpressions,
          componentRequirementFields);
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
                      : super.builder.name())
              .addStatement("return new $T()", super.builder.name())
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
   * Creates the nested implementation class for a subcomponent.
   */
  private static final class SubcomponentWriter extends ComponentWriter {
    private final ComponentWriter parent;

    SubcomponentWriter(
        ComponentWriter parent,
        BindingGraph graph,
        GeneratedComponentModel generatedComponentModel,
        ComponentBindingExpressions bindingExpressions,
        ComponentRequirementFields componentRequirementFields) {
      super(
          parent.types,
          parent.elements,
          parent.compilerOptions,
          graph,
          generatedComponentModel,
          parent.subcomponentNames,
          parent.optionalFactories,
          bindingExpressions,
          componentRequirementFields);
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
      Optional.ofNullable(
              parent
                  .graph
                  .componentDescriptor()
                  .subcomponentsByFactoryMethod()
                  .inverse()
                  .get(super.graph.componentDescriptor()))
          .map(method -> method.methodElement())
          .ifPresent(this::createSubcomponentFactoryMethod);
    }

    private void createSubcomponentFactoryMethod(ExecutableElement factoryMethod) {
      parent.generatedComponentModel.addMethod(
          COMPONENT_METHOD,
          MethodSpec.overriding(factoryMethod, parentType(), parent.types)
              .addStatement(
                  "return new $T($L)",
                  super.generatedComponentModel.name(),
                  factoryMethod
                      .getParameters()
                      .stream()
                      .map(param -> CodeBlock.of("$L", param.getSimpleName()))
                      .collect(toParametersCodeBlock()))
              .build());

      writeSubcomponentConstructorFor(factoryMethod);
    }

    private void writeSubcomponentConstructorFor(ExecutableElement factoryMethod) {
      Set<ComponentRequirement> modules =
          asExecutable(super.types.asMemberOf(parentType(), factoryMethod))
              .getParameterTypes()
              .stream()
              .map(param -> ComponentRequirement.forModule(asTypeElement(param).asType()))
              .collect(toImmutableSet());

      for (ComponentRequirement module : modules) {
        FieldSpec field = createSubcomponentModuleField(module);
        super.constructor
            .addParameter(field.type, field.name)
            .addStatement("this.$1N = $2T.checkNotNull($1N)", field, Preconditions.class);
      }

      Set<ComponentRequirement> remainingModules =
          super.graph
              .componentRequirements()
              .stream()
              .filter(requirement -> requirement.kind().equals(ComponentRequirement.Kind.MODULE))
              .filter(requirement -> !modules.contains(requirement))
              .collect(toImmutableSet());

      for (ComponentRequirement module : remainingModules) {
        FieldSpec field = createSubcomponentModuleField(module);
        super.constructor.addStatement("this.$N = new $T()", field, field.type);
      }
    }

    // TODO(user): We shouldn't have to create these manually. They should be created lazily
    // by ComponentRequirementFields, similar to how it's done for ComponentBindingExpressions.
    private FieldSpec createSubcomponentModuleField(ComponentRequirement module) {
      TypeElement moduleElement = module.typeElement();
      String fieldName =
          super.generatedComponentModel.getUniqueFieldName(
              UPPER_CAMEL.to(LOWER_CAMEL, moduleElement.getSimpleName().toString()));
      FieldSpec contributionField =
          FieldSpec.builder(ClassName.get(moduleElement), fieldName)
              .addModifiers(PRIVATE, FINAL).build();

      super.generatedComponentModel.addField(COMPONENT_REQUIREMENT_FIELD, contributionField);
      super.componentRequirementFields.add(
          ComponentRequirementField.componentField(
              module, contributionField, super.generatedComponentModel.name()));

      return contributionField;
    }

    private DeclaredType parentType() {
      return asDeclared(parent.graph.componentType().asType());
    }
  }
}
