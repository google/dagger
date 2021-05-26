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
import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.producers.CancellationPolicy.Propagation.PROPAGATE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** The implementation of a component type. */
@PerComponentImplementation
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

    /** A field for the cached provider of a {@link PrivateMethodBindingExpression}. */
    PRIVATE_METHOD_CACHED_PROVIDER_FIELD,

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
    CANCELLATION_LISTENER_METHOD
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

  /** The boolean parameter of the onProducerFutureCancelled method. */
  public static final ParameterSpec MAY_INTERRUPT_IF_RUNNING_PARAM =
      ParameterSpec.builder(boolean.class, "mayInterruptIfRunning").build();

  private static final String CANCELLATION_LISTENER_METHOD_NAME = "onProducerFutureCancelled";

  /**
   * How many statements per {@code initialize()} or {@code onProducerFutureCancelled()} method
   * before they get partitioned.
   */
  private static final int STATEMENTS_PER_METHOD = 100;

  private ShardImplementation currentShard;
  private final ShardImplementation componentShard;
  private final Map<Key, ShardImplementation> shardsByKey = new HashMap<>();
  private final Map<ShardImplementation, FieldSpec> shardFieldsByImplementation = new HashMap<>();
  private final Optional<ComponentImplementation> parent;
  private final BindingGraph graph;
  private final ComponentNames componentNames;
  private final CompilerOptions compilerOptions;
  private final DaggerElements elements;
  private final ImmutableMap<ComponentImplementation, FieldSpec> componentFieldsByImplementation;

  @Inject
  ComponentImplementation(
      @ParentComponent Optional<ComponentImplementation> parent,
      BindingGraph graph,
      ComponentNames componentNames,
      CompilerOptions compilerOptions,
      DaggerElements elements) {
    this.parent = parent;
    this.graph = graph;
    this.componentNames = componentNames;
    this.compilerOptions = compilerOptions;
    this.elements = elements;

    // The first group of keys belong to the component itself. We call this the componentShard.
    this.componentShard = new ShardImplementation(getComponentName(graph, parent, componentNames));

    // Create and claim the fields for this and all ancestor components stored as fields.
    this.componentFieldsByImplementation =
        createComponentFieldsByImplementation(this, compilerOptions);
  }

  /**
   * Returns the shard for a given {@link Key}.
   *
   * <p>Each set of {@link CompilerOptions#keysPerShard()} will get its own shard instance.
   */
  public ShardImplementation shardImplementation(Key key) {
    if (!shardsByKey.containsKey(key)) {
      int keysPerShard = compilerOptions.keysPerComponentShard(graph.componentTypeElement());
      if (shardsByKey.isEmpty()) {
        currentShard = componentShard;
      } else if (shardsByKey.size() % keysPerShard == 0) {
        ClassName shardName = name().nestedClass("Shard" + shardsByKey.size() / keysPerShard);
        currentShard = createShard(shardName);
      }
      shardsByKey.put(key, currentShard);
    }
    return shardsByKey.get(key);
  }

  private ShardImplementation createShard(ClassName shardName) {
    ShardImplementation shard = new ShardImplementation(shardName);

    // Create a field for the shard in the owning component.
    String shardFieldName =
        componentShard.getUniqueFieldName(UPPER_CAMEL.to(LOWER_CAMEL, shardName.simpleName()));
    FieldSpec shardField =
        FieldSpec.builder(shardName, shardFieldName, PRIVATE, FINAL)
            .initializer("new $T()", shardName)
            .build();
    componentShard.addField(FieldSpecKind.COMPONENT_SHARD, shardField);
    componentShard.addTypeSupplier(shard::generate);
    shardFieldsByImplementation.put(shard, shardField);

    return shard;
  }

  /** Returns the root {@link ComponentImplementation}. */
  ComponentImplementation rootComponentImplementation() {
    return parent.map(ComponentImplementation::rootComponentImplementation).orElse(this);
  }

  /** Returns a reference to this implementation when called from a different class. */
  public CodeBlock componentFieldReference() {
    // TODO(bcorso): This currently relies on all requesting classes having a reference to the
    // component with the same name, which is kind of sketchy. Try to think of a better way that
    // can accomodate the component missing in some classes if it's not used.
    return CodeBlock.of("$N", componentFieldsByImplementation.get(this));
  }

  /** Returns the fields for all components in the component path. */
  public ImmutableList<FieldSpec> componentFields() {
    return ImmutableList.copyOf(componentFieldsByImplementation.values());
  }

  /** Returns the fields for all components in the component path except the current component. */
  public ImmutableList<FieldSpec> creatorComponentFields() {
    return componentFieldsByImplementation.entrySet().stream()
        .filter(entry -> !this.equals(entry.getKey()))
        .map(Map.Entry::getValue)
        .collect(toImmutableList());
  }

  /** Returns the fields for all components in the component path by component implementation. */
  public ImmutableMap<ComponentImplementation, FieldSpec> componentFieldsByImplementation() {
    return componentFieldsByImplementation;
  }

  private static ImmutableMap<ComponentImplementation, FieldSpec>
      createComponentFieldsByImplementation(
          ComponentImplementation componentImplementation, CompilerOptions compilerOptions) {
    checkArgument(
        componentImplementation.componentShard != null,
        "The component shard must be set before computing the component fields.");
    ImmutableList.Builder<ComponentImplementation> builder = ImmutableList.builder();
    for (ComponentImplementation curr = componentImplementation;
        curr != null;
        curr = curr.parent.orElse(null)) {
      builder.add(curr);
    }
    // For better readability when adding these fields/parameters to generated code, we collect the
    // component implementations in reverse order so that parents appear before children.
    return builder.build().reverse().stream()
        .collect(
            toImmutableMap(
                componentImpl -> componentImpl,
                componentImpl -> {
                  TypeElement component = componentImpl.graph.componentPath().currentComponent();
                  ClassName fieldType = componentImpl.name();
                  String fieldName =
                      componentImpl.isNested()
                          ? simpleVariableName(componentImpl.name())
                          : simpleVariableName(component);
                  FieldSpec.Builder field = FieldSpec.builder(fieldType, fieldName, PRIVATE, FINAL);
                  componentImplementation.componentShard.componentFieldNames.claim(fieldName);

                  return field.build();
                }));
  }

  /** Returns the shard representing the {@link ComponentImplementation} itself. */
  ShardImplementation getComponentShard() {
    return componentShard;
  }

  // TODO(bcorso): Once everything is migrated to ShardImplementation, we should remove most of the
  // methods in ComponentImplementation. Currently, we just delegate to componentShard to be
  // compatible with the current behavior.
  /** Returns the binding graph for the component being generated. */
  public BindingGraph graph() {
    return componentShard.graph();
  }

  /** Returns the descriptor for the component being generated. */
  public ComponentDescriptor componentDescriptor() {
    return componentShard.componentDescriptor();
  }

  /** Returns the name of the component. */
  public ClassName name() {
    return componentShard.name();
  }

  /** Returns whether or not the implementation is nested within another class. */
  public boolean isNested() {
    return name().enclosingClassName() != null;
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
        ? name().peerClass(componentNames.getCreatorName(componentDescriptor()))
        : name().nestedClass(creatorKind().typeName());
  }

  /** Returns the name of the component implementation class for a component/subcomponent. */
  private static ClassName getComponentName(
      BindingGraph graph, Optional<ComponentImplementation> parent, ComponentNames componentNames) {
    if (!parent.isPresent()) {
      return ComponentNames.getRootComponentClassName(graph.componentDescriptor());
    }
    ComponentDescriptor parentDescriptor = parent.get().graph.componentDescriptor();
    ComponentDescriptor childDescriptor = graph.componentDescriptor();
    checkArgument(
        parentDescriptor.childComponents().contains(childDescriptor),
        "%s is not a child component of %s",
        childDescriptor.typeElement(),
        parentDescriptor.typeElement());

    // TODO(erichang): Hacky fix to shorten the suffix if we're too deeply
    // nested to save on file name length. 2 chosen arbitrarily.
    String suffix = parent.get().name().simpleNames().size() > 2 ? "I" : "Impl";
    return parent.get().name().nestedClass(componentNames.get(childDescriptor) + suffix);
  }

  /**
   * Returns the simple name of the creator implementation class for the given subcomponent creator
   * {@link Key}.
   */
  String getSubcomponentCreatorSimpleName(Key key) {
    return componentShard.getSubcomponentCreatorSimpleName(key);
  }

  /** Returns {@code true} if {@code type} is accessible from the generated component. */
  boolean isTypeAccessible(TypeMirror type) {
    return componentShard.isTypeAccessible(type);
  }

  // TODO(dpb): Consider taking FieldSpec, and returning identical FieldSpec with unique name?
  /** Adds the given field to the component. */
  public void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec) {
    componentShard.addField(fieldKind, fieldSpec);
  }

  // TODO(dpb): Consider taking MethodSpec, and returning identical MethodSpec with unique name?
  /** Adds the given method to the component. */
  public void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec) {
    componentShard.addMethod(methodKind, methodSpec);
  }

  /** Adds the given type to the component. */
  public void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    componentShard.addType(typeKind, typeSpec);
  }

  /** Adds a {@link Supplier} for the SwitchingProvider for the component. */
  void addTypeSupplier(Supplier<TypeSpec> typeSpecSupplier) {
    componentShard.addTypeSupplier(typeSpecSupplier);
  }

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock) {
    componentShard.addInitialization(codeBlock);
  }

  /** Adds the given code block that initializes a {@link ComponentRequirement}. */
  void addComponentRequirementInitialization(CodeBlock codeBlock) {
    componentShard.addComponentRequirementInitialization(codeBlock);
  }

  /** Adds the given cancellation statement to the cancellation listener method of the component. */
  void addCancellation(Key key, CodeBlock codeBlock) {
    componentShard.addCancellation(key, codeBlock);
  }

  /** Returns a new, unique field name for the component based on the given name. */
  String getUniqueFieldName(String name) {
    return componentShard.getUniqueFieldName(name);
  }

  /** Returns a new, unique method name for the component based on the given name. */
  public String getUniqueMethodName(String name) {
    return componentShard.getUniqueMethodName(name);
  }

  /** Returns a new, unique method name for a getter method for the given request. */
  String getUniqueMethodName(BindingRequest request) {
    return componentShard.getUniqueMethodName(request);
  }

  /**
   * Gets the parameter name to use for the given requirement for this component, starting with the
   * given base name if no parameter name has already been selected for the requirement.
   */
  public String getParameterName(ComponentRequirement requirement, String baseName) {
    return componentShard.getParameterName(requirement, baseName);
  }

  /** Claims a new method name for the component. Does nothing if method name already exists. */
  public void claimMethodName(CharSequence name) {
    componentShard.claimMethodName(name);
  }

  /** Generates the component and returns the resulting {@link TypeSpec}. */
  public TypeSpec generate() {
    return componentShard.generate();
  }

  /**
   * The implementation of a shard.
   *
   * <p>The purpose of a shard is to allow a component implemenation to be split into multiple
   * classes, where each class owns the creation logic for a set of keys. Sharding is useful for
   * large component implementations, where a single component implementation class can reach size
   * limitations, such as the constant pool size.
   *
   * <p>When generating the actual sources, the creation logic within the first instance of {@link
   * ShardImplementation} will go into the component implementation class itself (e.g. {@code
   * MySubcomponentImpl}). Each subsequent instance of {@link ShardImplementation} will generate a
   * nested "shard" class within the component implementation (e.g. {@code
   * MySubcomponentImpl.Shard1}, {@code MySubcomponentImpl.Shard2}, etc).
   */
  public final class ShardImplementation {
    private final ClassName name;
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

    private ShardImplementation(ClassName name) {
      this.name = name;
      if (graph.componentDescriptor().isProduction()) {
        claimMethodName(CANCELLATION_LISTENER_METHOD_NAME);
      }
    }

    /** Returns the {@link ComponentImplementation} that owns this shard. */
    public ComponentImplementation getComponentImplementation() {
      return ComponentImplementation.this;
    }

    /**
     * Returns {@code true} if this shard represents the component implementation rather than a
     * separate {@code Shard} class.
     */
    public boolean isComponentShard() {
      return this == componentShard;
    }

    /** Returns a reference to this implementation when called from a different class. */
    public CodeBlock fieldReference() {
      // TODO(bcorso): This currently relies on all requesting classes having a reference to the
      // component with the same name, which is kind of sketchy. Try to think of a better way that
      // can accomodate the component missing in some classes if it's not used.
      return isComponentShard()
          ? componentFieldReference()
          : CodeBlock.of("$L.$N", componentFieldReference(), shardFieldsByImplementation.get(this));
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

    /**
     * Returns the simple name of the creator implementation class for the given subcomponent
     * creator {@link Key}.
     */
    String getSubcomponentCreatorSimpleName(Key key) {
      return componentNames.getCreatorName(key);
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

    /**
     * Adds the given cancellation statement to the cancellation listener method of the component.
     */
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
      String baseMethodName =
          bindingName
              + (request.isRequestKind(RequestKind.INSTANCE)
                  ? ""
                  : UPPER_UNDERSCORE.to(UPPER_CAMEL, request.kindName()));
      return getUniqueMethodName(baseMethodName);
    }

    /**
     * Gets the parameter name to use for the given requirement for this component, starting with
     * the given base name if no parameter name has already been selected for the requirement.
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
    public TypeSpec generate() {
      TypeSpec.Builder builder = classBuilder(name);

      if (isComponentShard()) {
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
      return builder.build();
    }

    private ImmutableSet<Modifier> modifiers() {
      if (!isComponentShard()) {
        // TODO(bcorso): Consider making shards static and unnested too?
        return ImmutableSet.of(PRIVATE, FINAL);
      } else if (isNested()) {
        return ImmutableSet.of(PRIVATE, STATIC, FINAL);
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

      // Add a constructor parameter and initialization for each component field. We initialize
      // these fields immediately so that we don't need to be pass them to each initialize method.
      componentFieldsByImplementation()
          .forEach(
              (componentImplementation, field) -> {
                if (componentImplementation.equals(ComponentImplementation.this)) {
                  // For the self-referenced component field, just initialize it in the initializer.
                  addField(
                      FieldSpecKind.COMPONENT_REQUIREMENT_FIELD,
                      field.toBuilder().initializer("this").build());
                } else {
                  addField(FieldSpecKind.COMPONENT_REQUIREMENT_FIELD, field);
                  constructor.addStatement("this.$1N = $1N", field);
                  constructor.addParameter(field.type, field.name);
                }
              });

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
          methodBuilder.addStatement(
              "$N($N)", cancelProducersMethod, MAY_INTERRUPT_IF_RUNNING_PARAM);
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
                  "$L.$N($N)",
                  parent.get().componentFieldReference(),
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
     * Creates one or more methods, all taking the given {@code parameters}, which partition the
     * given list of {@code statements} among themselves such that no method has more than {@code
     * STATEMENTS_PER_METHOD} statements in it and such that the returned methods, if called in
     * order, will execute the {@code statements} in the given order.
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

    private ParameterSpec renameParameter(
        ComponentRequirement requirement, ParameterSpec parameter) {
      return ParameterSpec.builder(parameter.type, getParameterName(requirement, parameter.name))
          .addAnnotations(parameter.annotations)
          .addModifiers(parameter.modifiers)
          .build();
    }
  }

  private static ImmutableList<ParameterSpec> makeFinal(List<ParameterSpec> parameters) {
    return parameters.stream()
        .map(param -> param.toBuilder().addModifiers(FINAL).build())
        .collect(toImmutableList());
  }
}
