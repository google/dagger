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
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.ReferenceReleasingProviderManager;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.internal.CancellationListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** The model of the component being generated. */
final class GeneratedComponentModel {
  /** A type of field that this component model can generate. */
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

    /** A framework field for type T, e.g. Provider<T>. */
    FRAMEWORK_FIELD,

    /** A field for a {@link ReferenceReleasingProviderManager}. */
    REFERENCE_RELEASING_MANAGER_FIELD,

    /** A static field that always returns an absent {@code Optional} value for the binding. */
    ABSENT_OPTIONAL_FIELD
  }

  /** A type of method that this component model can generate. */
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
     * A method that encapsulates a modifiable binding. A binding is modifiable if it can change
     * across implementations of a subcomponent. This is only relevant for ahead-of-time
     * subcomponents.
     */
    MODIFIABLE_BINDING_METHOD,

    /**
     * The {@link CancellationListener#onProducerFutureCancelled(boolean)} method for a production
     * component.
     */
    CANCELLATION_LISTENER_METHOD,
    ;
  }

  /** A type of nested class that this component model can generate. */
  enum TypeSpecKind {
    /** A factory class for a present optional binding. */
    PRESENT_FACTORY,

    /** A class for the component builder (Only used by the root component.) */
    COMPONENT_BUILDER,

    /** A provider class for a component provision. */
    COMPONENT_PROVISION_FACTORY,

    /** A class for the subcomponent or subcomponent builder. */
    SUBCOMPONENT
  }

  private final ComponentDescriptor componentDescriptor;
  private final ClassName name;
  private final NestingKind nestingKind;
  private final boolean isAbstract;
  private final Optional<GeneratedComponentModel> supermodel;
  private final Map<TypeElement, GeneratedComponentModel> subcomponentModels = new HashMap<>();
  private final TypeSpec.Builder component;
  private final SubcomponentNames subcomponentNames;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final List<CodeBlock> initializations = new ArrayList<>();
  private final List<CodeBlock> cancellations = new ArrayList<>();
  private final ListMultimap<FieldSpecKind, FieldSpec> fieldSpecsMap =
      MultimapBuilder.enumKeys(FieldSpecKind.class).arrayListValues().build();
  private final ListMultimap<MethodSpecKind, MethodSpec> methodSpecsMap =
      MultimapBuilder.enumKeys(MethodSpecKind.class).arrayListValues().build();
  private final ListMultimap<TypeSpecKind, TypeSpec> typeSpecsMap =
      MultimapBuilder.enumKeys(TypeSpecKind.class).arrayListValues().build();
  private final List<Supplier<TypeSpec>> switchingProviderSupplier = new ArrayList<>();
  private final ModifiableBindingMethods modifiableBindingMethods = new ModifiableBindingMethods();
  private final SetMultimap<Key, DependencyRequest> contributionsByMultibinding =
      HashMultimap.create();

  private GeneratedComponentModel(
      ComponentDescriptor componentDescriptor,
      ClassName name,
      NestingKind nestingKind,
      Optional<GeneratedComponentModel> supermodel,
      SubcomponentNames subcomponentNames,
      Modifier... modifiers) {
    checkName(name, nestingKind);
    this.componentDescriptor = componentDescriptor;
    this.name = name;
    this.nestingKind = nestingKind;
    this.isAbstract = Arrays.asList(modifiers).contains(ABSTRACT);
    this.supermodel = supermodel;
    this.component = classBuilder(name).addModifiers(modifiers);
    this.subcomponentNames = subcomponentNames;
  }

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

  /** Creates a model for a root component or top-level abstract subcomponent. */
  static GeneratedComponentModel create(ClassName name, BindingGraph graph, KeyFactory keyFactory) {
    return new GeneratedComponentModel(
        graph.componentDescriptor(),
        name,
        NestingKind.TOP_LEVEL,
        Optional.empty(), // supermodel
        new SubcomponentNames(graph, keyFactory),
        PUBLIC,
        graph.componentDescriptor().kind().isTopLevel() ? FINAL : ABSTRACT);
  }

  /**
   * Create a model for a subcomponent. This is for concrete subcomponents implementations when not
   * generating ahead-of-time subcomponents.
   */
  static GeneratedComponentModel forSubcomponent(
      ComponentDescriptor componentDescriptor, GeneratedComponentModel parentModel) {
    return new GeneratedComponentModel(
        componentDescriptor,
        parentModel.getSubcomponentName(componentDescriptor),
        NestingKind.MEMBER,
        Optional.empty(), // supermodel
        parentModel.subcomponentNames,
        PRIVATE,
        FINAL);
  }

  /**
   * Create a model for an inner abstract implementation of a subcomponent. This is applicable when
   * generating ahead-of-time subcomponents.
   */
  static GeneratedComponentModel forAbstractSubcomponent(
      ComponentDescriptor componentDescriptor,
      GeneratedComponentModel supermodel,
      GeneratedComponentModel parentModel) {
    return new GeneratedComponentModel(
        componentDescriptor,
        parentModel.getSubcomponentName(componentDescriptor),
        NestingKind.MEMBER,
        Optional.of(supermodel),
        parentModel.subcomponentNames,
        PUBLIC,
        ABSTRACT);
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

  /** Returns the model of this model's superclass. */
  Optional<GeneratedComponentModel> supermodel() {
    return supermodel;
  }

  /**
   * Returns the name of the builder class for this component. It will be a sibling of this
   * generated class unless this is a top-level component, in which case it will be nested.
   */
  ClassName getBuilderName() {
    return isNested()
        ? name.peerClass(subcomponentNames.get(componentDescriptor) + "Builder")
        : name.nestedClass("Builder");
  }

  /** Returns the name of the nested implementation class for a child component. */
  private ClassName getSubcomponentName(ComponentDescriptor childDescriptor) {
    checkArgument(
        componentDescriptor.subcomponents().contains(childDescriptor),
        "%s is not a child of %s",
        childDescriptor.componentDefinitionType(),
        componentDescriptor.componentDefinitionType());
    return name.nestedClass(subcomponentNames.get(childDescriptor) + "Impl");
  }

  /** Returns the simple subcomponent name for the given subcomponent builder {@link Key}. */
  String getSubcomponentName(Key key) {
    return subcomponentNames.get(key);
  }

  /** Returns the model of the child subcomponent. */
  Optional<GeneratedComponentModel> subcomponentModel(ComponentDescriptor subcomponent) {
    return Optional.ofNullable(subcomponentModels.get(subcomponent.componentDefinitionType()));
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
        supermodel.isPresent(),
        "Setting the supertype for model [%s] as a class when model has no supermodel.",
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
      MethodSpec methodSpec,
      boolean finalized) {
    modifiableBindingMethods.addMethod(type, request, methodSpec, finalized);
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
      MethodSpec methodSpec,
      boolean finalized) {
    modifiableBindingMethods.addMethod(type, request, methodSpec, finalized);
  }

  /** Adds the implementation for the given {@link ModifiableBindingMethod} to the component. */
  void addImplementedModifiableBindingMethod(ModifiableBindingMethod method) {
    modifiableBindingMethods.methodImplemented(method);
    methodSpecsMap.put(MethodSpecKind.MODIFIABLE_BINDING_METHOD, method.methodSpec());
  }

  /** Adds the given type to the component. */
  void addType(TypeSpecKind typeKind, TypeSpec typeSpec) {
    typeSpecsMap.put(typeKind, typeSpec);
  }

  /** Adds the given types to the component. */
  void addTypes(TypeSpecKind typeKind, Iterable<TypeSpec> typeSpecs) {
    typeSpecsMap.putAll(typeKind, typeSpecs);
  }

  /** Adds the type generated from the given subcomponent model. */
  void addSubcomponent(
      ComponentDescriptor subcomponent, GeneratedComponentModel subcomponentModel) {
    subcomponentModels.put(subcomponent.componentDefinitionType(), subcomponentModel);
    addType(TypeSpecKind.SUBCOMPONENT, subcomponentModel.generate().build());
  }

  /** Adds a {@link Supplier} for the SwitchingProvider for the component. */
  void addSwitchingProvider(Supplier<TypeSpec> typeSpecSupplier) {
    switchingProviderSupplier.add(typeSpecSupplier);
  }

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock) {
    initializations.add(codeBlock);
  }

  /** Adds the given code block to the cancellation listener method of the component. */
  void addCancellation(CodeBlock codeBlock) {
    cancellations.add(codeBlock);
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
    return uniqueMethodName(
        request, simpleVariableName(MoreTypes.asTypeElement(request.key().type())));
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

  /** Returns the list of {@link CodeBlock}s that need to go in the cancellation listener method. */
  ImmutableList<CodeBlock> getCancellations() {
    return ImmutableList.copyOf(cancellations);
  }

  /**
   * Returns the {@link ModifiableBindingMethod}s for this subcomponent implementation and its
   * superclasses.
   */
  ImmutableList<ModifiableBindingMethod> getModifiableBindingMethods() {
    ImmutableList.Builder<ModifiableBindingMethod> modifiableBindingMethodsBuilder =
        ImmutableList.builder();
    if (supermodel.isPresent()) {
      ImmutableList<ModifiableBindingMethod> superclassModifiableBindingMethods =
          supermodel.get().getModifiableBindingMethods();
      superclassModifiableBindingMethods.stream()
          .filter(method -> !modifiableBindingMethods.finalized(method))
          .forEach(modifiableBindingMethodsBuilder::add);
    }
    modifiableBindingMethodsBuilder.addAll(modifiableBindingMethods.getNonFinalizedMethods());
    return modifiableBindingMethodsBuilder.build();
  }

  /**
   * Returns the {@link ModifiableBindingMethod} for this subcomponent for the given binding, if it
   * exists.
   */
  Optional<ModifiableBindingMethod> getModifiableBindingMethod(BindingRequest request) {
    Optional<ModifiableBindingMethod> method = modifiableBindingMethods.getMethod(request);
    if (!method.isPresent() && supermodel.isPresent()) {
      return supermodel.get().getModifiableBindingMethod(request);
    }
    return method;
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
  void registerImplementedMultibinding(ContributionBinding multibinding) {
    checkArgument(multibinding.isSyntheticMultibinding());
    // We register a multibinding as implemented each time we request the multibinding expression,
    // so only modify the set of contributions once.
    if (!contributionsByMultibinding.containsKey(multibinding.key())) {
      contributionsByMultibinding.putAll(multibinding.key(), multibinding.dependencies());
    }
  }

  /**
   * Returns the set of multibinding contributions associated with all superclass implementations of
   * a multibinding.
   */
  ImmutableSet<DependencyRequest> superclassContributionsMade(Key key) {
    ImmutableSet.Builder<DependencyRequest> contributionsBuilder = ImmutableSet.builder();
    if (supermodel.isPresent()) {
      contributionsBuilder.addAll(supermodel.get().getAllMultibindingContributions(key));
    }
    return contributionsBuilder.build();
  }

  /**
   * Returns the set of multibinding contributions associated with all implementations of a
   * multibinding.
   */
  private ImmutableSet<DependencyRequest> getAllMultibindingContributions(Key key) {
    ImmutableSet.Builder<DependencyRequest> contributionsBuilder = ImmutableSet.builder();
    if (contributionsByMultibinding.containsKey(key)) {
      contributionsBuilder.addAll(contributionsByMultibinding.get(key));
    }
    return contributionsBuilder.addAll(superclassContributionsMade(key)).build();
  }
}
