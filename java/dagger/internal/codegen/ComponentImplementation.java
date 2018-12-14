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
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
  // TODO(user, dpb): Move component requirements and reference managers to top? The order should
  // be component requirements, reference managers, framework fields, private method fields, ... etc
  enum FieldSpecKind {

    /**
     * A field for the lock and cached value for {@linkplain PrivateMethodBindingExpression
     * private-method scoped bindings}.
     */
    PRIVATE_METHOD_SCOPED_FIELD,

    /** A field required by the component, e.g. module instances. */
    COMPONENT_REQUIREMENT_FIELD,

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

    /**
     * In ahead-of-time subcomponents, this method coordinates the invocation of {@link
     * #INITIALIZE_METHOD initialization methods} instead of constructors.
     */
    // TODO(b/117833324): try to merge this with other initialize() methods so it looks more natural
    CONFIGURE_INITIALIZATION_METHOD,

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
     * A method that encapsulates a modifiable binding. A binding is modifiable if it can change
     * across implementations of a subcomponent. This is only relevant for ahead-of-time
     * subcomponents.
     */
    MODIFIABLE_BINDING_METHOD,

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

  private final ComponentDescriptor componentDescriptor;
  private final ClassName name;
  private final NestingKind nestingKind;
  private final boolean isAbstract;
  private final Optional<ComponentImplementation> superclassImplementation;
  private Optional<ComponentCreatorImplementation> creatorImplementation;
  private final Map<TypeElement, ComponentImplementation> childImplementations = new HashMap<>();
  private final TypeSpec.Builder component;
  private final SubcomponentNames subcomponentNames;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final List<CodeBlock> initializations = new ArrayList<>();
  private final List<CodeBlock> componentRequirementInitializations = new ArrayList<>();
  private final Set<Key> cancellableProducerKeys = new LinkedHashSet<>();
  private final ListMultimap<FieldSpecKind, FieldSpec> fieldSpecsMap =
      MultimapBuilder.enumKeys(FieldSpecKind.class).arrayListValues().build();
  private final ListMultimap<MethodSpecKind, MethodSpec> methodSpecsMap =
      MultimapBuilder.enumKeys(MethodSpecKind.class).arrayListValues().build();
  private final ListMultimap<TypeSpecKind, TypeSpec> typeSpecsMap =
      MultimapBuilder.enumKeys(TypeSpecKind.class).arrayListValues().build();
  private final List<Supplier<TypeSpec>> switchingProviderSupplier = new ArrayList<>();
  private final ModifiableBindingMethods modifiableBindingMethods = new ModifiableBindingMethods();
  // TODO(b/117833324): can this just be a Set instead of a SetMultimap? The values should be
  // implicit
  private final SetMultimap<BindingRequest, DependencyRequest> multibindingContributionsMade =
      HashMultimap.create();
  private Optional<MethodSpec> configureInitializationMethod = Optional.empty();
  private final Map<ComponentRequirement, String> modifiableModuleMethods = new LinkedHashMap<>();

  ComponentImplementation(
      ComponentDescriptor componentDescriptor,
      ClassName name,
      NestingKind nestingKind,
      Optional<ComponentImplementation> superclassImplementation,
      SubcomponentNames subcomponentNames,
      Modifier... modifiers) {
    checkName(name, nestingKind);
    this.componentDescriptor = componentDescriptor;
    this.name = name;
    this.nestingKind = nestingKind;
    this.isAbstract = Arrays.asList(modifiers).contains(ABSTRACT);
    this.superclassImplementation = superclassImplementation;
    this.component = classBuilder(name).addModifiers(modifiers);
    this.subcomponentNames = subcomponentNames;
  }

  ComponentImplementation(
      ComponentImplementation parent,
      ComponentDescriptor componentDescriptor,
      Optional<ComponentImplementation> superclassImplementation,
      Modifier... modifiers) {
    this(
        componentDescriptor,
        parent.getSubcomponentName(componentDescriptor),
        NestingKind.MEMBER,
        superclassImplementation,
        parent.subcomponentNames,
        modifiers);
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

  /** Returns whether or not the implementation is abstract. */
  boolean isAbstract() {
    return isAbstract;
  }

  /** Returns the superclass implementation. */
  Optional<ComponentImplementation> superclassImplementation() {
    return superclassImplementation;
  }

  /**
   * Returns the base implementation of this component in ahead-of-time subcomponents mode. If this
   * is the base implementation, this returns {@link Optional#empty()}.
   */
  Optional<ComponentImplementation> baseImplementation() {
    return superclassImplementation.isPresent()
        ? Optional.of(Optionals.rootmostValue(this, c -> c.superclassImplementation))
        : Optional.empty();
  }

  /**
   * Returns the {@link #configureInitializationMethod()} of the nearest supertype that defines one,
   * if any.
   *
   * <p>Only returns a present value in {@link CompilerOptions#aheadOfTimeSubcomponents()}.
   */
  Optional<MethodSpec> superConfigureInitializationMethod() {
    for (Optional<ComponentImplementation> currentSuper = superclassImplementation;
        currentSuper.isPresent();
        currentSuper = currentSuper.get().superclassImplementation) {
      if (currentSuper.get().configureInitializationMethod.isPresent()) {
        return currentSuper.get().configureInitializationMethod;
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the {@link MethodSpecKind#CONFIGURE_INITIALIZATION_METHOD} of this implementation if
   * there is one.
   *
   * <p>Only returns a present value in {@link CompilerOptions#aheadOfTimeSubcomponents()}.
   */
  Optional<MethodSpec> configureInitializationMethod() {
    return configureInitializationMethod;
  }

  /**
   * Set's this component implementation's {@code configureInitialization()} method and {@linkplain
   * #addMethod(MethodSpecKind, MethodSpec) adds the method}.
   */
  void setConfigureInitializationMethod(MethodSpec method) {
    configureInitializationMethod = Optional.of(method);
    addMethod(MethodSpecKind.CONFIGURE_INITIALIZATION_METHOD, method);
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
   * Returns the name of the creator class for this component. It will be a sibling of this
   * generated class unless this is a top-level component, in which case it will be nested.
   */
  ClassName getCreatorName() {
    return isNested()
        ? name.peerClass(subcomponentNames.get(componentDescriptor) + "Builder")
        : name.nestedClass("Builder");
  }

  /** Returns the name of the nested implementation class for a child component. */
  ClassName getSubcomponentName(ComponentDescriptor childDescriptor) {
    checkArgument(
        componentDescriptor.childComponents().contains(childDescriptor),
        "%s is not a child component of %s",
        childDescriptor.typeElement(),
        componentDescriptor.typeElement());
    return name.nestedClass(subcomponentNames.get(childDescriptor) + "Impl");
  }

  /** Returns the simple subcomponent name for the given subcomponent builder {@link Key}. */
  String getSubcomponentName(Key key) {
    return subcomponentNames.get(key);
  }

  /** Returns the child implementation. */
  Optional<ComponentImplementation> childImplementation(ComponentDescriptor child) {
    return Optional.ofNullable(childImplementations.get(child.typeElement()));
  }

  /** Returns {@code true} if {@code type} is accessible from the generated component. */
  boolean isTypeAccessible(TypeMirror type) {
    return isTypeAccessibleFrom(type, name.packageName());
  }

  /** Adds the given super type to the component. */
  void addSupertype(TypeElement supertype) {
    TypeSpecs.addSupertype(component, supertype);
  }

  /** Adds the given super class to the subcomponent. */
  void addSuperclass(ClassName className) {
    checkState(
        superclassImplementation.isPresent(),
        "Setting the superclass for component [%s] when there is no superclass implementation.",
        name);
    component.superclass(className);
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

  /** Adds the given methods to the component. */
  void addMethods(MethodSpecKind methodKind, Iterable<MethodSpec> methodSpecs) {
    methodSpecsMap.putAll(methodKind, methodSpecs);
  }

  /**
   * Adds the given method to the component. In this case, the method represents an encapsulation of
   * a modifiable binding between implementations of a subcomponent. This is only relevant for
   * ahead-of-time subcomponents.
   */
  void addModifiableBindingMethod(
      ModifiableBindingType type,
      BindingRequest request,
      TypeMirror returnType,
      MethodSpec methodSpec,
      boolean finalized) {
    modifiableBindingMethods.addMethod(type, request, returnType, methodSpec, finalized);
    methodSpecsMap.put(MethodSpecKind.MODIFIABLE_BINDING_METHOD, methodSpec);
  }

  /**
   * Registers a known method as encapsulating a modifiable binding without adding the method to the
   * current component. This is relevant when a method of a different type, such as a component
   * method, encapsulates a modifiable binding.
   */
  void registerModifiableBindingMethod(
      ModifiableBindingType type,
      BindingRequest request,
      TypeMirror returnType,
      MethodSpec methodSpec,
      boolean finalized) {
    modifiableBindingMethods.addMethod(type, request, returnType, methodSpec, finalized);
  }

  /** Adds the implementation for the given {@link ModifiableBindingMethod} to the component. */
  void addImplementedModifiableBindingMethod(ModifiableBindingMethod method) {
    modifiableBindingMethods.methodImplemented(method);
    methodSpecsMap.put(MethodSpecKind.MODIFIABLE_BINDING_METHOD, method.methodSpec());
  }

  /** Add's a modifiable module method to this implementation. */
  void addModifiableModuleMethod(ComponentRequirement module, MethodSpec method) {
    checkArgument(module.kind().isModule());
    checkState(modifiableModuleMethods.put(module, method.name) == null);
    methodSpecsMap.put(MethodSpecKind.MODIFIABLE_BINDING_METHOD, method);
  }

  /** Adds the given type to the component. */
  void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    typeSpecsMap.put(typeKind, typeSpec);
  }

  /** Adds the given types to the component. */
  void addTypes(TypeSpecKind typeKind, Iterable<TypeSpec> typeSpecs) {
    typeSpecsMap.putAll(typeKind, typeSpecs);
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

  /**
   * Adds the given code block that initializes a {@link ComponentRequirement} to the component
   * implementation.
   */
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

  /**
   * Returns a new, unique method name for a getter method exposing the given binding for the given
   * request.
   */
  String getUniqueMethodName(BindingRequest request, ContributionBinding binding) {
    // TODO(user): Use a better name for @MapKey binding instances.
    // TODO(user): Include the binding method as part of the method name.
    return uniqueMethodName(request, BindingVariableNamer.name(binding));
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

  /** Claims a new method name for the component. Does nothing if method name already exists. */
  void claimMethodName(CharSequence name) {
    componentMethodNames.claim(name);
  }

  /** Returns the list of {@link CodeBlock}s that need to go in the initialize method. */
  ImmutableList<CodeBlock> getInitializations() {
    return ImmutableList.copyOf(initializations);
  }

  /**
   * Returns the list of {@link CodeBlock}s that initialize {@link ComponentRequirement}s. These
   * initializations are kept separate from {@link #getInitializations()} because they must be
   * executed before the initializations of any framework instance initializations in a superclass
   * implementation that may depend on the instances. We cannot use the same strategy that we use
   * for framework instances (i.e. wrap in a {@link dagger.internal.DelegateFactory} or {@link
   * dagger.producers.internal.DelegateProducer} since the types of these initialized fields have no
   * interface type that we can write a proxy for.
   */
  ImmutableList<CodeBlock> getComponentRequirementInitializations() {
    return ImmutableList.copyOf(componentRequirementInitializations);
  }

  /**
   * Returns the list of producer {@link Key}s that need cancellation statements in the cancellation
   * listener method.
   */
  ImmutableList<Key> getCancellableProducerKeys() {
    Optional<ComponentImplementation> currentSuperImplementation = superclassImplementation;
    Set<Key> cancelledKeysFromSuperclass = new HashSet<>();
    while (currentSuperImplementation.isPresent()) {
      cancelledKeysFromSuperclass.addAll(
          currentSuperImplementation.get().cancellableProducerKeys);
      currentSuperImplementation = currentSuperImplementation.get().superclassImplementation;
    }
    return Sets.difference(cancellableProducerKeys, cancelledKeysFromSuperclass)
        .immutableCopy()
        .asList();
  }

  /**
   * Returns the {@link ModifiableBindingMethod}s for this subcomponent implementation and its
   * superclasses.
   */
  ImmutableList<ModifiableBindingMethod> getModifiableBindingMethods() {
    ImmutableList.Builder<ModifiableBindingMethod> modifiableBindingMethodsBuilder =
        ImmutableList.builder();
    if (superclassImplementation.isPresent()) {
      ImmutableList<ModifiableBindingMethod> superclassModifiableBindingMethods =
          superclassImplementation.get().getModifiableBindingMethods();
      superclassModifiableBindingMethods.stream()
          .filter(method -> !modifiableBindingMethods.finalized(method))
          .forEach(modifiableBindingMethodsBuilder::add);
    }
    modifiableBindingMethodsBuilder.addAll(modifiableBindingMethods.getNonFinalizedMethods());
    return modifiableBindingMethodsBuilder.build();
  }

  /**
   * Returns the names of every modifiable method of this implementation and any superclass
   * implementations.
   */
  ImmutableSet<String> getAllModifiableMethodNames() {
    ImmutableSet.Builder<String> names = ImmutableSet.builder();
    modifiableBindingMethods.allMethods().forEach(method -> names.add(method.methodSpec().name));
    names.addAll(modifiableModuleMethods.values());
    superclassImplementation.ifPresent(
        superclass -> names.addAll(superclass.getAllModifiableMethodNames()));
    return names.build();
  }

  /**
   * Returns the {@link ModifiableBindingMethod} for this subcomponent for the given binding, if it
   * exists.
   */
  Optional<ModifiableBindingMethod> getModifiableBindingMethod(BindingRequest request) {
    Optional<ModifiableBindingMethod> method = modifiableBindingMethods.getMethod(request);
    if (!method.isPresent() && superclassImplementation.isPresent()) {
      return superclassImplementation.get().getModifiableBindingMethod(request);
    }
    return method;
  }

  /**
   * Returns the {@link ModifiableBindingMethod} of a supertype for this method's {@code request},
   * if one exists.
   */
  Optional<ModifiableBindingMethod> supertypeModifiableBindingMethod(BindingRequest request) {
    return superclassImplementation()
        .flatMap(superImplementation -> superImplementation.getModifiableBindingMethod(request));
  }

  /**
   * Returns the names of modifiable module methods for this implementation and all inherited
   * implementations, keyed by the corresponding module's {@link ComponentRequirement}.
   */
  ImmutableMap<ComponentRequirement, String> getAllModifiableModuleMethods() {
    ImmutableMap.Builder<ComponentRequirement, String> methods = ImmutableMap.builder();
    methods.putAll(modifiableModuleMethods);
    superclassImplementation.ifPresent(
        superclass -> methods.putAll(superclass.getAllModifiableModuleMethods()));
    return methods.build();
  }

  /**
   * Returns the name of the modifiable module method for {@code module} that is inherited in this
   * implementation, or empty if none has been defined.
   */
  Optional<String> supertypeModifiableModuleMethodName(ComponentRequirement module) {
    checkArgument(module.kind().isModule());
    if (!superclassImplementation.isPresent()) {
      return Optional.empty();
    }
    String methodName = superclassImplementation.get().modifiableModuleMethods.get(module);
    if (methodName == null) {
      return superclassImplementation.get().supertypeModifiableModuleMethodName(module);
    }
    return Optional.of(methodName);
  }

  /** Generates the component and returns the resulting {@link TypeSpec.Builder}. */
  TypeSpec.Builder generate() {
    fieldSpecsMap.asMap().values().forEach(component::addFields);
    methodSpecsMap.asMap().values().forEach(component::addMethods);
    typeSpecsMap.asMap().values().forEach(component::addTypes);
    switchingProviderSupplier.stream().map(Supplier::get).forEach(component::addType);
    return component;
  }

  /**
   * Registers a {@ProvisionBinding} representing a multibinding as having been implemented in this
   * component. Multibindings are modifiable across subcomponent implementations and this allows us
   * to know whether a contribution has been made by a superclass implementation. This is only
   * relevant for ahead-of-time subcomponents.
   */
  void registerImplementedMultibinding(
      ContributionBinding multibinding, BindingRequest bindingRequest) {
    checkArgument(multibinding.isSyntheticMultibinding());
    // We register a multibinding as implemented each time we request the multibinding expression,
    // so only modify the set of contributions once.
    if (!multibindingContributionsMade.containsKey(bindingRequest)) {
      multibindingContributionsMade.putAll(bindingRequest, multibinding.dependencies());
    }
  }

  /**
   * Returns the set of multibinding contributions associated with all superclass implementations of
   * a multibinding.
   */
  ImmutableSet<DependencyRequest> superclassContributionsMade(BindingRequest bindingRequest) {
    return superclassImplementation
        .map(s -> s.getAllMultibindingContributions(bindingRequest))
        .orElse(ImmutableSet.of());
  }

  /**
   * Returns the set of multibinding contributions associated with all implementations of a
   * multibinding.
   */
  private ImmutableSet<DependencyRequest> getAllMultibindingContributions(
      BindingRequest bindingRequest) {
    return ImmutableSet.copyOf(
        Sets.union(
            multibindingContributionsMade.get(bindingRequest),
            superclassContributionsMade(bindingRequest)));
  }
}
