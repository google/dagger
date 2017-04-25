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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.DELEGATED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.INITIALIZED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.BindingKey.contribution;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.MemberSelect.emptyFrameworkMapFactory;
import static dagger.internal.codegen.MemberSelect.emptySetProvider;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.MemberSelect.noOpMembersInjector;
import static dagger.internal.codegen.MemberSelect.staticMethod;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.Scope.reusableScope;
import static dagger.internal.codegen.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.TypeNames.LISTENABLE_FUTURE;
import static dagger.internal.codegen.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER_MANAGER;
import static dagger.internal.codegen.TypeNames.SET_FACTORY;
import static dagger.internal.codegen.TypeNames.SET_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.SET_PRODUCER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.TypeNames.TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR;
import static dagger.internal.codegen.TypeNames.providerOf;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.DelegateFactory;
import dagger.internal.InstanceFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.Preconditions;
import dagger.internal.SetFactory;
import dagger.internal.TypedReleasableReferenceManagerDecorator;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.MapOfProducerProducer;
import dagger.producers.internal.MapProducer;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import dagger.releasablereferences.CanReleaseReferences;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Creates the implementation class for a component or subcomponent. */
abstract class AbstractComponentWriter implements HasBindingMembers {
  private static final String NOOP_BUILDER_METHOD_JAVADOC =
      "This module is declared, but an instance is not used in the component. This method is a "
          + "no-op. For more, see https://google.github.io/dagger/unused-modules.\n";

  // TODO(dpb): Make all these fields private after refactoring is complete.
  protected final Elements elements;
  protected final Types types;
  protected final Key.Factory keyFactory;
  protected final CompilerOptions compilerOptions;
  protected final ClassName name;
  protected final BindingGraph graph;
  protected final ImmutableMap<ComponentDescriptor, String> subcomponentNames;
  private final Map<BindingKey, InitializationState> initializationStates = new HashMap<>();
  protected final TypeSpec.Builder component;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final Map<BindingKey, MemberSelect> memberSelects = new HashMap<>();
  private final Map<BindingKey, MemberSelect> producerFromProviderMemberSelects = new HashMap<>();
  private final RequestFulfillmentRegistry requestFulfillmentRegistry;
  protected final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  protected Optional<ClassName> builderName = Optional.empty();
  private final OptionalFactories optionalFactories;
  private boolean done;

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private ImmutableMap<ComponentRequirement, FieldSpec> builderFields = ImmutableMap.of();

  /**
   * For each component requirement, the member select for the component field that holds it.
   *
   * <p>Fields are written for all requirements for subcomponents that do not use a builder, and for
   * any requirement that is reused from a subcomponent of this component.
   */
  protected final Map<ComponentRequirement, MemberSelect> componentContributionFields =
      Maps.newHashMap();

  /**
   * The member-selects for {@link dagger.internal.ReferenceReleasingProviderManager} fields,
   * indexed by their {@link CanReleaseReferences @CanReleaseReferences} scope.
   */
  private ImmutableMap<Scope, MemberSelect> referenceReleasingProviderManagerFields;

  AbstractComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentNames,
      OptionalFactories optionalFactories) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.component = classBuilder(name);
    this.name = name;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
    this.optionalFactories = optionalFactories;
    this.requestFulfillmentRegistry =
        new RequestFulfillmentRegistry(graph.resolvedBindings(), this);
  }

  protected AbstractComponentWriter(
      AbstractComponentWriter parent, ClassName name, BindingGraph graph) {
    this(
        parent.types,
        parent.elements,
        parent.keyFactory,
        parent.compilerOptions,
        name,
        graph,
        parent.subcomponentNames,
        parent.optionalFactories);
  }

  protected final ClassName componentDefinitionTypeName() {
    return ClassName.get(graph.componentType());
  }

  /**
   * Returns an expression that evaluates to an instance of the requirement, looking for either a
   * builder field or a component field.
   */
  private CodeBlock getComponentContributionExpression(ComponentRequirement componentRequirement) {
    if (builderFields.containsKey(componentRequirement)) {
      return CodeBlock.of("builder.$N", builderFields.get(componentRequirement));
    } else {
      Optional<CodeBlock> codeBlock =
          getOrCreateComponentRequirementFieldExpression(componentRequirement);
      checkState(
          codeBlock.isPresent(), "no builder or component field for %s", componentRequirement);
      return codeBlock.get();
    }
  }

  /**
   * Returns an expression for a component requirement field. Adds a field the first time one is
   * requested for a requirement if this component's builder has a field for it.
   */
  protected Optional<CodeBlock> getOrCreateComponentRequirementFieldExpression(
      ComponentRequirement componentRequirement) {
    MemberSelect fieldSelect = componentContributionFields.get(componentRequirement);
    if (fieldSelect == null) {
      if (!builderFields.containsKey(componentRequirement)) {
        return Optional.empty();
      }
      FieldSpec componentField =
          componentField(
                  TypeName.get(componentRequirement.type()),
                  simpleVariableName(componentRequirement.typeElement()))
              .addModifiers(PRIVATE, FINAL)
              .build();
      component.addField(componentField);
      constructor.addCode(
          "this.$N = builder.$N;", componentField, builderFields.get(componentRequirement));
      fieldSelect = localField(name, componentField.name);
      componentContributionFields.put(componentRequirement, fieldSelect);
    }
    return Optional.of(fieldSelect.getExpressionFor(name));
  }

  /**
   * Creates a {@link FieldSpec.Builder} with a unique name based off of {@code name}.
   */
  protected final FieldSpec.Builder componentField(TypeName type, String name) {
    return FieldSpec.builder(type, componentFieldNames.getUniqueName(name));
  }

  private CodeBlock getMemberSelectExpression(BindingKey key) {
    return getMemberSelect(key).getExpressionFor(name);
  }

  @Override
  public MemberSelect getMemberSelect(BindingKey key) {
    return memberSelects.get(key);
  }

  /**
   * Returns the initialization state of the factory field for a binding key in this component.
   */
  protected InitializationState getInitializationState(BindingKey bindingKey) {
    return initializationStates.containsKey(bindingKey)
        ? initializationStates.get(bindingKey)
        : UNINITIALIZED;
  }

  private void setInitializationState(BindingKey bindingKey, InitializationState state) {
    initializationStates.put(bindingKey, state);
  }

  /**
   * The member-select expression for the {@link dagger.internal.ReferenceReleasingProviderManager}
   * object for a scope.
   */
  protected CodeBlock getReferenceReleasingProviderManagerExpression(Scope scope) {
    return referenceReleasingProviderManagerFields.get(scope).getExpressionFor(name);
  }

  /**
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  final TypeSpec.Builder write() {
    checkState(!done, "ComponentWriter has already been generated.");
    decorateComponent();
    addBuilder();
    addFactoryMethods();
    addReferenceReleasingProviderManagerFields();
    addFrameworkFields();
    initializeFrameworkTypes();
    implementInterfaceMethods();
    addSubcomponents();
    component.addMethod(constructor.build());
    if (graph.componentDescriptor().kind().isTopLevel()) {
      optionalFactories.addMembers(component);
    }
    done = true;
    return component;
  }

  /**
   * Adds Javadoc, modifiers, supertypes, and annotations to the component implementation class
   * declaration.
   */
  protected abstract void decorateComponent();

  /**
   * Adds a builder type.
   */
  protected void addBuilder() {
    builderName = Optional.of(builderName());
    TypeSpec.Builder componentBuilder =
        createBuilder(builderName.get().simpleName()).addModifiers(FINAL);

    Optional<BuilderSpec> builderSpec = graph.componentDescriptor().builderSpec();
    if (builderSpec.isPresent()) {
      componentBuilder.addModifiers(PRIVATE);
      addSupertype(componentBuilder, builderSpec.get().builderDefinitionType());
    } else {
      componentBuilder
          .addModifiers(PUBLIC)
          .addMethod(constructorBuilder().addModifiers(PRIVATE).build());
    }

    builderFields = addBuilderFields(componentBuilder);
    addBuildMethod(componentBuilder, builderSpec);
    addBuilderMethods(componentBuilder, builderSpec);
    addBuilderClass(componentBuilder.build());

    constructor.addParameter(builderName.get(), "builder");
    constructor.addStatement("assert builder != null");
  }

  /**
   * Adds {@code builder} as a nested builder class. Root components and subcomponents will nest
   * this in different classes.
   */
  protected abstract void addBuilderClass(TypeSpec builder);

  /**
   * Adds fields for each of the {@linkplain BindingGraph#componentRequirements component
   * requirements}. Regardless of builder spec, there is always one field per requirement.
   */
  private ImmutableMap<ComponentRequirement, FieldSpec> addBuilderFields(
      TypeSpec.Builder componentBuilder) {
    UniqueNameSet builderFieldNames = new UniqueNameSet();
    ImmutableMap.Builder<ComponentRequirement, FieldSpec> builderFields = ImmutableMap.builder();
    for (ComponentRequirement componentRequirement : graph.componentRequirements()) {
      String contributionName =
          builderFieldNames.getUniqueName(componentRequirement.variableName());
      FieldSpec builderField =
          FieldSpec.builder(TypeName.get(componentRequirement.type()), contributionName, PRIVATE)
              .build();
      componentBuilder.addField(builderField);
      builderFields.put(componentRequirement, builderField);
    }
    return builderFields.build();
  }

  /** Adds the build method to the builder. */
  private void addBuildMethod(
      TypeSpec.Builder componentBuilder, Optional<BuilderSpec> builderSpec) {
    MethodSpec.Builder buildMethod;
    if (builderSpec.isPresent()) {
      ExecutableElement specBuildMethod = builderSpec.get().buildMethod();
      // Note: we don't use the specBuildMethod.getReturnType() as the return type
      // because it might be a type variable.  We make use of covariant returns to allow
      // us to return the component type, which will always be valid.
      buildMethod =
          methodBuilder(specBuildMethod.getSimpleName().toString()).addAnnotation(Override.class);
    } else {
      buildMethod = methodBuilder("build");
    }
    buildMethod.returns(componentDefinitionTypeName()).addModifiers(PUBLIC);

    for (Map.Entry<ComponentRequirement, FieldSpec> builderFieldEntry : builderFields.entrySet()) {
      FieldSpec builderField = builderFieldEntry.getValue();
      switch (builderFieldEntry.getKey().nullPolicy(elements, types)) {
        case NEW:
          buildMethod.addCode(
              "if ($1N == null) { this.$1N = new $2T(); }", builderField, builderField.type);
          break;
        case THROW:
          buildMethod.addCode(
              "if ($N == null) { throw new $T($T.class.getCanonicalName() + $S); }",
              builderField,
              IllegalStateException.class,
              TypeNames.rawTypeName(builderField.type),
              " must be set");
          break;
        case ALLOW:
          break;
        default:
          throw new AssertionError(builderFieldEntry.getKey());
      }
    }
    buildMethod.addStatement("return new $T(this)", name);
    componentBuilder.addMethod(buildMethod.build());
  }

  /**
   * Adds the methods that set each of parameters on the builder. If the {@link BuilderSpec} is
   * present, it will tailor the methods to match the spec.
   */
  private void addBuilderMethods(
      TypeSpec.Builder componentBuilder, Optional<BuilderSpec> builderSpec) {
    ImmutableSet<ComponentRequirement> componentRequirements = graph.componentRequirements();
    if (builderSpec.isPresent()) {
      UniqueNameSet parameterNames = new UniqueNameSet();
      for (BuilderRequirementMethod requirementMethod : builderSpec.get().requirementMethods()) {
        ComponentRequirement builderRequirement = requirementMethod.requirement();
        ExecutableElement specMethod = requirementMethod.method();
        MethodSpec.Builder builderMethod = addBuilderMethodFromSpec(specMethod);
        VariableElement parameterElement = Iterables.getOnlyElement(specMethod.getParameters());
        String parameterName = parameterNames.getUniqueName(parameterElement.getSimpleName());

        TypeName argType =
            parameterElement.asType().getKind().isPrimitive()
                // Primitives need to use the original (unresolved) type to avoid boxing.
                ? TypeName.get(parameterElement.asType())
                // Otherwise we use the full resolved type.
                : TypeName.get(builderRequirement.type());

        builderMethod.addParameter(argType, parameterName);
        if (componentRequirements.contains(builderRequirement)) {
          // required type
          builderMethod.addStatement(
              "this.$N = $L",
              builderFields.get(builderRequirement),
              builderRequirement
                      .nullPolicy(elements, types)
                      .equals(ComponentRequirement.NullPolicy.ALLOW)
                  ? parameterName
                  : CodeBlock.of("$T.checkNotNull($L)", Preconditions.class, parameterName));
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else if (graph.ownedModuleTypes().contains(builderRequirement.typeElement())) {
          // owned, but not required
          builderMethod.addJavadoc(NOOP_BUILDER_METHOD_JAVADOC);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else {
          // neither owned nor required, so it must be an inherited module
          builderMethod.addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UnsupportedOperationException.class,
              String.class,
              "%s cannot be set because it is inherited from the enclosing component",
              TypeNames.rawTypeName(TypeName.get(builderRequirement.type())));
        }
        componentBuilder.addMethod(builderMethod.build());
      }
    } else {
      for (ComponentRequirement componentRequirement : graph.availableDependencies()) {
        String componentRequirementName = simpleVariableName(componentRequirement.typeElement());
        MethodSpec.Builder builderMethod =
            methodBuilder(componentRequirementName)
                .returns(builderName.get())
                .addModifiers(PUBLIC)
                .addParameter(ClassName.get(componentRequirement.type()), componentRequirementName);
        if (componentRequirements.contains(componentRequirement)) {
          builderMethod.addStatement(
              "this.$N = $T.checkNotNull($L)",
              builderFields.get(componentRequirement),
              Preconditions.class,
              componentRequirementName);
        } else {
          builderMethod.addStatement("$T.checkNotNull($L)",
              Preconditions.class,
              componentRequirementName);
          builderMethod.addJavadoc("@deprecated " + NOOP_BUILDER_METHOD_JAVADOC);
          builderMethod.addAnnotation(Deprecated.class);
        }
        builderMethod.addStatement("return this");
        componentBuilder.addMethod(builderMethod.build());
      }
    }
  }

  private void addBuilderMethodReturnStatementForSpec(
      ExecutableElement specMethod, MethodSpec.Builder builderMethod) {
    if (!specMethod.getReturnType().getKind().equals(VOID)) {
      builderMethod.addStatement("return this");
    }
  }

  private MethodSpec.Builder addBuilderMethodFromSpec(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    MethodSpec.Builder builderMethod =
        methodBuilder(method.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
    // If the return type is void, we add a method with the void return type.
    // Otherwise we use the generated builder name and take advantage of covariant returns
    // (so that we don't have to worry about setter methods that return type variables).
    if (!returnType.getKind().equals(TypeKind.VOID)) {
      builderMethod.returns(builderName.get());
    }
    return builderMethod;
  }

  /**
   * Creates the builder class.
   */
  protected abstract TypeSpec.Builder createBuilder(String builderName);

  protected abstract ClassName builderName();

  /**
   * Adds component factory methods.
   */
  protected abstract void addFactoryMethods();

  /**
   * Adds a {@link dagger.internal.ReferenceReleasingProviderManager} field for every {@link
   * CanReleaseReferences @ReleasableReferences} scope for which {@linkplain
   * #requiresReleasableReferences(Scope) one is required}.
   */
  private void addReferenceReleasingProviderManagerFields() {
    ImmutableMap.Builder<Scope, MemberSelect> fields = ImmutableMap.builder();
    for (Scope scope : graph.componentDescriptor().releasableReferencesScopes()) {
      if (requiresReleasableReferences(scope)) {
        FieldSpec field = referenceReleasingProxyManagerField(scope);
        component.addField(field);
        fields.put(scope, localField(name, field.name));
      }
    }
    referenceReleasingProviderManagerFields = fields.build();
  }

  /**
   * Returns {@code true} if {@code scope} {@linkplain CanReleaseReferences can release its
   * references} and there is a dependency request in the component for any of
   *
   * <ul>
   * <li>{@code @ForReleasableReferences(scope)} {@link ReleasableReferenceManager}
   * <li>{@code @ForReleasableReferences(scope)} {@code TypedReleasableReferenceManager<M>}, where
   *     {@code M} is the releasable-references metatadata type for {@code scope}
   * <li>{@code Set<ReleasableReferenceManager>}
   * <li>{@code Set<TypedReleasableReferenceManager<M>>}, where {@code M} is the metadata type for
   *     the scope
   * </ul>
   */
  private boolean requiresReleasableReferences(Scope scope) {
    if (!scope.canReleaseReferences()) {
      return false;
    }

    if (graphHasContributionBinding(keyFactory.forReleasableReferenceManager(scope))
        || graphHasContributionBinding(keyFactory.forSetOfReleasableReferenceManagers())) {
      return true;
    }

    for (AnnotationMirror metadata : scope.releasableReferencesMetadata()) {
      if (graphHasContributionBinding(
              keyFactory.forTypedReleasableReferenceManager(scope, metadata.getAnnotationType()))
          || graphHasContributionBinding(
              keyFactory.forSetOfTypedReleasableReferenceManagers(metadata.getAnnotationType()))) {
        return true;
      }
    }

    return false;
  }

  private boolean graphHasContributionBinding(Key key) {
    return graph.resolvedBindings().containsKey(contribution(key));
  }

  private FieldSpec referenceReleasingProxyManagerField(Scope scope) {
    return componentField(
            REFERENCE_RELEASING_PROVIDER_MANAGER,
            UPPER_CAMEL.to(
                LOWER_CAMEL, scope.scopeAnnotationElement().getSimpleName() + "References"))
        .addModifiers(PRIVATE, FINAL)
        .initializer(
            "new $T($T.class)",
            REFERENCE_RELEASING_PROVIDER_MANAGER,
            scope.scopeAnnotationElement())
        .addJavadoc(
            "The manager that releases references for the {@link $T} scope.\n",
            scope.scopeAnnotationElement())
        .build();
  }

  private void addFrameworkFields() {
    graph.resolvedBindings().values().forEach(this::addField);
  }

  private void addField(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    // If the binding can be satisfied with a static method call without dependencies or state,
    // no field is necessary.
    Optional<MemberSelect> staticMemberSelect = staticMemberSelect(resolvedBindings);
    if (staticMemberSelect.isPresent()) {
      memberSelects.put(bindingKey, staticMemberSelect.get());
      return;
    }

    // No field needed if there are no owned bindings.
    if (resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    // TODO(gak): get rid of the field for unscoped delegated bindings

    FieldSpec frameworkField = addFrameworkField(resolvedBindings, Optional.empty());
    memberSelects.put(bindingKey, localField(name, frameworkField.name));
  }

  /**
   * Adds a field representing the resolved bindings, optionally forcing it to use a particular
   * framework class (instead of the class the resolved bindings would typically use).
   */
  private FieldSpec addFrameworkField(
      ResolvedBindings resolvedBindings, Optional<ClassName> frameworkClass) {
    boolean useRawType = useRawType(resolvedBindings);

    FrameworkField contributionBindingField =
        FrameworkField.forResolvedBindings(resolvedBindings, frameworkClass);
    FieldSpec.Builder contributionField =
        componentField(
            useRawType
                ? contributionBindingField.type().rawType
                : contributionBindingField.type(),
            contributionBindingField.name());
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
    }

    FieldSpec field = contributionField.build();
    component.addField(field);
    return field;
  }

  private boolean useRawType(ResolvedBindings resolvedBindings) {
    return useRawType(resolvedBindings.bindingPackage());
  }

  private boolean useRawType(Binding binding) {
    return useRawType(binding.bindingPackage());
  }

  private boolean useRawType(Optional<String> bindingPackage) {
    return bindingPackage.isPresent() && !bindingPackage.get().equals(name.packageName());
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments or a
   * no-op members injection binding, then we don't need a field to hold its factory. In that case,
   * this method returns the static member select that returns the factory or no-op members
   * injector.
   */
  private static Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
        if (contributionBinding.factoryCreationStrategy().equals(SINGLETON_INSTANCE)
            && !contributionBinding.scope().isPresent()) {
          switch (contributionBinding.bindingKind()) {
            case SYNTHETIC_MULTIBOUND_MAP:
              BindingType bindingType = contributionBinding.bindingType();
              MapType mapType = MapType.from(contributionBinding.key());
              return Optional.of(
                  emptyFrameworkMapFactory(
                      bindingType,
                      mapType.keyType(),
                      mapType.unwrappedValueType(bindingType.frameworkClass())));

            case SYNTHETIC_MULTIBOUND_SET:
              return Optional.of(
                  emptySetFactoryStaticMemberSelect(
                      contributionBinding.bindingType(), contributionBinding.key()));

            case INJECTION:
            case PROVISION:
              if (bindingKey.key().type().getKind().equals(DECLARED)) {
                ImmutableList<TypeVariableName> typeVariables =
                    bindingTypeElementTypeVariableNames(contributionBinding);
                if (!typeVariables.isEmpty()) {
                  List<? extends TypeMirror> typeArguments =
                      ((DeclaredType) bindingKey.key().type()).getTypeArguments();
                  return Optional.of(MemberSelect.parameterizedFactoryCreateMethod(
                      generatedClassNameForBinding(contributionBinding), typeArguments));
                }
              }
              // fall through

            default:
              return Optional.of(
                  staticMethod(
                      generatedClassNameForBinding(contributionBinding), CodeBlock.of("create()")));
          }
        }
        break;

      case MEMBERS_INJECTION:
        Optional<MembersInjectionBinding> membersInjectionBinding =
            resolvedBindings.membersInjectionBinding();
        if (membersInjectionBinding.isPresent()
            && membersInjectionBinding.get().injectionSites().isEmpty()) {
          return Optional.of(noOpMembersInjector(membersInjectionBinding.get().key().type()));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.empty();
  }

  /**
   * A static member select for an empty set factory. Calls {@link SetFactory#empty()}, {@link
   * SetProducer#empty()}, or {@link SetOfProducedProducer#empty()}, depending on the set
   * bindings.
   */
  private static MemberSelect emptySetFactoryStaticMemberSelect(BindingType bindingType, Key key) {
    return emptySetProvider(setFactoryClassName(bindingType, key), SetType.from(key));
  }

  /**
   * The {@link Set} factory class name appropriate for set bindings.
   *
   * <ul>
   * <li>{@link SetFactory} for provision bindings.
   * <li>{@link SetProducer} for production bindings for {@code Set<T>}.
   * <li>{@link SetOfProducedProducer} for production bindings for {@code Set<Produced<T>>}.
   * </ul>
   */
  private static ClassName setFactoryClassName(BindingType bindingType, Key key) {
    if (bindingType.equals(BindingType.PROVISION)) {
      return SET_FACTORY;
    } else {
      SetType setType = SetType.from(key);
      return setType.elementsAreTypeOf(Produced.class) ? SET_OF_PRODUCED_PRODUCER : SET_PRODUCER;
    }
  }

  /**
   * The {@link Map}-of-value factory class name appropriate for map bindings.
   *
   * <ul>
   * <li>{@link MapFactory} for provision bindings.
   * <li>{@link MapProducer} for production bindings.
   * </ul>
   */
  private static ClassName mapFactoryClassName(ContributionBinding binding) {
    switch (binding.bindingType()) {
      case PRODUCTION:
        return MapType.from(binding.key()).valuesAreTypeOf(Produced.class)
            ? MAP_OF_PRODUCED_PRODUCER : MAP_PRODUCER;

      case PROVISION:
      case MEMBERS_INJECTION:
        return MAP_FACTORY;

      default:
        throw new AssertionError(binding.toString());
    }
  }

  /**
   * The {@link Map}-of-framework factory class name appropriate for map bindings.
   *
   * <ul>
   * <li>{@link MapProviderFactory} for provision bindings.
   * <li>{@link MapOfProducerProducer} for production bindings.
   * </ul>
   */
  private static ClassName frameworkMapFactoryClassName(BindingType bindingType) {
    return bindingType.equals(BindingType.PRODUCTION)
        ? MAP_OF_PRODUCER_PRODUCER : MAP_PROVIDER_FACTORY;
  }

  private void implementInterfaceMethods() {
    Set<MethodSignature> interfaceMethods = Sets.newHashSet();
    for (ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest interfaceRequest = componentMethod.dependencyRequest().get();
        ExecutableElement methodElement =
            MoreElements.asExecutable(componentMethod.methodElement());
        ExecutableType requestType =
            MoreTypes.asExecutable(
                types.asMemberOf(
                    MoreTypes.asDeclared(graph.componentType().asType()), methodElement));
        MethodSignature signature =
            MethodSignature.fromExecutableType(
                methodElement.getSimpleName().toString(), requestType);
        if (!interfaceMethods.contains(signature)) {
          interfaceMethods.add(signature);
          MethodSpec.Builder interfaceMethod =
              methodSpecForComponentMethod(methodElement, requestType);
          RequestFulfillment fulfillment =
              requestFulfillmentRegistry.getRequestFulfillment(interfaceRequest.bindingKey());
          CodeBlock codeBlock = fulfillment.getSnippetForDependencyRequest(interfaceRequest, name);
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              List<? extends VariableElement> parameters = methodElement.getParameters();
              if (!parameters.isEmpty()) {
                Name parameterName =
                    Iterables.getOnlyElement(methodElement.getParameters()).getSimpleName();
                interfaceMethod.addStatement("$L.injectMembers($L)", codeBlock, parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.addStatement("return $L", parameterName);
                }
                break;
              }
              // fall through
            default:
              interfaceMethod.addStatement("return $L", codeBlock);
              break;
          }
          component.addMethod(interfaceMethod.build());
        }
      }
    }
  }

  private MethodSpec.Builder methodSpecForComponentMethod(
      ExecutableElement method, ExecutableType methodType) {
    String methodName = method.getSimpleName().toString();
    MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);

    methodBuilder.addAnnotation(Override.class);

    Set<Modifier> modifiers = EnumSet.copyOf(method.getModifiers());
    modifiers.remove(Modifier.ABSTRACT);
    methodBuilder.addModifiers(modifiers);

    methodBuilder.returns(TypeName.get(methodType.getReturnType()));

    List<? extends VariableElement> parameters = method.getParameters();
    List<? extends TypeMirror> resolvedParameterTypes = methodType.getParameterTypes();
    verify(parameters.size() == resolvedParameterTypes.size());
    for (int i = 0; i < parameters.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeName type = TypeName.get(resolvedParameterTypes.get(i));
      String name = parameter.getSimpleName().toString();
      Set<Modifier> parameterModifiers = parameter.getModifiers();
      ParameterSpec.Builder parameterBuilder =
          ParameterSpec.builder(type, name)
              .addModifiers(parameterModifiers.toArray(new Modifier[0]));
      methodBuilder.addParameter(parameterBuilder.build());
    }
    for (TypeMirror thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }
    return methodBuilder;
  }

  private void addSubcomponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      ComponentMethodDescriptor componentMethodDescriptor =
          graph.componentDescriptor()
              .subcomponentsByFactoryMethod()
              .inverse()
              .get(subgraph.componentDescriptor());
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, Optional.ofNullable(componentMethodDescriptor), subgraph);
      component.addType(subcomponent.write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void initializeFrameworkTypes() {
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    for (BindingKey bindingKey : graph.resolvedBindings().keySet()) {
      initializeFrameworkType(bindingKey).ifPresent(codeBlocks::add);
    }
    List<List<CodeBlock>> partitions =
        Lists.partition(codeBlocks.build(), INITIALIZATIONS_PER_INITIALIZE_METHOD);

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
      if (builderName.isPresent()) {
        initializeMethod.addParameter(builderName.get(), "builder", FINAL);
        constructor.addStatement("$L(builder)", methodName);
      } else {
        constructor.addStatement("$L()", methodName);
      }
      component.addMethod(initializeMethod.build());
    }
  }

  /**
   * Returns a single code block representing the initialization of the framework type.
   *
   * <p>Note that this must be a single code block because initialization code blocks can be invoked
   * from any place in any order.  By requiring a single code block (often of concatenated code
   * blocks) we ensure that things like local variables always behave as expected by the
   * initialization logic.
   */
  private Optional<CodeBlock> initializeFrameworkType(BindingKey bindingKey) {
    // If the field is inherited or the member select is static, don't initialize.
    MemberSelect memberSelect = getMemberSelect(bindingKey);
    if (memberSelect.staticMember() || !memberSelect.owningClass().equals(name)) {
      return Optional.empty();
    }

    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        return initializeContributionBinding(bindingKey);

      case MEMBERS_INJECTION:
        return initializeMembersInjectionBinding(bindingKey);

      default:
        throw new AssertionError();
    }
  }

  private Optional<CodeBlock> initializeContributionBinding(BindingKey bindingKey) {
    ContributionBinding binding = graph.resolvedBindings().get(bindingKey).contributionBinding();
    /* We have some duplication in the branches below b/c initializeDeferredDependencies must be
     * called before we get the code block that initializes the member. */
    switch (binding.factoryCreationStrategy()) {
      case DELEGATE:
        CodeBlock delegatingCodeBlock =
            CodeBlock.of(
                "($T) $L",
                binding.bindingType().frameworkClass(),
                getMemberSelect(
                        Iterables.getOnlyElement(binding.explicitDependencies()).bindingKey())
                    .getExpressionFor(name));
        return Optional.of(
            CodeBlocks.concat(
                ImmutableList.of(
                    initializeDeferredDependencies(binding),
                    initializeMember(
                        bindingKey, decorateForScope(delegatingCodeBlock, binding.scope())))));
      case SINGLETON_INSTANCE:
        if (!binding.scope().isPresent()) {
          return Optional.empty();
        }
        // fall through
      case CLASS_CONSTRUCTOR:
        return Optional.of(
            CodeBlocks.concat(
                ImmutableList.of(
                    initializeDeferredDependencies(binding),
                    initializeMember(
                        bindingKey, initializeFactoryForContributionBinding(binding)))));
      default:
        throw new AssertionError();
    }
  }

  private Optional<CodeBlock> initializeMembersInjectionBinding(BindingKey bindingKey) {
    MembersInjectionBinding binding =
        graph.resolvedBindings().get(bindingKey).membersInjectionBinding().get();

    if (binding.injectionSites().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        CodeBlocks.concat(
            ImmutableList.of(
                initializeDeferredDependencies(binding),
                initializeMember(bindingKey, initializeMembersInjectorForBinding(binding)))));
  }

  /**
   * Initializes any dependencies of the given binding that need to be instantiated, i.e., as we get
   * to them during normal initialization.
   */
  private CodeBlock initializeDeferredDependencies(Binding binding) {
    return CodeBlocks.concat(
        ImmutableList.of(
            initializeDelegateFactoriesForUninitializedDependencies(binding),
            initializeProducersFromProviderDependencies(binding)));
  }

  /**
   * Initializes delegate factories for any dependencies of {@code binding} that are uninitialized
   * because of a dependency cycle.
   */
  private CodeBlock initializeDelegateFactoriesForUninitializedDependencies(Binding binding) {
    ImmutableList.Builder<CodeBlock> initializations = ImmutableList.builder();

    for (BindingKey dependencyKey :
        FluentIterable.from(binding.dependencies())
            .transform(DependencyRequest::bindingKey)
            .toSet()) {
      if (!getMemberSelect(dependencyKey).staticMember()
          && getInitializationState(dependencyKey).equals(UNINITIALIZED)) {
        initializations.add(
            CodeBlock.of(
                "this.$L = new $T();", getMemberSelectExpression(dependencyKey), DELEGATE_FACTORY));
        setInitializationState(dependencyKey, DELEGATED);
      }
    }

    return CodeBlocks.concat(initializations.build());
  }

  private CodeBlock initializeProducersFromProviderDependencies(Binding binding) {
    ImmutableList.Builder<CodeBlock> initializations = ImmutableList.builder();
    for (FrameworkDependency frameworkDependency : binding.frameworkDependencies()) {
      ResolvedBindings resolvedBindings =
          graph.resolvedBindings().get(frameworkDependency.bindingKey());
      if (resolvedBindings.frameworkClass().equals(Provider.class)
          && frameworkDependency.frameworkClass().equals(Producer.class)) {
        MemberSelect memberSelect =
            producerFromProviderMemberSelects.get(frameworkDependency.bindingKey());
        if (memberSelect != null) {
          continue;
        }
        FieldSpec frameworkField =
            addFrameworkField(resolvedBindings, Optional.of(PRODUCER));
        memberSelect = localField(name, frameworkField.name);
        producerFromProviderMemberSelects.put(frameworkDependency.bindingKey(), memberSelect);
        initializations.add(
            CodeBlock.of(
                "this.$L = $L;",
                memberSelect.getExpressionFor(name),
                requestFulfillmentRegistry
                    .getRequestFulfillment(frameworkDependency.bindingKey())
                    .getSnippetForFrameworkDependency(frameworkDependency, name)));
      }
    }
    return CodeBlocks.concat(initializations.build());
  }

  private CodeBlock initializeMember(BindingKey bindingKey, CodeBlock initializationCodeBlock) {
    ImmutableList.Builder<CodeBlock> initializations = ImmutableList.builder();

    CodeBlock memberSelect = getMemberSelectExpression(bindingKey);
    CodeBlock delegateFactoryVariable = delegateFactoryVariableExpression(bindingKey);
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializations.add(
          CodeBlock.of(
              "$1T $2L = ($1T) $3L;", DELEGATE_FACTORY, delegateFactoryVariable, memberSelect));
    }
    initializations.add(
        CodeBlock.of("this.$L = $L;", memberSelect, initializationCodeBlock));
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializations.add(
          CodeBlock.of("$L.setDelegatedProvider($L);", delegateFactoryVariable, memberSelect));
    }
    setInitializationState(bindingKey, INITIALIZED);

    return CodeBlocks.concat(initializations.build());
  }

  private CodeBlock delegateFactoryVariableExpression(BindingKey key) {
    return CodeBlock.of("$LDelegate", getMemberSelectExpression(key).toString().replace('.', '_'));
  }

  private CodeBlock initializeFactoryForContributionBinding(ContributionBinding binding) {
    TypeName bindingKeyTypeName = TypeName.get(binding.key().type());
    switch (binding.bindingKind()) {
      case COMPONENT:
        return CodeBlock.of(
            "$T.<$T>create($L)",
            INSTANCE_FACTORY,
            bindingKeyTypeName,
            bindingKeyTypeName.equals(componentDefinitionTypeName())
                ? "this"
                : getComponentContributionExpression(
                    ComponentRequirement.forDependency(binding.key().type())));

      case COMPONENT_PROVISION:
        {
          TypeElement dependencyType = dependencyTypeForBinding(binding);
          String dependencyVariable = simpleVariableName(dependencyType);
          String componentMethod = binding.bindingElement().get().getSimpleName().toString();
          CodeBlock callFactoryMethod =
              CodeBlock.of("$L.$L()", dependencyVariable, componentMethod);
          // TODO(sameb): This throws a very vague NPE right now.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          CodeBlock getMethodBody =
              binding.nullableType().isPresent()
                      || compilerOptions.nullableValidationKind().equals(Diagnostic.Kind.WARNING)
                  ? CodeBlock.of("return $L;", callFactoryMethod)
                  : CodeBlock.of(
                      "return $T.checkNotNull($L, $S);",
                      Preconditions.class,
                      callFactoryMethod,
                      CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          ClassName dependencyClassName = ClassName.get(dependencyType);
          String factoryName =
              dependencyClassName.toString().replace('.', '_') + "_" + componentMethod;
          MethodSpec.Builder getMethod =
              methodBuilder("get")
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .returns(bindingKeyTypeName)
                  .addCode(getMethodBody);
          if (binding.nullableType().isPresent()) {
            getMethod.addAnnotation(
                ClassName.get(MoreTypes.asTypeElement(binding.nullableType().get())));
          }
          component.addType(
              TypeSpec.classBuilder(factoryName)
                  .addSuperinterface(providerOf(bindingKeyTypeName))
                  .addModifiers(PRIVATE, STATIC)
                  .addField(dependencyClassName, dependencyVariable, PRIVATE, FINAL)
                  .addMethod(
                      constructorBuilder()
                          .addParameter(dependencyClassName, dependencyVariable)
                          .addStatement("this.$1L = $1L", dependencyVariable)
                          .build())
                  .addMethod(getMethod.build())
                  .build());
          return CodeBlock.of(
              "new $L($L)",
              factoryName,
              getComponentContributionExpression(
                  ComponentRequirement.forDependency(dependencyType.asType())));
        }

      case SUBCOMPONENT_BUILDER:
        String subcomponentName =
            subcomponentNames.get(
                graph.componentDescriptor()
                    .subcomponentsByBuilderType()
                    .get(MoreTypes.asTypeElement(binding.key().type())));
        return CodeBlock.of(
            Joiner.on('\n')
                .join(
                    "new $1L<$2T>() {",
                    "  @Override public $2T get() {",
                    "    return new $3LBuilder();",
                    "  }",
                    "}"),
            // TODO(ronshapiro): Until we remove Factory, fully qualify the import so it doesn't
            // conflict with dagger.android.ActivityInjector.Factory
            /* 1 */ "dagger.internal.Factory",
            /* 2 */ bindingKeyTypeName,
            /* 3 */ subcomponentName);

      case BUILDER_BINDING:
        return CodeBlock.of(
            "$T.$L($L)",
            InstanceFactory.class,
            binding.nullableType().isPresent() ? "createNullable" : "create",
            getComponentContributionExpression(ComponentRequirement.forBinding(binding)));

      case INJECTION:
      case PROVISION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.explicitDependencies().size() + 1);
          if (binding.requiresModuleInstance()) {
            arguments.add(
                getComponentContributionExpression(
                    ComponentRequirement.forModule(binding.contributingModule().get().asType())));
          }
          arguments.addAll(getDependencyArguments(binding));

          CodeBlock factoryCreate =
              CodeBlock.of(
                  "$T.create($L)",
                  generatedClassNameForBinding(binding),
                  makeParametersCodeBlock(arguments));

          // If scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
          // type properly, so cast to a raw framework type before scoping.
          if (binding.bindingKind().equals(INJECTION)
              && binding.unresolved().isPresent()
              && binding.scope().isPresent()) {
            factoryCreate =
                CodeBlock.of("($T) $L", binding.bindingType().frameworkClass(), factoryCreate);
          }
          return decorateForScope(factoryCreate, binding.scope());
        }

      case COMPONENT_PRODUCTION:
        {
          TypeElement dependencyType = dependencyTypeForBinding(binding);
          return CodeBlock.of(
              Joiner.on('\n')
                  .join(
                      "new $1T<$2T>() {",
                      "  private final $6T $7L = $4L;",
                      "  @Override public $3T<$2T> get() {",
                      "    return $7L.$5L();",
                      "  }",
                      "}"),
              /* 1 */ PRODUCER,
              /* 2 */ TypeName.get(binding.key().type()),
              /* 3 */ LISTENABLE_FUTURE,
              /* 4 */ getComponentContributionExpression(
                  ComponentRequirement.forDependency(dependencyType.asType())),
              /* 5 */ binding.bindingElement().get().getSimpleName(),
              /* 6 */ TypeName.get(dependencyType.asType()),
              /* 7 */ simpleVariableName(dependencyType));
        }

      case PRODUCTION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.dependencies().size() + 2);
          if (binding.requiresModuleInstance()) {
            arguments.add(
                getComponentContributionExpression(
                    ComponentRequirement.forModule(binding.contributingModule().get().asType())));
          }
          arguments.addAll(getDependencyArguments(binding));

          return CodeBlock.of(
              "new $T($L)",
              generatedClassNameForBinding(binding),
              makeParametersCodeBlock(arguments));
        }

      case SYNTHETIC_MAP:
        FrameworkDependency frameworkDependency = getOnlyElement(binding.frameworkDependencies());
        return CodeBlock.of(
            "$T.create($L)",
            mapFactoryClassName(binding),
            requestFulfillmentRegistry
                .getRequestFulfillment(frameworkDependency.bindingKey())
                .getSnippetForFrameworkDependency(frameworkDependency, name));

      case SYNTHETIC_MULTIBOUND_SET:
        return initializeFactoryForSetMultibinding(binding);

      case SYNTHETIC_MULTIBOUND_MAP:
        return initializeFactoryForMapMultibinding(binding);

      case SYNTHETIC_RELEASABLE_REFERENCE_MANAGER:
        return initializeFactoryForSyntheticReleasableReferenceManagerBinding(binding);

      case SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS:
        return initializeFactoryForSyntheticSetOfReleasableReferenceManagers(binding);

      case SYNTHETIC_OPTIONAL_BINDING:
        return initializeFactoryForSyntheticOptionalBinding(binding);

      default:
        throw new AssertionError(binding);
    }
  }

  private TypeElement dependencyTypeForBinding(ContributionBinding binding) {
    return graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement().get());
  }

  private CodeBlock decorateForScope(CodeBlock factoryCreate, Optional<Scope> maybeScope) {
    if (!maybeScope.isPresent()) {
      return factoryCreate;
    }
    Scope scope = maybeScope.get();
    if (requiresReleasableReferences(scope)) {
      return CodeBlock.of(
          "$T.create($L, $L)",
          REFERENCE_RELEASING_PROVIDER,
          factoryCreate,
          getReferenceReleasingProviderManagerExpression(scope));
    } else {
      return CodeBlock.of(
          "$T.provider($L)",
          scope.equals(reusableScope(elements)) ? SINGLE_CHECK : DOUBLE_CHECK,
          factoryCreate);
    }
  }

  private CodeBlock initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    return binding.injectionSites().isEmpty()
        ? CodeBlock.of("$T.noOp()", MEMBERS_INJECTORS)
        : CodeBlock.of(
            "$T.create($L)",
            membersInjectorNameForType(binding.membersInjectedType()),
            makeParametersCodeBlock(getDependencyArguments(binding)));
  }

  /**
   * The expressions that represent factory arguments for the dependencies of a binding.
   */
  private ImmutableList<CodeBlock> getDependencyArguments(Binding binding) {
    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (FrameworkDependency frameworkDependency : binding.frameworkDependencies()) {
      parameters.add(getDependencyArgument(frameworkDependency));
    }
    return parameters.build();
  }

  /** Returns the expression to use as an argument for a dependency. */
  private CodeBlock getDependencyArgument(FrameworkDependency frameworkDependency) {
    BindingKey requestedKey = frameworkDependency.bindingKey();
    ResolvedBindings resolvedBindings = graph.resolvedBindings().get(requestedKey);
    if (resolvedBindings.frameworkClass().equals(Provider.class)
        && frameworkDependency.frameworkClass().equals(Producer.class)) {
      return producerFromProviderMemberSelects.get(requestedKey).getExpressionFor(name);
    } else {
      RequestFulfillment requestFulfillment =
          requestFulfillmentRegistry.getRequestFulfillment(requestedKey);
      return requestFulfillment.getSnippetForFrameworkDependency(frameworkDependency, name);
    }
  }

  private CodeBlock initializeFactoryForSetMultibinding(ContributionBinding binding) {
    CodeBlock.Builder builder =
        CodeBlock.builder().add("$T.", setFactoryClassName(binding.bindingType(), binding.key()));
    boolean useRawTypes = useRawType(binding);
    if (!useRawTypes) {
      SetType setType = SetType.from(binding.key());
      builder.add(
          "<$T>",
          setType.elementsAreTypeOf(Produced.class)
              ? setType.unwrappedElementType(Produced.class)
              : setType.elementType());
    }
    int individualProviders = 0;
    int setProviders = 0;
    CodeBlock.Builder builderMethodCalls = CodeBlock.builder();
    for (FrameworkDependency frameworkDependency : binding.frameworkDependencies()) {
      ContributionType contributionType =
          graph.resolvedBindings().get(frameworkDependency.bindingKey()).contributionType();
      String methodName;
      String methodNameSuffix = frameworkDependency.frameworkClass().getSimpleName();
      switch (contributionType) {
        case SET:
          individualProviders++;
          methodName = "add" + methodNameSuffix;
          break;
        case SET_VALUES:
          setProviders++;
          methodName = "addCollection" + methodNameSuffix;
          break;
        default:
          throw new AssertionError(frameworkDependency + " is not a set multibinding");
      }

      builderMethodCalls.add(
          ".$L($L)",
          methodName,
          potentiallyCast(
              useRawTypes,
              frameworkDependency.frameworkClass(),
              getDependencyArgument(frameworkDependency)));
    }
    builder.add("builder($L, $L)", individualProviders, setProviders);
    builder.add(builderMethodCalls.build());
    return builder.add(".build()").build();
  }

  private CodeBlock initializeFactoryForMapMultibinding(ContributionBinding binding) {
    ImmutableList<FrameworkDependency> frameworkDependencies = binding.frameworkDependencies();

    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    MapType mapType = MapType.from(binding.key().type());
    CodeBlock.Builder builderCall =
        CodeBlock.builder().add("$T.", frameworkMapFactoryClassName(binding.bindingType()));
    boolean useRawTypes = useRawType(binding);
    if (!useRawTypes) {
      builderCall.add("<$T, $T>", TypeName.get(mapType.keyType()),
          TypeName.get(mapType.unwrappedValueType(binding.bindingType().frameworkClass())));
    }
    builderCall.add("builder($L)", frameworkDependencies.size());
    codeBlocks.add(builderCall.build());

    for (FrameworkDependency frameworkDependency : frameworkDependencies) {
      BindingKey bindingKey = frameworkDependency.bindingKey();
      ContributionBinding contributionBinding =
          graph.resolvedBindings().get(bindingKey).contributionBinding();
      CodeBlock value =
          potentiallyCast(
              useRawTypes,
              frameworkDependency.frameworkClass(),
              getDependencyArgument(frameworkDependency));
      codeBlocks.add(
          CodeBlock.of(
              ".put($L, $L)", getMapKeyExpression(contributionBinding.mapKey().get()), value));
    }
    codeBlocks.add(CodeBlock.of(".build()"));

    return CodeBlocks.concat(codeBlocks.build());
  }

  private CodeBlock potentiallyCast(boolean shouldCast, Class<?> classToCast, CodeBlock notCasted) {
    if (!shouldCast) {
      return notCasted;
    }
    return CodeBlock.of("($T) $L", classToCast, notCasted);
  }

  /**
   * Initializes the factory for a {@link
   * ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGER} binding.
   *
   * <p>The {@code get()} method just returns the component field with the {@link
   * dagger.internal.ReferenceReleasingProviderManager} object.
   */
  private CodeBlock initializeFactoryForSyntheticReleasableReferenceManagerBinding(
      ContributionBinding binding) {
    // The scope is the value of the @ForReleasableReferences annotation.
    Scope scope = forReleasableReferencesAnnotationValue(binding.key().qualifier().get());

    CodeBlock managerExpression;
    if (MoreTypes.isTypeOf(TypedReleasableReferenceManager.class, binding.key().type())) {
      /* The key's type is TypedReleasableReferenceManager<M>, so return
       * new TypedReleasableReferenceManager(field, metadata). */
      TypeMirror metadataType =
          MoreTypes.asDeclared(binding.key().type()).getTypeArguments().get(0);
      managerExpression =
          typedReleasableReferenceManagerDecoratorExpression(
              getReferenceReleasingProviderManagerExpression(scope),
              scope.releasableReferencesMetadata(metadataType).get());
    } else {
      // The key's type is ReleasableReferenceManager, so return the field as is.
      managerExpression = getReferenceReleasingProviderManagerExpression(scope);
    }

    TypeName keyType = TypeName.get(binding.key().type());
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .addSuperinterface(providerOf(keyType))
            .addMethod(
                methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(keyType)
                    .addCode("return $L;", managerExpression)
                    .build())
            .build());
  }

  /**
   * Initializes the factory for a {@link
   * ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS} binding.
   *
   * <p>A binding for {@code Set<ReleasableReferenceManager>} will include managers for all
   * reference-releasing scopes. A binding for {@code Set<TypedReleasableReferenceManager<M>>} will
   * include managers for all reference-releasing scopes whose metadata type is {@code M}.
   */
  private CodeBlock initializeFactoryForSyntheticSetOfReleasableReferenceManagers(
      ContributionBinding binding) {
    Key key = binding.key();
    SetType keyType = SetType.from(key);
    ImmutableList.Builder<CodeBlock> managerExpressions = ImmutableList.builder();
    for (Map.Entry<Scope, MemberSelect> entry :
        referenceReleasingProviderManagerFields.entrySet()) {
      Scope scope = entry.getKey();
      CodeBlock releasableReferenceManagerExpression = entry.getValue().getExpressionFor(name);

      if (keyType.elementsAreTypeOf(ReleasableReferenceManager.class)) {
        managerExpressions.add(releasableReferenceManagerExpression);
      } else if (keyType.elementsAreTypeOf(TypedReleasableReferenceManager.class)) {
        TypeMirror metadataType =
            keyType.unwrappedElementType(TypedReleasableReferenceManager.class);
        Optional<AnnotationMirror> metadata = scope.releasableReferencesMetadata(metadataType);
        if (metadata.isPresent()) {
          managerExpressions.add(
              typedReleasableReferenceManagerDecoratorExpression(
                  releasableReferenceManagerExpression, metadata.get()));
        }
      } else {
        throw new IllegalArgumentException("inappropriate key: " + binding);
      }
    }
    TypeName keyTypeName = TypeName.get(key.type());
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .addSuperinterface(providerOf(keyTypeName))
            .addMethod(
                methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(keyTypeName)
                    .addCode(
                        "return new $T($T.asList($L));",
                        HashSet.class,
                        Arrays.class,
                        makeParametersCodeBlock(managerExpressions.build()))
                    .build())
            .build());
  }

  /**
   * Returns an expression that evaluates to a {@link TypedReleasableReferenceManagerDecorator} that
   * decorates the {@code managerExpression} to supply {@code metadata}.
   */
  private CodeBlock typedReleasableReferenceManagerDecoratorExpression(
      CodeBlock managerExpression, AnnotationMirror metadata) {
    return CodeBlock.of(
        "new $T($L, $L)",
        ParameterizedTypeName.get(
            TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR,
            TypeName.get(metadata.getAnnotationType())),
        managerExpression,
        new AnnotationExpression(metadata).getAnnotationInstanceExpression());
  }

  private Scope forReleasableReferencesAnnotationValue(AnnotationMirror annotation) {
    checkArgument(
        MoreTypes.isTypeOf(ForReleasableReferences.class, annotation.getAnnotationType()));
    return Scope.scope(
        MoreElements.asType(MoreTypes.asDeclared(getTypeValue(annotation, "value")).asElement()));
  }

  /**
   * Returns an expression that initializes a {@link Provider} or {@link Producer} for an optional
   * binding.
   */
  private CodeBlock initializeFactoryForSyntheticOptionalBinding(ContributionBinding binding) {
    if (binding.explicitDependencies().isEmpty()) {
      verify(
          binding.bindingType().equals(BindingType.PROVISION),
          "Absent optional bindings should be provisions: %s",
          binding);
      return optionalFactories.absentOptionalProvider(binding);
    } else {
      return optionalFactories.presentOptionalFactory(
          binding, getOnlyElement(getDependencyArguments(binding)));
    }
  }

  /**
   * Initialization state for a factory field.
   */
  enum InitializationState {
    /** The field is {@code null}. */
    UNINITIALIZED,

    /** The field is set to a {@link DelegateFactory}. */
    DELEGATED,

    /** The field is set to an undelegated factory. */
    INITIALIZED;
  }
}
