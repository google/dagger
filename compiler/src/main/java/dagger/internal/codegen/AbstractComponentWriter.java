/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
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
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.DelegateFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.Preconditions;
import dagger.internal.SetFactory;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.MapOfProducerProducer;
import dagger.producers.internal.MapProducer;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.DELEGATED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.INITIALIZED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.FrameworkDependency.frameworkDependenciesForBinding;
import static dagger.internal.codegen.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.MemberSelect.emptyFrameworkMapFactory;
import static dagger.internal.codegen.MemberSelect.emptySetProvider;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.MemberSelect.noOpMembersInjector;
import static dagger.internal.codegen.MemberSelect.staticMethod;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.Scope.reusableScope;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.FACTORY;
import static dagger.internal.codegen.TypeNames.ILLEGAL_STATE_EXCEPTION;
import static dagger.internal.codegen.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.TypeNames.LISTENABLE_FUTURE;
import static dagger.internal.codegen.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static dagger.internal.codegen.TypeNames.PRODUCERS;
import static dagger.internal.codegen.TypeNames.SET_FACTORY;
import static dagger.internal.codegen.TypeNames.SET_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.SET_PRODUCER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.TypeNames.STRING;
import static dagger.internal.codegen.TypeNames.UNSUPPORTED_OPERATION_EXCEPTION;
import static dagger.internal.codegen.TypeNames.providerOf;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.requiresAPassedInstance;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Creates the implementation class for a component or subcomponent.
 */
abstract class AbstractComponentWriter {
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
  protected TypeSpec.Builder component;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final Map<BindingKey, MemberSelect> memberSelects = new HashMap<>();
  private final Map<BindingKey, MemberSelect> producerFromProviderMemberSelects = new HashMap<>();
  protected final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  protected Optional<ClassName> builderName = Optional.absent();

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private ImmutableMap<TypeElement, FieldSpec> builderFields = ImmutableMap.of();

  /**
   * For each component requirement, the member select for the component field that holds it.
   *
   * <p>Fields are written for all requirements for subcomponents that do not use a builder, and for
   * any requirement that is reused from a subcomponent of this component.
   */
  protected final Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();

  AbstractComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentNames) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.name = name;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
  }

  protected final TypeElement componentDefinitionType() {
    return graph.componentDescriptor().componentDefinitionType();
  }

  protected final ClassName componentDefinitionTypeName() {
    return ClassName.get(componentDefinitionType());
  }

  /**
   * Returns an expression that evaluates to an instance of the contribution, looking for either a
   * builder field or a component field.
   */
  private CodeBlock getComponentContributionExpression(TypeElement contributionType) {
    if (builderFields.containsKey(contributionType)) {
      return CodeBlock.of("builder.$N", builderFields.get(contributionType));
    } else {
      Optional<CodeBlock> codeBlock =
          getOrCreateComponentContributionFieldExpression(contributionType);
      checkState(codeBlock.isPresent(), "no builder or component field for %s", contributionType);
      return codeBlock.get();
    }
  }

  /**
   * Returns an expression for a component contribution field. Adds a field the first time one is
   * requested for a contribution type if this component's builder has a field for it.
   */
  protected Optional<CodeBlock> getOrCreateComponentContributionFieldExpression(
      TypeElement contributionType) {
    MemberSelect fieldSelect = componentContributionFields.get(contributionType);
    if (fieldSelect == null) {
      if (!builderFields.containsKey(contributionType)) {
        return Optional.absent();
      }
      FieldSpec componentField =
          componentField(ClassName.get(contributionType), simpleVariableName(contributionType))
              .addModifiers(PRIVATE, FINAL)
              .build();
      component.addField(componentField);
      constructor.addCode(
          "this.$N = builder.$N;", componentField, builderFields.get(contributionType));
      fieldSelect = localField(name, componentField.name);
      componentContributionFields.put(contributionType, fieldSelect);
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

  protected MemberSelect getMemberSelect(BindingKey key) {
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
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  final TypeSpec.Builder write() {
    checkState(component == null, "ComponentWriter has already been generated.");
    component = createComponentClass();
    addBuilder();
    addFactoryMethods();
    addFields();
    initializeFrameworkTypes();
    implementInterfaceMethods();
    addSubcomponents();
    component.addMethod(constructor.build());
    return component;
  }

  /**
   * Creates the component implementation class.
   */
  protected abstract TypeSpec.Builder createComponentClass();

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
  private ImmutableMap<TypeElement, FieldSpec> addBuilderFields(TypeSpec.Builder componentBuilder) {
    UniqueNameSet builderFieldNames = new UniqueNameSet();
    ImmutableMap.Builder<TypeElement, FieldSpec> builderFields = ImmutableMap.builder();
    for (TypeElement contributionElement : graph.componentRequirements()) {
      String contributionName =
          builderFieldNames.getUniqueName(simpleVariableName(contributionElement));
      FieldSpec builderField =
          FieldSpec.builder(ClassName.get(contributionElement), contributionName, PRIVATE).build();
      componentBuilder.addField(builderField);
      builderFields.put(contributionElement, builderField);
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

    for (Map.Entry<TypeElement, FieldSpec> builderFieldEntry : builderFields.entrySet()) {
      FieldSpec builderField = builderFieldEntry.getValue();
      if (componentCanMakeNewInstances(builderFieldEntry.getKey())) {
        buildMethod.addCode(
            "if ($1N == null) { this.$1N = new $2T(); }", builderField, builderField.type);
      } else if (requiresAPassedInstance(elements, builderFieldEntry.getKey())) {
        buildMethod.addCode(
            "if ($N == null) { throw new $T($T.class.getCanonicalName() + $S); }",
            builderField,
            ILLEGAL_STATE_EXCEPTION,
            builderField.type,
            " must be set");
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
    if (builderSpec.isPresent()) {
      UniqueNameSet parameterNames = new UniqueNameSet();
      for (Map.Entry<TypeElement, ExecutableElement> builderMethodEntry :
          builderSpec.get().methodMap().entrySet()) {
        TypeElement builderMethodType = builderMethodEntry.getKey();
        ExecutableElement specMethod = builderMethodEntry.getValue();
        MethodSpec.Builder builderMethod = addBuilderMethodFromSpec(specMethod);
        String parameterName =
            parameterNames.getUniqueName(
                Iterables.getOnlyElement(specMethod.getParameters()).getSimpleName());
        builderMethod.addParameter(ClassName.get(builderMethodType), parameterName);
        if (graph.componentRequirements().contains(builderMethodType)) {
          // required type
          builderMethod.addStatement(
              "this.$N = $T.checkNotNull($L)",
              builderFields.get(builderMethodType),
              Preconditions.class,
              parameterName);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else if (graph.ownedModuleTypes().contains(builderMethodType)) {
          // owned, but not required
          builderMethod.addJavadoc(NOOP_BUILDER_METHOD_JAVADOC);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else {
          // neither owned nor required, so it must be an inherited module
          builderMethod.addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UNSUPPORTED_OPERATION_EXCEPTION,
              STRING,
              "%s cannot be set because it is inherited from the enclosing component",
              ClassName.get(builderMethodType));
        }
        componentBuilder.addMethod(builderMethod.build());
      }
    } else {
      for (TypeElement componentRequirement : graph.availableDependencies()) {
        String componentRequirementName = simpleVariableName(componentRequirement);
        MethodSpec.Builder builderMethod =
            methodBuilder(componentRequirementName)
                .returns(builderName.get())
                .addModifiers(PUBLIC)
                .addParameter(ClassName.get(componentRequirement), componentRequirementName);
        if (graph.componentRequirements().contains(componentRequirement)) {
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

  private void addFields() {
    for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
      addField(resolvedBindings);
    }
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

    FieldSpec frameworkField = addFrameworkField(resolvedBindings, Optional.<BindingType>absent());
    memberSelects.put(
        bindingKey,
        localField(name, frameworkField.name));
  }

  /**
   * Adds a field representing the resolved bindings, optionally forcing it to use a particular
   * binding type (instead of the type the resolved bindings would typically use).
   */
  private FieldSpec addFrameworkField(
      ResolvedBindings resolvedBindings, Optional<BindingType> bindingType) {
    boolean useRawType = useRawType(resolvedBindings);

    FrameworkField contributionBindingField =
        FrameworkField.createForResolvedBindings(resolvedBindings, bindingType);
    FieldSpec.Builder contributionField =
        componentField(
            useRawType
                ? contributionBindingField.frameworkType().rawType
                : contributionBindingField.frameworkType(),
            contributionBindingField.name());
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.addAnnotation(AnnotationSpecs.SUPPRESS_WARNINGS_RAWTYPES);
    }

    FieldSpec field = contributionField.build();
    component.addField(field);
    return field;
  }

  private boolean useRawType(ResolvedBindings resolvedBindings) {
    Optional<String> bindingPackage = resolvedBindings.bindingPackage();
    return bindingPackage.isPresent() && !bindingPackage.get().equals(name.packageName());
  }

  /**
   * If {@code resolvedBindings} is an unscoped provision binding with no factory arguments or a
   * no-op members injection binding, then we don't need a field to hold its factory. In that case,
   * this method returns the static member select that returns the factory or no-op members
   * injector.
   */
  private Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
        if (contributionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
            && !contributionBinding.scope().isPresent()) {
          switch (contributionBinding.bindingKind()) {
            case SYNTHETIC_MULTIBOUND_MAP:
              BindingType bindingType = contributionBinding.bindingType();
              MapType mapType = MapType.from(contributionBinding.key().type());
              return Optional.of(
                  emptyFrameworkMapFactory(
                      frameworkMapFactoryClassName(bindingType),
                      mapType.keyType(),
                      mapType.unwrappedValueType(bindingType.frameworkClass())));

            case SYNTHETIC_MULTIBOUND_SET:
              return Optional.of(
                  emptySetFactoryStaticMemberSelect(
                      contributionBinding.bindingType(), contributionBinding.key()));

            default:
              return Optional.of(
                  staticMethod(
                      generatedClassNameForBinding(contributionBinding),
                      CodeBlock.of("create()")));
          }
        }
        break;

      case MEMBERS_INJECTION:
        Optional<MembersInjectionBinding> membersInjectionBinding =
            resolvedBindings.membersInjectionBinding();
        if (membersInjectionBinding.isPresent()
            && membersInjectionBinding.get().injectionStrategy().equals(NO_OP)) {
          return Optional.of(noOpMembersInjector(membersInjectionBinding.get().key().type()));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.absent();
  }

  /**
   * A static member select for an empty set factory. Calls
   * {@link SetFactory#create(javax.inject.Provider...)},
   * {@link SetProducer#create(dagger.producers.Producer...)}, or
   * {@link SetOfProducedProducer#create(dagger.producers.Producer...)}, depending on the set
   * bindings.
   */
  private static MemberSelect emptySetFactoryStaticMemberSelect(BindingType bindingType, Key key) {
    return emptySetProvider(setFactoryClassName(bindingType, key), SetType.from(key.type()));
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
      SetType setType = SetType.from(key.type());
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
        return MapType.from(binding.key().type()).valuesAreTypeOf(Produced.class)
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
        ExecutableElement requestElement =
            MoreElements.asExecutable(interfaceRequest.requestElement());
        ExecutableType requestType = MoreTypes.asExecutable(types.asMemberOf(
            MoreTypes.asDeclared(componentDefinitionType().asType()), requestElement));
        MethodSignature signature = MethodSignature.fromExecutableType(
            requestElement.getSimpleName().toString(), requestType);
        if (!interfaceMethods.contains(signature)) {
          interfaceMethods.add(signature);
          MethodSpec.Builder interfaceMethod =
              methodBuilder(requestElement.getSimpleName().toString())
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .returns(TypeName.get(requestType.getReturnType()));
          BindingKey bindingKey = interfaceRequest.bindingKey();
          MemberSelect memberSelect = getMemberSelect(bindingKey);
          CodeBlock memberSelectCodeBlock = memberSelect.getExpressionFor(name);
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                // we're returning the framework type
                interfaceMethod.addStatement("return $L", memberSelectCodeBlock);
              } else {
                Name parameterName = Iterables.getOnlyElement(parameters).getSimpleName();
                interfaceMethod.addParameter(
                    TypeName.get(Iterables.getOnlyElement(requestType.getParameterTypes())),
                    parameterName.toString());
                interfaceMethod.addStatement(
                    "$L.injectMembers($L)", memberSelectCodeBlock, parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.addStatement("return $L", parameterName);
                }
              }
              break;
            case INSTANCE:
              if (memberSelect.staticMember()
                  && bindingKey.key().type().getKind().equals(DECLARED)
                  && !((DeclaredType) bindingKey.key().type()).getTypeArguments().isEmpty()) {
                // If using a parameterized enum type, then we need to store the factory
                // in a temporary variable, in order to help javac be able to infer
                // the generics of the Factory.create methods.
                TypeName factoryType = providerOf(TypeName.get(requestType.getReturnType()));
                interfaceMethod
                    .addStatement("$T factory = $L", factoryType, memberSelectCodeBlock)
                    .addStatement("return factory.get()");
                break;
              }
              // fall through in the else case.
            case LAZY:
            case PRODUCED:
            case PRODUCER:
            case PROVIDER:
            case PROVIDER_OF_LAZY:
            case FUTURE:
              interfaceMethod.addStatement(
                  "return $L",
                  frameworkTypeUsageStatement(memberSelectCodeBlock, interfaceRequest.kind()));
              break;
            default:
              throw new AssertionError();
          }
          component.addMethod(interfaceMethod.build());
        }
      }
    }
  }

  private void addSubcomponents() {
    for (Map.Entry<ExecutableElement, BindingGraph> subgraphEntry : graph.subgraphs().entrySet()) {
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, subgraphEntry.getKey(), subgraphEntry.getValue());
      component.addType(subcomponent.write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void initializeFrameworkTypes() {
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    for (BindingKey bindingKey : graph.resolvedBindings().keySet()) {
      codeBlocks.addAll(initializeFrameworkType(bindingKey).asSet());
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
              .addAnnotation(SUPPRESS_WARNINGS_UNCHECKED)
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
      return Optional.absent();
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
    switch (binding.factoryCreationStrategy()) {
      case DELEGATE:
        CodeBlock delegatingCodeBlock = CodeBlock.of(
            "($T) $L",
            binding.frameworkClass(),
            getMemberSelect(
                Iterables.getOnlyElement(binding.dependencies()).bindingKey())
                    .getExpressionFor(name));
        return Optional.of(
            initializeMember(
                bindingKey,
                binding.scope().isPresent()
                    ? decorateForScope(delegatingCodeBlock, binding.scope().get())
                    : delegatingCodeBlock));
      case ENUM_INSTANCE:
        if (!binding.scope().isPresent()) {
          return Optional.absent();
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

    if (binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
      return Optional.absent();
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
        FluentIterable.from(binding.implicitDependencies())
            .transform(DependencyRequest.BINDING_KEY_FUNCTION)
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
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
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
            addFrameworkField(resolvedBindings, Optional.of(BindingType.PRODUCTION));
        memberSelect = localField(name, frameworkField.name);
        producerFromProviderMemberSelects.put(frameworkDependency.bindingKey(), memberSelect);
        initializations.add(
            CodeBlock.of(
                "this.$L = $T.producerFromProvider($L);",
                memberSelect.getExpressionFor(name),
                PRODUCERS,
                getMemberSelectExpression(frameworkDependency.bindingKey())));
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
                    MoreTypes.asTypeElement(binding.key().type())));

      case COMPONENT_PROVISION:
        {
          TypeElement bindingTypeElement =
              graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
          String localFactoryVariable = simpleVariableName(bindingTypeElement);
          CodeBlock callFactoryMethod =
              CodeBlock.of(
                  "$L.$L()",
                  localFactoryVariable,
                  binding.bindingElement().getSimpleName().toString());
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
                  : CodeBlock.of("return $T.checkNotNull($L, $S);",
                      Preconditions.class,
                      callFactoryMethod,
                      CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          return CodeBlock.of(
              Joiner.on('\n')
                  .join(
                      "new $1T<$2T>() {",
                      "  private final $5T $6L = $3L;",
                      "  $4L@Override public $2T get() {",
                      "    $7L",
                      "  }",
                      "}"),
              /* 1 */ FACTORY,
              /* 2 */ bindingKeyTypeName,
              /* 3 */ getComponentContributionExpression(bindingTypeElement),
              /* 4 */ nullableAnnotation(binding.nullableType()),
              /* 5 */ TypeName.get(bindingTypeElement.asType()),
              /* 6 */ localFactoryVariable,
              /* 7 */ getMethodBody);
        }

      case SUBCOMPONENT_BUILDER:
        return CodeBlock.of(
            Joiner.on('\n')
                .join(
                    "new $1T<$2T>() {",
                    "  @Override public $2T get() {",
                    "    return $3L();",
                    "  }",
                    "}"),
            /* 1 */ FACTORY,
            /* 2 */ bindingKeyTypeName,
            /* 3 */ binding.bindingElement().getSimpleName().toString());

      case INJECTION:
      case PROVISION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
          if (binding.bindingKind().equals(PROVISION)
              && !binding.bindingElement().getModifiers().contains(STATIC)) {
            arguments.add(getComponentContributionExpression(binding.contributedBy().get()));
          }
          arguments.addAll(getDependencyArguments(binding));

          CodeBlock factoryCreate =
              CodeBlock.of(
                  "$T.create($L)",
                  generatedClassNameForBinding(binding),
                  makeParametersCodeBlock(arguments));
          return binding.scope().isPresent()
              ? decorateForScope(factoryCreate, binding.scope().get())
              : factoryCreate;
        }

      case COMPONENT_PRODUCTION:
        {
          TypeElement bindingTypeElement =
              graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
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
              /* 4 */ getComponentContributionExpression(bindingTypeElement),
              /* 5 */ binding.bindingElement().getSimpleName().toString(),
              /* 6 */ TypeName.get(bindingTypeElement.asType()),
              /* 7 */ simpleVariableName(bindingTypeElement));
        }

      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.implicitDependencies().size() + 2);
          if (!binding.bindingElement().getModifiers().contains(STATIC)) {
            arguments.add(getComponentContributionExpression(binding.bindingTypeElement()));
          }
          arguments.addAll(getDependencyArguments(binding));

          return CodeBlock.of(
              "new $T($L)",
              generatedClassNameForBinding(binding),
              makeParametersCodeBlock(arguments));
        }

      case SYNTHETIC_MAP:
        return CodeBlock.of(
            "$T.create($L)",
            mapFactoryClassName(binding),
            getMemberSelectExpression(getOnlyElement(binding.dependencies()).bindingKey()));

      case SYNTHETIC_MULTIBOUND_SET:
        return initializeFactoryForSetMultibinding(binding);

      case SYNTHETIC_MULTIBOUND_MAP:
        return initializeFactoryForMapMultibinding(binding);

      default:
        throw new AssertionError(binding.toString());
    }
  }

  private CodeBlock decorateForScope(CodeBlock factoryCreate, Scope scope) {
    return CodeBlock.of(
        "$T.provider($L)",
        scope.equals(reusableScope(elements)) ? SINGLE_CHECK : DOUBLE_CHECK,
        factoryCreate);
  }

  private CodeBlock nullableAnnotation(Optional<DeclaredType> nullableType) {
    return nullableType.isPresent()
        ? CodeBlock.of("@$T ", TypeName.get(nullableType.get()))
        : CodeBlock.of("");
  }

  private CodeBlock initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return CodeBlock.of("$T.noOp()", MEMBERS_INJECTORS);
      case INJECT_MEMBERS:
        return CodeBlock.of(
            "$T.create($L)",
            membersInjectorNameForType(binding.bindingElement()),
            makeParametersCodeBlock(getDependencyArguments(binding)));
      default:
        throw new AssertionError();
    }
  }

  /**
   * The expressions that represent factory arguments for the dependencies of a binding.
   */
  private ImmutableList<CodeBlock> getDependencyArguments(
      Binding binding) {
    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
      parameters.add(getDependencyArgument(frameworkDependency));
    }
    return parameters.build();
  }

  /**
   * The expression to use as an argument for a dependency.
   */
  private CodeBlock getDependencyArgument(FrameworkDependency frameworkDependency) {
    BindingKey requestedKey = frameworkDependency.bindingKey();
    ResolvedBindings resolvedBindings = graph.resolvedBindings().get(requestedKey);
    if (resolvedBindings.frameworkClass().equals(Provider.class)
        && frameworkDependency.frameworkClass().equals(Producer.class)) {
      return producerFromProviderMemberSelects.get(requestedKey).getExpressionFor(name);
    } else {
      return getMemberSelectExpression(requestedKey);
    }
  }

  private CodeBlock initializeFactoryForSetMultibinding(ContributionBinding binding) {
    return CodeBlock.of(
        "$T.create($L)",
        setFactoryClassName(binding.bindingType(), binding.key()),
        makeParametersCodeBlock(getDependencyArguments(binding)));
  }

  private CodeBlock initializeFactoryForMapMultibinding(ContributionBinding binding) {
    ImmutableSet<FrameworkDependency> frameworkDependencies =
        FrameworkDependency.frameworkDependenciesForBinding(binding);

    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    MapType mapType = MapType.from(binding.key().type());
    codeBlocks.add(
        CodeBlock.of(
            "$T.<$T, $T>builder($L)",
            frameworkMapFactoryClassName(binding.bindingType()),
            TypeName.get(mapType.keyType()),
            TypeName.get(
                mapType.unwrappedValueType(binding.bindingType().frameworkClass())),
            frameworkDependencies.size()));

    for (FrameworkDependency frameworkDependency : frameworkDependencies) {
      BindingKey bindingKey = frameworkDependency.bindingKey();
      ContributionBinding contributionBinding =
          graph.resolvedBindings().get(bindingKey).contributionBinding();
      codeBlocks.add(
          CodeBlock.of(
              ".put($L, $L)",
              getMapKeyExpression(contributionBinding.bindingElement()),
              getDependencyArgument(frameworkDependency)));
    }
    codeBlocks.add(CodeBlock.of(".build()"));

    return CodeBlocks.concat(codeBlocks.build());
  }

  private static String simpleVariableName(TypeElement typeElement) {
    return UPPER_CAMEL.to(LOWER_CAMEL, typeElement.getSimpleName().toString());
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
