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
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
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
import dagger.internal.codegen.javapoet.TypeSpecs;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
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

  private ComponentImplementation currentShard = this;
  private final Map<Key, ComponentImplementation> shardsByKey = new HashMap<>();
  private final Optional<ComponentImplementation> shardOwner;
  private final BindingGraph graph;
  private final ClassName name;
  private final TypeSpec.Builder component;
  private final SubcomponentNames subcomponentNames;
  private final CompilerOptions compilerOptions;
  private final CodeBlock externalReferenceBlock;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final List<CodeBlock> initializations = new ArrayList<>();
  private final List<CodeBlock> componentRequirementInitializations = new ArrayList<>();
  private final Map<ComponentRequirement, String> componentRequirementParameterNames =
      new HashMap<>();
  private final Set<Key> cancellableProducerKeys = new LinkedHashSet<>();
  private final ListMultimap<FieldSpecKind, FieldSpec> fieldSpecsMap =
      MultimapBuilder.enumKeys(FieldSpecKind.class).arrayListValues().build();
  private final ListMultimap<MethodSpecKind, MethodSpec> methodSpecsMap =
      MultimapBuilder.enumKeys(MethodSpecKind.class).arrayListValues().build();
  private final ListMultimap<TypeSpecKind, TypeSpec> typeSpecsMap =
      MultimapBuilder.enumKeys(TypeSpecKind.class).arrayListValues().build();
  private final List<Supplier<TypeSpec>> typeSuppliers = new ArrayList<>();

  private ComponentImplementation(
      BindingGraph graph,
      ClassName name,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions) {
    this.graph = graph;
    this.name = name;
    this.component = classBuilder(name);
    this.subcomponentNames = subcomponentNames;
    this.shardOwner = Optional.empty();
    this.externalReferenceBlock = CodeBlock.of("$T.this", name);
    this.compilerOptions = compilerOptions;
  }

  private ComponentImplementation(ComponentImplementation shardOwner, ClassName shardName) {
    this.graph = shardOwner.graph;
    this.name = shardName;
    this.component = classBuilder(shardName);
    this.subcomponentNames = shardOwner.subcomponentNames;
    this.compilerOptions = shardOwner.compilerOptions;
    this.shardOwner = Optional.of(shardOwner);
    String fieldName = UPPER_CAMEL.to(LOWER_CAMEL, name.simpleName());
    String uniqueFieldName = shardOwner.getUniqueFieldName(fieldName);
    this.externalReferenceBlock = CodeBlock.of("$T.this.$N", shardOwner.name, uniqueFieldName);
    shardOwner.addTypeSupplier(() -> generate().build());
    shardOwner.addField(
        FieldSpecKind.COMPONENT_SHARD,
        FieldSpec.builder(name, uniqueFieldName, PRIVATE, FINAL)
            .initializer("new $T()", name)
            .build());
  }

  /** Returns a component implementation for a top-level component. */
  public static ComponentImplementation topLevelComponentImplementation(
      BindingGraph graph,
      ClassName name,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions) {
    return new ComponentImplementation(graph, name, subcomponentNames, compilerOptions);
  }

  /** Returns a component implementation that is a child of the current implementation. */
  public ComponentImplementation childComponentImplementation(BindingGraph graph) {
    checkState(!shardOwner.isPresent(), "Shards cannot create child components.");
    ClassName childName = getSubcomponentName(graph.componentDescriptor());
    return new ComponentImplementation(graph, childName, subcomponentNames, compilerOptions);
  }

  /** Returns a component implementation that is a shard of the current implementation. */
  public ComponentImplementation shardImplementation(Key key) {
    checkState(!shardOwner.isPresent(), "Shards cannot create other shards.");
    if (!shardsByKey.containsKey(key)) {
      int keysPerShard = compilerOptions.keysPerComponentShard(graph.componentTypeElement());
      if (!shardsByKey.isEmpty() && shardsByKey.size() % keysPerShard == 0) {
        ClassName shardName = name.nestedClass("Shard" + shardsByKey.size() / keysPerShard);
        currentShard = new ComponentImplementation(this, shardName);
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

  /** Adds the given super type to the component. */
  public void addSupertype(TypeElement supertype) {
    TypeSpecs.addSupertype(component, supertype);
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

  /** Adds the given annotation to the component. */
  public void addAnnotation(AnnotationSpec annotation) {
    component.addAnnotation(annotation);
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
   * Marks the given key of a producer as one that should have a cancellation statement in the
   * cancellation listener method of the component.
   */
  void addCancellableProducerKey(Key key) {
    cancellableProducerKeys.add(key);
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

  /** Returns the list of {@link CodeBlock}s that need to go in the initialize method. */
  public ImmutableList<CodeBlock> getInitializations() {
    return ImmutableList.copyOf(initializations);
  }

  /**
   * Returns a list of {@link CodeBlock}s for initializing {@link ComponentRequirement}s.
   *
   * <p>These initializations are kept separate from {@link #getInitializations()} because they must
   * be executed before the initializations of any framework instance initializations in a
   * superclass implementation that may depend on the instances. We cannot use the same strategy
   * that we use for framework instances (i.e. wrap in a {@link dagger.internal.DelegateFactory} or
   * {@link dagger.producers.internal.DelegateProducer} since the types of these initialized fields
   * have no interface type that we can write a proxy for.
   */
  // TODO(cgdecker): can these be inlined with getInitializations() now that we've turned down
  // ahead-of-time subcomponents?
  public ImmutableList<CodeBlock> getComponentRequirementInitializations() {
    return ImmutableList.copyOf(componentRequirementInitializations);
  }

  /**
   * Returns the list of producer {@link Key}s that need cancellation statements in the cancellation
   * listener method.
   */
  public ImmutableList<Key> getCancellableProducerKeys() {
    return ImmutableList.copyOf(cancellableProducerKeys);
  }

  /** Generates the component and returns the resulting {@link TypeSpec.Builder}. */
  public TypeSpec.Builder generate() {
    modifiers().forEach(component::addModifiers);
    fieldSpecsMap.asMap().values().forEach(component::addFields);
    methodSpecsMap.asMap().values().forEach(component::addMethods);
    typeSpecsMap.asMap().values().forEach(component::addTypes);
    typeSuppliers.stream().map(Supplier::get).forEach(component::addType);
    return component;
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
}
