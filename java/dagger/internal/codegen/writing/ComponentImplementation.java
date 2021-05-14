/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.producers.CancellationPolicy.Propagation.PROPAGATE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentCreatorKind;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.KeyVariableNamer;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.CodeBlocks;
import dagger.internal.codegen.javapoet.TypeSpecs;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.internal.CancellationListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/** The implementation of a component type. */
public final class ComponentImplementation {
  /** A type of field that this component can contain. */
  public enum FieldSpecKind {
    /** A field for a component shard. */
    COMPONENT_SHARD,

    /** A field required by the component, e.g. module instances. */
    COMPONENT_REQUIREMENT_FIELD,

    /**
     * A field for the lock and cached value for {@linkplain PrivateMethodBindingExpression
     * private-method scoped bindings}.
     */
    PRIVATE_METHOD_SCOPED_FIELD,

    /** A framework field for type T, e.g. {@code Provider<T>}. */
    FRAMEWORK_FIELD,

    /** A static field that always returns an absent {@code Optional} value for the binding. */
    ABSENT_OPTIONAL_FIELD
  }

  /** A type of method that this component can contain. */
  // TODO(bcorso, dpb): Change the oder to constructor, initialize, component, then private
  // (including MIM and AOM—why treat those separately?).
  public enum MethodSpecKind {
    /** The component constructor. */
    CONSTRUCTOR,

    /** A builder method for the component. (Only used by the root component.) */
    BUILDER_METHOD,

    /** A private method that wraps dependency expressions. */
    PRIVATE_METHOD,

    /** An initialization method that initializes component requirements and framework types. */
    INITIALIZE_METHOD,

    /** An implementation of a component interface method. */
    COMPONENT_METHOD,

    /** A private method that encapsulates members injection logic for a binding. */
    MEMBERS_INJECTION_METHOD,

    /** A static method that always returns an absent {@code Optional} value for the binding. */
    ABSENT_OPTIONAL_METHOD,

    /**
     * The {@link dagger.producers.internal.CancellationListener#onProducerFutureCancelled(boolean)}
     * method for a production component.
     */
    CANCELLATION_LISTENER_METHOD,
    ;
  }

  /** A type of nested class that this component can contain. */
  public enum TypeSpecKind {
    /** A factory class for a present optional binding. */
    PRESENT_FACTORY,

    /** A class for the component creator (only used by the root component.) */
    COMPONENT_CREATOR,

    /** A provider class for a component provision. */
    COMPONENT_PROVISION_FACTORY,

    /** A class for the subcomponent or subcomponent builder. */
    SUBCOMPONENT
  }

  /** Returns a component implementation for a top-level component. */
  public static ComponentImplementation topLevelComponentImplementation(
      BindingGraph graph,
      ClassName name,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions,
      DaggerElements elements) {
    return create(name, Optional.empty(), graph, subcomponentNames, compilerOptions, elements);
  }

  private static ComponentImplementation create(
      ClassName name,
      Optional<ComponentImplementation> parent,
      BindingGraph graph,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions,
      DaggerElements elements) {
    return new ComponentImplementation(
        name,
        parent,
        /* shardOwner= */ Optional.empty(),
        /* externalReferenceBlock= */ CodeBlock.of("$T.this", name),
        graph,
        subcomponentNames,
        compilerOptions,
        elements);
  }

  private static ComponentImplementation createShard(
      ClassName shardName, ComponentImplementation shardOwner) {
    String fieldName =
        shardOwner.getUniqueFieldName(UPPER_CAMEL.to(LOWER_CAMEL, shardName.simpleName()));
    ComponentImplementation shardImplementation =
        new ComponentImplementation(
            shardName,
            shardOwner.parent,
            Optional.of(shardOwner),
            /* externalReferenceBlock= */ CodeBlock.of("$T.this.$N", shardOwner.name, fieldName),
            shardOwner.graph,
            shardOwner.subcomponentNames,
            shardOwner.compilerOptions,
            shardOwner.elements);

    // Add the shard class and field to the shardOwner.
    shardOwner.addTypeSupplier(() -> shardImplementation.generate().build());
    shardOwner.addField(
        FieldSpecKind.COMPONENT_SHARD,
        FieldSpec.builder(shardName, fieldName, PRIVATE, FINAL)
            .initializer("new $T()", shardName)
            .build());

    return shardImplementation;
  }

  /** The boolean parameter of the onProducerFutureCancelled method. */
  public static final ParameterSpec MAY_INTERRUPT_IF_RUNNING_PARAM =
      ParameterSpec.builder(boolean.class, "mayInterruptIfRunning").build();

  private static final String CANCELLATION_LISTENER_METHOD_NAME = "onProducerFutureCancelled";

  /**
   * How many statements per {@code initialize()} or {@code onProducerFutureCancelled()} method
   * before they get partitioned.
   */
  private static final int STATEMENTS_PER_METHOD = 100;

  private ComponentImplementation currentShard = this;
  private final Map<Key, ComponentImplementation> shardsByKey = new HashMap<>();
  private final Optional<ComponentImplementation> shardOwner;
  private final Optional<ComponentImplementation> parent;
  private final BindingGraph graph;
  private final ClassName name;
  private final SubcomponentNames subcomponentNames;
  private final CompilerOptions compilerOptions;
  private final DaggerElements elements;
  private final CodeBlock externalReferenceBlock;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final List<CodeBlock> initializations = new ArrayList<>();
  private final Map<Key, CodeBlock> cancellations = new LinkedHashMap<>();
  private final List<CodeBlock> componentRequirementInitializations = new ArrayList<>();
  private final Map<ComponentRequirement, String> componentRequirementParameterNames =
      new HashMap<>();
  private final ListMultimap<FieldSpecKind, FieldSpec> fieldSpecsMap =
      MultimapBuilder.enumKeys(FieldSpecKind.class).arrayListValues().build();
  private final ListMultimap<MethodSpecKind, MethodSpec> methodSpecsMap =
      MultimapBuilder.enumKeys(MethodSpecKind.class).arrayListValues().build();
  private final ListMultimap<TypeSpecKind, TypeSpec> typeSpecsMap =
      MultimapBuilder.enumKeys(TypeSpecKind.class).arrayListValues().build();
  private final List<Supplier<TypeSpec>> typeSuppliers = new ArrayList<>();

  private ComponentImplementation(
      ClassName name,
      Optional<ComponentImplementation> parent,
      Optional<ComponentImplementation> shardOwner,
      CodeBlock externalReferenceBlock,
      BindingGraph graph,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions,
      DaggerElements elements) {
    this.name = name;
    this.parent = parent;
    this.shardOwner = shardOwner;
    this.externalReferenceBlock = externalReferenceBlock;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
    this.compilerOptions = compilerOptions;
    this.elements = elements;
    if (graph.componentDescriptor().isProduction()) {
      claimMethodName(CANCELLATION_LISTENER_METHOD_NAME);
    }
  }

  /** Returns a component implementation that is a child of the current implementation. */
  public ComponentImplementation childComponentImplementation(BindingGraph graph) {
    checkState(!shardOwner.isPresent(), "Shards cannot create child components.");
    ClassName childName = getSubcomponentName(graph.componentDescriptor());
    return create(
        childName, Optional.of(this), graph, subcomponentNames, compilerOptions, elements);
  }

  /** Returns a component implementation that is a shard of the current implementation. */
  public ComponentImplementation shardImplementation(Key key) {
    checkState(!shardOwner.isPresent(), "Shards cannot create other shards.");
    if (!shardsByKey.containsKey(key)) {
      int keysPerShard = compilerOptions.keysPerComponentShard(graph.componentTypeElement());
      if (!shardsByKey.isEmpty() && shardsByKey.size() % keysPerShard == 0) {
        ClassName shardName = name.nestedClass("Shard" + shardsByKey.size() / keysPerShard);
        currentShard = createShard(shardName, this);
      }
      shardsByKey.put(key, currentShard);
    }
    return shardsByKey.get(key);
  }

  /** Returns a reference to this compenent when called from a class nested in this component. */
  public CodeBlock externalReferenceBlock() {
    return externalReferenceBlock;
  }

  // TODO(ronshapiro): see if we can remove this method and instead inject it in the objects that
  // need it.
  /** Returns the binding graph for the component being generated. */
  public BindingGraph graph() {
    return graph;
  }

  /** Returns the descriptor for the component being generated. */
  public ComponentDescriptor componentDescriptor() {
    return graph.componentDescriptor();
  }

  /** Returns the name of the component. */
  public ClassName name() {
    return name;
  }

  /** Returns whether or not the implementation is nested within another class. */
  public boolean isNested() {
    return name.enclosingClassName() != null;
  }

  /**
   * Returns the kind of this component's creator.
   *
   * @throws IllegalStateException if the component has no creator
   */
  private ComponentCreatorKind creatorKind() {
    checkState(componentDescriptor().hasCreator());
    return componentDescriptor()
        .creatorDescriptor()
        .map(ComponentCreatorDescriptor::kind)
        .orElse(BUILDER);
  }

  /**
   * Returns the name of the creator class for this component. It will be a sibling of this
   * generated class unless this is a top-level component, in which case it will be nested.
   */
  public ClassName getCreatorName() {
    return isNested()
        ? name.peerClass(subcomponentNames.getCreatorName(componentDescriptor()))
        : name.nestedClass(creatorKind().typeName());
  }

  /** Returns the name of the nested implementation class for a child component. */
  private ClassName getSubcomponentName(ComponentDescriptor childDescriptor) {
    checkArgument(
        componentDescriptor().childComponents().contains(childDescriptor),
        "%s is not a child component of %s",
        childDescriptor.typeElement(),
        componentDescriptor().typeElement());
    // TODO(erichang): Hacky fix to shorten the suffix if we're too deeply
    // nested to save on file name length. 2 chosen arbitrarily.
    String suffix = name.simpleNames().size() > 2 ? "I" : "Impl";
    return name.nestedClass(subcomponentNames.get(childDescriptor) + suffix);
  }

  /**
   * Returns the simple name of the creator implementation class for the given subcomponent creator
   * {@link Key}.
   */
  String getSubcomponentCreatorSimpleName(Key key) {
    return subcomponentNames.getCreatorName(key);
  }

  /** Returns {@code true} if {@code type} is accessible from the generated component. */
  boolean isTypeAccessible(TypeMirror type) {
    return isTypeAccessibleFrom(type, name.packageName());
  }

  // TODO(dpb): Consider taking FieldSpec, and returning identical FieldSpec with unique name?
  /** Adds the given field to the component. */
  public void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec) {
    fieldSpecsMap.put(fieldKind, fieldSpec);
  }

  // TODO(dpb): Consider taking MethodSpec, and returning identical MethodSpec with unique name?
  /** Adds the given method to the component. */
  public void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec) {
    methodSpecsMap.put(methodKind, methodSpec);
  }

  /** Adds the given type to the component. */
  public void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    typeSpecsMap.put(typeKind, typeSpec);
  }

  /** Adds a {@link Supplier} for the SwitchingProvider for the component. */
  void addTypeSupplier(Supplier<TypeSpec> typeSpecSupplier) {
    typeSuppliers.add(typeSpecSupplier);
  }

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock) {
    initializations.add(codeBlock);
  }

  /** Adds the given code block that initializes a {@link ComponentRequirement}. */
  void addComponentRequirementInitialization(CodeBlock codeBlock) {
    componentRequirementInitializations.add(codeBlock);
  }

  /** Adds the given cancellation statement to the cancellation listener method of the component. */
  void addCancellation(Key key, CodeBlock codeBlock) {
    // Store cancellations by key to avoid adding the same cancellation twice.
    cancellations.putIfAbsent(key, codeBlock);
  }

  /** Returns a new, unique field name for the component based on the given name. */
  String getUniqueFieldName(String name) {
    return componentFieldNames.getUniqueName(name);
  }

  /** Returns a new, unique method name for the component based on the given name. */
  public String getUniqueMethodName(String name) {
    return componentMethodNames.getUniqueName(name);
  }

  /** Returns a new, unique method name for a getter method for the given request. */
  String getUniqueMethodName(BindingRequest request) {
    return uniqueMethodName(request, KeyVariableNamer.name(request.key()));
  }

  private String uniqueMethodName(BindingRequest request, String bindingName) {
    // This name is intentionally made to match the name for fields in fastInit
    // in order to reduce the constant pool size. b/162004246
    String baseMethodName = bindingName
        + (request.isRequestKind(RequestKind.INSTANCE)
            ? ""
            : UPPER_UNDERSCORE.to(UPPER_CAMEL, request.kindName()));
    return getUniqueMethodName(baseMethodName);
  }

  /**
   * Gets the parameter name to use for the given requirement for this component, starting with the
   * given base name if no parameter name has already been selected for the requirement.
   */
  public String getParameterName(ComponentRequirement requirement, String baseName) {
    return componentRequirementParameterNames.computeIfAbsent(
        requirement, r -> getUniqueFieldName(baseName));
  }

  /** Claims a new method name for the component. Does nothing if method name already exists. */
  public void claimMethodName(CharSequence name) {
    componentMethodNames.claim(name);
  }

  /** Generates the component and returns the resulting {@link TypeSpec.Builder}. */
  public TypeSpec.Builder generate() {
    TypeSpec.Builder builder = classBuilder(name);

    if (!shardOwner.isPresent()) {
      TypeSpecs.addSupertype(builder, graph.componentTypeElement());

      addConstructorAndInitializationMethods();

      if (graph.componentDescriptor().isProduction()) {
        TypeSpecs.addSupertype(builder, elements.getTypeElement(CancellationListener.class));
        addCancellationListenerImplementation();
      }
    }

    modifiers().forEach(builder::addModifiers);
    fieldSpecsMap.asMap().values().forEach(builder::addFields);
    methodSpecsMap.asMap().values().forEach(builder::addMethods);
    typeSpecsMap.asMap().values().forEach(builder::addTypes);
    typeSuppliers.stream().map(Supplier::get).forEach(builder::addType);
    return builder;
  }

  private ImmutableSet<Modifier> modifiers() {
    if (isNested()) {
      return ImmutableSet.of(PRIVATE, FINAL);
    }
    return graph.componentTypeElement().getModifiers().contains(PUBLIC)
        // TODO(ronshapiro): perhaps all generated components should be non-public?
        ? ImmutableSet.of(PUBLIC, FINAL)
        : ImmutableSet.of(FINAL);
  }

  /** Creates and adds the constructor and methods needed for initializing the component. */
  private void addConstructorAndInitializationMethods() {
    MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
    ImmutableList<ParameterSpec> parameters = constructorParameters();
    constructor.addParameters(parameters);
    constructor.addCode(CodeBlocks.concat(componentRequirementInitializations));

    // TODO(cgdecker): It's not the case that each initialize() method has need for all of the
    // given parameters. In some cases, those parameters may have already been assigned to fields
    // which could be referenced instead. In other cases, an initialize method may just not need
    // some of the parameters because the set of initializations in that partition does not
    // include any reference to them. Right now, the Dagger code has no way of getting that
    // information because, among other things, componentImplementation.getImplementations() just
    // returns a bunch of CodeBlocks with no semantic information. Additionally, we may not know
    // yet whether a field will end up needing to be created for a specific requirement, and we
    // don't want to create a field that ends up only being used during initialization.
    CodeBlock args = parameterNames(parameters);
    ImmutableList<MethodSpec> initializationMethods =
        createPartitionedMethods(
            "initialize",
            makeFinal(parameters),
            initializations,
            methodName ->
                methodBuilder(methodName)
                    /* TODO(gak): Strictly speaking, we only need the suppression here if we are
                     * also initializing a raw field in this method, but the structure of this
                     * code makes it awkward to pass that bit through.  This will be cleaned up
                     * when we no longer separate fields and initialization as we do now. */
                    .addAnnotation(suppressWarnings(UNCHECKED)));
    for (MethodSpec initializationMethod : initializationMethods) {
      constructor.addStatement("$N($L)", initializationMethod, args);
      addMethod(MethodSpecKind.INITIALIZE_METHOD, initializationMethod);
    }
    addMethod(MethodSpecKind.CONSTRUCTOR, constructor.build());
  }

  private void addCancellationListenerImplementation() {
    MethodSpec.Builder methodBuilder =
        methodBuilder(CANCELLATION_LISTENER_METHOD_NAME)
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(MAY_INTERRUPT_IF_RUNNING_PARAM);

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
        ImmutableList.copyOf(cancellations.values()).reverse();
    if (cancellationStatements.size() < STATEMENTS_PER_METHOD) {
      methodBuilder.addCode(CodeBlocks.concat(cancellationStatements)).build();
    } else {
      ImmutableList<MethodSpec> cancelProducersMethods =
          createPartitionedMethods(
              "cancelProducers",
              ImmutableList.of(MAY_INTERRUPT_IF_RUNNING_PARAM),
              cancellationStatements,
              methodName -> methodBuilder(methodName).addModifiers(PRIVATE));
      for (MethodSpec cancelProducersMethod : cancelProducersMethods) {
        methodBuilder.addStatement("$N($N)", cancelProducersMethod, MAY_INTERRUPT_IF_RUNNING_PARAM);
        addMethod(MethodSpecKind.CANCELLATION_LISTENER_METHOD, cancelProducersMethod);
      }
    }
    cancelParentStatement().ifPresent(methodBuilder::addCode);
    addMethod(MethodSpecKind.CANCELLATION_LISTENER_METHOD, methodBuilder.build());
  }

  private Optional<CodeBlock> cancelParentStatement() {
    if (!shouldPropagateCancellationToParent()) {
      return Optional.empty();
    }
    return Optional.of(
        CodeBlock.builder()
            .addStatement(
                "$T.this.$N($N)",
                parent.get().name(),
                CANCELLATION_LISTENER_METHOD_NAME,
                MAY_INTERRUPT_IF_RUNNING_PARAM)
            .build());
  }

  private boolean shouldPropagateCancellationToParent() {
    return parent.isPresent()
        && parent
            .get()
            .componentDescriptor()
            .cancellationPolicy()
            .map(policy -> policy.fromSubcomponents().equals(PROPAGATE))
            .orElse(false);
  }

  /**
   * Creates one or more methods, all taking the given {@code parameters}, which partition the given
   * list of {@code statements} among themselves such that no method has more than {@code
   * STATEMENTS_PER_METHOD} statements in it and such that the returned methods, if called in order,
   * will execute the {@code statements} in the given order.
   */
  private ImmutableList<MethodSpec> createPartitionedMethods(
      String methodName,
      Iterable<ParameterSpec> parameters,
      List<CodeBlock> statements,
      Function<String, MethodSpec.Builder> methodBuilderCreator) {
    return Lists.partition(statements, STATEMENTS_PER_METHOD).stream()
        .map(
            partition ->
                methodBuilderCreator
                    .apply(getUniqueMethodName(methodName))
                    .addModifiers(PRIVATE)
                    .addParameters(parameters)
                    .addCode(CodeBlocks.concat(partition))
                    .build())
        .collect(toImmutableList());
  }

  private ImmutableList<ParameterSpec> constructorParameters() {
    Map<ComponentRequirement, ParameterSpec> map = new LinkedHashMap<>();
    if (graph.componentDescriptor().hasCreator()) {
      map = Maps.toMap(graph.componentRequirements(), ComponentRequirement::toParameterSpec);
    } else if (graph.factoryMethod().isPresent()) {
      map = Maps.transformValues(graph.factoryMethodParameters(), ParameterSpec::get);
    } else {
      throw new AssertionError(
          "Expected either a component creator or factory method but found neither.");
    }

    // Renames the given parameters to guarantee their names do not conflict with fields in the
    // component to ensure that a parameter is never referenced where a reference to a field was
    // intended.
    return ImmutableList.copyOf(Maps.transformEntries(map, this::renameParameter).values());
  }

  private ParameterSpec renameParameter(ComponentRequirement requirement, ParameterSpec parameter) {
    return ParameterSpec.builder(parameter.type, getParameterName(requirement, parameter.name))
        .addAnnotations(parameter.annotations)
        .addModifiers(parameter.modifiers)
        .build();
  }

  private static ImmutableList<ParameterSpec> makeFinal(List<ParameterSpec> parameters) {
    return parameters.stream()
        .map(param -> param.toBuilder().addModifiers(FINAL).build())
        .collect(toImmutableList());
  }
}
