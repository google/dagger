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

package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
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
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** The implementation of a component type. */
final class ComponentImplementation {
  /** A type of field that this component can contain. */
  enum FieldSpecKind {

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
  // TODO(user, dpb): Change the oder to constructor, initialize, component, then private
  // (including MIM and AOM—why treat those separately?).
  enum MethodSpecKind {
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
  enum TypeSpecKind {
    /** A factory class for a present optional binding. */
    PRESENT_FACTORY,

    /** A class for the component creator (only used by the root component.) */
    COMPONENT_CREATOR,

    /** A provider class for a component provision. */
    COMPONENT_PROVISION_FACTORY,

    /** A class for the subcomponent or subcomponent builder. */
    SUBCOMPONENT
  }

  private final CompilerOptions compilerOptions;
  private final ComponentDescriptor componentDescriptor;
  private final BindingGraph graph;
  private final ClassName name;
  private final NestingKind nestingKind;
  private Optional<ComponentCreatorImplementation> creatorImplementation;
  private final Map<TypeElement, ComponentImplementation> childImplementations = new HashMap<>();
  private final TypeSpec.Builder component;
  private final SubcomponentNames subcomponentNames;
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
  private final List<Supplier<TypeSpec>> switchingProviderSupplier = new ArrayList<>();

  private ComponentImplementation(
      ComponentDescriptor componentDescriptor,
      BindingGraph graph,
      ClassName name,
      NestingKind nestingKind,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions,
      ImmutableSet<Modifier> modifiers) {
    checkName(name, nestingKind);
    this.compilerOptions = compilerOptions;
    this.componentDescriptor = componentDescriptor;
    this.graph = graph;
    this.name = name;
    this.nestingKind = nestingKind;
    this.component = classBuilder(name);
    modifiers.forEach(component::addModifiers);
    this.subcomponentNames = subcomponentNames;
  }

  /** Returns a component implementation for a top-level component. */
  static ComponentImplementation topLevelComponentImplementation(
      BindingGraph graph,
      ClassName name,
      SubcomponentNames subcomponentNames,
      CompilerOptions compilerOptions) {
    return new ComponentImplementation(
        graph.componentDescriptor(),
        graph,
        name,
        NestingKind.TOP_LEVEL,
        subcomponentNames,
        compilerOptions,
        topLevelComponentImplementationModifiers(graph));
  }

  private static ImmutableSet<Modifier> topLevelComponentImplementationModifiers(
      BindingGraph graph) {
    ImmutableSet.Builder<Modifier> modifiers = ImmutableSet.builder();
    if (graph.componentTypeElement().getModifiers().contains(PUBLIC)
        || graph.componentDescriptor().isSubcomponent()) {
      // TODO(ronshapiro): perhaps all generated components should be non-public?
      modifiers.add(PUBLIC);
    }
    return modifiers.add(graph.componentDescriptor().isSubcomponent() ? ABSTRACT : FINAL).build();
  }

  /** Returns a component implementation that is a child of the current implementation. */
  ComponentImplementation childComponentImplementation(BindingGraph graph, Modifier... modifiers) {
    return new ComponentImplementation(
        graph.componentDescriptor(),
        graph,
        getSubcomponentName(graph.componentDescriptor()),
        NestingKind.MEMBER,
        subcomponentNames,
        compilerOptions,
        ImmutableSet.copyOf(modifiers));
  }

  // TODO(dpb): Just determine the nesting kind from the name.
  private static void checkName(ClassName name, NestingKind nestingKind) {
    switch (nestingKind) {
      case TOP_LEVEL:
        checkArgument(
            name.enclosingClassName() == null, "must be a top-level class name: %s", name);
        break;

      case MEMBER:
        checkNotNull(name.enclosingClassName(), "must not be a top-level class name: %s", name);
        break;

      default:
        throw new IllegalArgumentException(
            "nestingKind must be TOP_LEVEL or MEMBER: " + nestingKind);
    }
  }

  // TODO(ronshapiro): see if we can remove this method and instead inject it in the objects that
  // need it.
  /** Returns the binding graph for the component being generated. */
  BindingGraph graph() {
    return graph;
  }

  /** Returns the descriptor for the component being generated. */
  ComponentDescriptor componentDescriptor() {
    return componentDescriptor;
  }

  /** Returns the name of the component. */
  ClassName name() {
    return name;
  }

  /** Returns whether or not the implementation is nested within another class. */
  boolean isNested() {
    return nestingKind.isNested();
  }

  void setCreatorImplementation(Optional<ComponentCreatorImplementation> creatorImplementation) {
    checkState(
        this.creatorImplementation == null, "setCreatorImplementation has already been called");
    this.creatorImplementation = creatorImplementation;
  }

  Optional<ComponentCreatorImplementation> creatorImplementation() {
    checkState(creatorImplementation != null, "setCreatorImplementation has not been called yet");
    return creatorImplementation;
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
  ClassName getCreatorName() {
    return isNested()
        ? name.peerClass(subcomponentNames().getCreatorName(componentDescriptor()))
        : name.nestedClass(creatorKind().typeName());
  }

  /** Returns the name of the nested implementation class for a child component. */
  ClassName getSubcomponentName(ComponentDescriptor childDescriptor) {
    checkArgument(
        componentDescriptor().childComponents().contains(childDescriptor),
        "%s is not a child component of %s",
        childDescriptor.typeElement(),
        componentDescriptor().typeElement());
    return name.nestedClass(subcomponentNames().get(childDescriptor) + "Impl");
  }

  /**
   * Returns the simple name of the creator implementation class for the given subcomponent creator
   * {@link Key}.
   */
  String getSubcomponentCreatorSimpleName(Key key) {
    return subcomponentNames().getCreatorName(key);
  }

  private SubcomponentNames subcomponentNames() {
    return subcomponentNames;
  }

  /** Returns {@code true} if {@code type} is accessible from the generated component. */
  boolean isTypeAccessible(TypeMirror type) {
    return isTypeAccessibleFrom(type, name.packageName());
  }

  /** Adds the given super type to the component. */
  void addSupertype(TypeElement supertype) {
    TypeSpecs.addSupertype(component, supertype);
  }

  // TODO(dpb): Consider taking FieldSpec, and returning identical FieldSpec with unique name?
  /** Adds the given field to the component. */
  void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec) {
    fieldSpecsMap.put(fieldKind, fieldSpec);
  }

  /** Adds the given fields to the component. */
  void addFields(FieldSpecKind fieldKind, Iterable<FieldSpec> fieldSpecs) {
    fieldSpecsMap.putAll(fieldKind, fieldSpecs);
  }

  // TODO(dpb): Consider taking MethodSpec, and returning identical MethodSpec with unique name?
  /** Adds the given method to the component. */
  void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec) {
    methodSpecsMap.put(methodKind, methodSpec);
  }

  /** Adds the given annotation to the component. */
  void addAnnotation(AnnotationSpec annotation) {
    component.addAnnotation(annotation);
  }

  /** Adds the given type to the component. */
  void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    typeSpecsMap.put(typeKind, typeSpec);
  }

  /** Adds the type generated from the given child implementation. */
  void addChild(ComponentDescriptor child, ComponentImplementation childImplementation) {
    childImplementations.put(child.typeElement(), childImplementation);
    addType(TypeSpecKind.SUBCOMPONENT, childImplementation.generate().build());
  }

  /** Adds a {@link Supplier} for the SwitchingProvider for the component. */
  void addSwitchingProvider(Supplier<TypeSpec> typeSpecSupplier) {
    switchingProviderSupplier.add(typeSpecSupplier);
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
  String getUniqueMethodName(String name) {
    return componentMethodNames.getUniqueName(name);
  }

  /** Returns a new, unique method name for a getter method for the given request. */
  String getUniqueMethodName(BindingRequest request) {
    return uniqueMethodName(request, KeyVariableNamer.name(request.key()));
  }

  private String uniqueMethodName(BindingRequest request, String bindingName) {
    String baseMethodName =
        "get"
            + LOWER_CAMEL.to(UPPER_CAMEL, bindingName)
            + (request.isRequestKind(RequestKind.INSTANCE)
                ? ""
                : UPPER_UNDERSCORE.to(UPPER_CAMEL, request.kindName()));
    return getUniqueMethodName(baseMethodName);
  }

  /**
   * Gets the parameter name to use for the given requirement for this component, starting with the
   * given base name if no parameter name has already been selected for the requirement.
   */
  String getParameterName(ComponentRequirement requirement, String baseName) {
    return componentRequirementParameterNames.computeIfAbsent(
        requirement, r -> getUniqueFieldName(baseName));
  }

  /** Claims a new method name for the component. Does nothing if method name already exists. */
  void claimMethodName(CharSequence name) {
    componentMethodNames.claim(name);
  }

  /** Returns the list of {@link CodeBlock}s that need to go in the initialize method. */
  ImmutableList<CodeBlock> getInitializations() {
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
  ImmutableList<CodeBlock> getComponentRequirementInitializations() {
    return ImmutableList.copyOf(componentRequirementInitializations);
  }

  /**
   * Returns the list of producer {@link Key}s that need cancellation statements in the cancellation
   * listener method.
   */
  ImmutableList<Key> getCancellableProducerKeys() {
    return ImmutableList.copyOf(cancellableProducerKeys);
  }

  /** Generates the component and returns the resulting {@link TypeSpec.Builder}. */
  TypeSpec.Builder generate() {
    fieldSpecsMap.asMap().values().forEach(component::addFields);
    methodSpecsMap.asMap().values().forEach(component::addMethods);
    typeSpecsMap.asMap().values().forEach(component::addTypes);
    switchingProviderSupplier.stream().map(Supplier::get).forEach(component::addType);
    return component;
  }
}
