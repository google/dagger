/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

/** Factory for creating {@link ComponentCreatorImplementation} instances. */
final class ComponentCreatorImplementationFactory {

  private final DaggerElements elements;
  private final DaggerTypes types;

  @Inject
  ComponentCreatorImplementationFactory(DaggerElements elements, DaggerTypes types) {
    this.elements = elements;
    this.types = types;
  }

  /** Returns a new creator implementation for the given component, if necessary. */
  Optional<ComponentCreatorImplementation> create(
      ComponentImplementation componentImplementation, Optional<BindingGraph> graph) {
    if (!componentImplementation.componentDescriptor().hasCreator()) {
      return Optional.empty();
    }

    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        componentImplementation.componentDescriptor().creatorDescriptor();

    Builder builder =
        creatorDescriptor.isPresent()
            ? new BuilderForCreatorDescriptor(
                componentImplementation, creatorDescriptor.get(), graph)
            : new BuilderForGeneratedRootComponentBuilder(componentImplementation);
    return Optional.of(builder.build());
  }

  /** Base class for building a creator implementation. */
  private abstract class Builder {
    final ComponentImplementation componentImplementation;
    final ClassName className;
    final TypeSpec.Builder classBuilder;

    private ImmutableMap<ComponentRequirement, FieldSpec> fields;

    Builder(ComponentImplementation componentImplementation) {
      this.componentImplementation = componentImplementation;
      this.className = componentImplementation.getCreatorName();
      this.classBuilder = classBuilder(className);
    }

    /** Builds the {@link ComponentCreatorImplementation}. */
    ComponentCreatorImplementation build() {
      setModifiers();
      setSupertype();
      this.fields = addFields();
      addConstructor();
      addSetterMethods();
      addFactoryMethod();
      return ComponentCreatorImplementation.create(classBuilder.build(), className, fields);
    }

    /** Returns the descriptor for the component. */
    final ComponentDescriptor componentDescriptor() {
      return componentImplementation.componentDescriptor();
    }

    /**
     * The set of requirements that must be passed to the component's constructor in the order
     * they must be passed.
     */
    final ImmutableSet<ComponentRequirement> componentConstructorRequirements() {
      return componentImplementation.graph().componentRequirements();
    }

    /** Returns the requirements that have setter methods on the creator type. */
    abstract ImmutableSet<ComponentRequirement> setterMethods();

    /**
     * Returns the component requirements that have factory method parameters, mapped to the name
     * for that parameter.
     */
    abstract ImmutableMap<ComponentRequirement, String> factoryMethodParameters();

    /**
     * The {@link ComponentRequirement}s that this creator allows users to set. Values are a status
     * for each requirement indicating what's needed for that requirement in the implementation
     * class currently being generated.
     */
    abstract ImmutableMap<ComponentRequirement, RequirementStatus> userSettableRequirements();

    /**
     * Component requirements that are both settable by the creator and needed to construct the
     * component.
     */
    private Set<ComponentRequirement> neededUserSettableRequirements() {
      return Sets.intersection(
          userSettableRequirements().keySet(), componentConstructorRequirements());
    }

    private void setModifiers() {
      visibility().ifPresent(classBuilder::addModifiers);
      if (!componentImplementation.isNested()) {
        classBuilder.addModifiers(STATIC);
      }
      classBuilder.addModifiers(FINAL);
    }

    /** Returns the visibility modifier the generated class should have, if any. */
    protected abstract Optional<Modifier> visibility();

    /** Sets the superclass being extended or interface being implemented for this creator. */
    protected abstract void setSupertype();

    /** Adds a constructor for the creator type, if needed. */
    protected abstract void addConstructor();

    private ImmutableMap<ComponentRequirement, FieldSpec> addFields() {
      // Fields in an abstract creator class need to be visible from subclasses.
      UniqueNameSet fieldNames = new UniqueNameSet();
      ImmutableMap<ComponentRequirement, FieldSpec> result =
          Maps.toMap(
              Sets.intersection(neededUserSettableRequirements(), setterMethods()),
              requirement ->
                  FieldSpec.builder(
                          TypeName.get(requirement.type()),
                          fieldNames.getUniqueName(requirement.variableName()),
                          PRIVATE)
                      .build());
      classBuilder.addFields(result.values());
      return result;
    }

    private void addSetterMethods() {
      Maps.filterKeys(userSettableRequirements(), setterMethods()::contains)
          .forEach(
              (requirement, status) ->
                  createSetterMethod(requirement, status).ifPresent(classBuilder::addMethod));
    }

    /** Creates a new setter method builder, with no method body, for the given requirement. */
    protected abstract MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement);

    private Optional<MethodSpec> createSetterMethod(
        ComponentRequirement requirement, RequirementStatus status) {
      switch (status) {
        case NEEDED:
          return Optional.of(normalSetterMethod(requirement));
        case UNNEEDED:
          return Optional.of(noopSetterMethod(requirement));
        case UNSETTABLE_REPEATED_MODULE:
          return Optional.of(repeatedModuleSetterMethod(requirement));
      }
      throw new AssertionError();
    }

    private MethodSpec normalSetterMethod(ComponentRequirement requirement) {
      MethodSpec.Builder method = setterMethodBuilder(requirement);
      ParameterSpec parameter = parameter(method.build());
      method.addStatement(
          "this.$N = $L",
          fields.get(requirement),
          requirement.nullPolicy(elements, types).equals(NullPolicy.ALLOW)
              ? CodeBlock.of("$N", parameter)
              : CodeBlock.of("$T.checkNotNull($N)", Preconditions.class, parameter));
      return maybeReturnThis(method);
    }

    private MethodSpec noopSetterMethod(ComponentRequirement requirement) {
      MethodSpec.Builder method = setterMethodBuilder(requirement);
      ParameterSpec parameter = parameter(method.build());
      method
          .addAnnotation(Deprecated.class)
          .addJavadoc(
              "@deprecated This module is declared, but an instance is not used in the component. "
                  + "This method is a no-op. For more, see https://dagger.dev/unused-modules.\n")
          .addStatement("$T.checkNotNull($N)", Preconditions.class, parameter);
      return maybeReturnThis(method);
    }

    private MethodSpec repeatedModuleSetterMethod(ComponentRequirement requirement) {
      return setterMethodBuilder(requirement)
          .addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UnsupportedOperationException.class,
              String.class,
              "%s cannot be set because it is inherited from the enclosing component",
              TypeNames.rawTypeName(TypeName.get(requirement.type())))
          .build();
    }

    private ParameterSpec parameter(MethodSpec method) {
      return getOnlyElement(method.parameters);
    }

    private MethodSpec maybeReturnThis(MethodSpec.Builder method) {
      MethodSpec built = method.build();
      return built.returnType.equals(TypeName.VOID)
          ? built
          : method.addStatement("return this").build();
    }

    private void addFactoryMethod() {
      classBuilder.addMethod(factoryMethod());
    }

    MethodSpec factoryMethod() {
      MethodSpec.Builder factoryMethod = factoryMethodBuilder();
      factoryMethod
          .returns(ClassName.get(componentDescriptor().typeElement()))
          .addModifiers(PUBLIC);

      ImmutableMap<ComponentRequirement, String> factoryMethodParameters =
          factoryMethodParameters();
      userSettableRequirements()
          .keySet()
          .forEach(
              requirement -> {
                if (fields.containsKey(requirement)) {
                  FieldSpec field = fields.get(requirement);
                  addNullHandlingForField(requirement, field, factoryMethod);
                } else if (factoryMethodParameters.containsKey(requirement)) {
                  String parameterName = factoryMethodParameters.get(requirement);
                  addNullHandlingForParameter(requirement, parameterName, factoryMethod);
                }
              });
      factoryMethod.addStatement(
          "return new $T($L)",
          componentImplementation.name(),
          componentConstructorArgs(factoryMethodParameters));
      return factoryMethod.build();
    }

    private void addNullHandlingForField(
        ComponentRequirement requirement, FieldSpec field, MethodSpec.Builder factoryMethod) {
      switch (requirement.nullPolicy(elements, types)) {
        case NEW:
          checkState(requirement.kind().isModule());
          factoryMethod
              .beginControlFlow("if ($N == null)", field)
              .addStatement("this.$N = $L", field, newModuleInstance(requirement))
              .endControlFlow();
          break;
        case THROW:
          // TODO(cgdecker,ronshapiro): ideally this should use the key instead of a class for
          // @BindsInstance requirements, but that's not easily proguardable.
          factoryMethod.addStatement(
              "$T.checkBuilderRequirement($N, $T.class)",
              Preconditions.class,
              field,
              TypeNames.rawTypeName(field.type));
          break;
        case ALLOW:
          break;
      }
    }

    private void addNullHandlingForParameter(
        ComponentRequirement requirement, String parameter, MethodSpec.Builder factoryMethod) {
      if (!requirement.nullPolicy(elements, types).equals(NullPolicy.ALLOW)) {
        // Factory method parameters are always required unless they are a nullable
        // binds-instance (i.e. ALLOW)
        factoryMethod.addStatement("$T.checkNotNull($L)", Preconditions.class, parameter);
      }
    }

    /** Returns a builder for the creator's factory method. */
    protected abstract MethodSpec.Builder factoryMethodBuilder();

    private CodeBlock componentConstructorArgs(
        ImmutableMap<ComponentRequirement, String> factoryMethodParameters) {
      return componentConstructorRequirements().stream()
          .map(
              requirement -> {
                if (fields.containsKey(requirement)) {
                  return CodeBlock.of("$N", fields.get(requirement));
                } else if (factoryMethodParameters.containsKey(requirement)) {
                  return CodeBlock.of("$L", factoryMethodParameters.get(requirement));
                } else {
                  return newModuleInstance(requirement);
                }
              })
          .collect(toParametersCodeBlock());
    }

    private CodeBlock newModuleInstance(ComponentRequirement requirement) {
      checkArgument(requirement.kind().isModule()); // this should be guaranteed to be true here
      return ModuleProxies.newModuleInstance(requirement.typeElement(), className, elements);
    }
  }

  /** Builder for a creator type defined by a {@code ComponentCreatorDescriptor}. */
  private final class BuilderForCreatorDescriptor extends Builder {
    final ComponentCreatorDescriptor creatorDescriptor;
    private final Optional<BindingGraph> graph;

    BuilderForCreatorDescriptor(
        ComponentImplementation componentImplementation,
        ComponentCreatorDescriptor creatorDescriptor,
        Optional<BindingGraph> graph) {
      super(componentImplementation);
      this.creatorDescriptor = creatorDescriptor;
      this.graph = graph;
    }

    @Override
    protected ImmutableMap<ComponentRequirement, RequirementStatus> userSettableRequirements() {
      return Maps.toMap(creatorDescriptor.userSettableRequirements(), this::requirementStatus);
    }

    @Override
    protected Optional<Modifier> visibility() {
      return Optional.of(PRIVATE);
    }

    @Override
    protected void setSupertype() {
      addSupertype(classBuilder, creatorDescriptor.typeElement());
    }

    @Override
    protected void addConstructor() {
      // Just use the implicit no-arg public constructor.
    }

    @Override
    protected ImmutableSet<ComponentRequirement> setterMethods() {
      return ImmutableSet.copyOf(creatorDescriptor.setterMethods().keySet());
    }

    @Override
    protected ImmutableMap<ComponentRequirement, String> factoryMethodParameters() {
      return ImmutableMap.copyOf(
          Maps.transformValues(
              creatorDescriptor.factoryParameters(),
              element -> element.getSimpleName().toString()));
    }

    private DeclaredType creatorType() {
      return asDeclared(creatorDescriptor.typeElement().asType());
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      return MethodSpec.overriding(creatorDescriptor.factoryMethod(), creatorType(), types);
    }

    private RequirementStatus requirementStatus(ComponentRequirement requirement) {
      if (isRepeatedModule(requirement)) {
        return RequirementStatus.UNSETTABLE_REPEATED_MODULE;
      }

      return componentConstructorRequirements().contains(requirement)
          ? RequirementStatus.NEEDED
          : RequirementStatus.UNNEEDED;
    }

    /**
     * Returns whether the given requirement is for a repeat of a module inherited from an ancestor
     * component. This creator is not allowed to set such a module.
     */
    final boolean isRepeatedModule(ComponentRequirement requirement) {
      return !componentConstructorRequirements().contains(requirement)
          && !isOwnedModule(requirement);
    }

    /**
     * Returns whether the given {@code requirement} is for a module type owned by the component.
     */
    private boolean isOwnedModule(ComponentRequirement requirement) {
      return graph.map(g -> g.ownedModuleTypes().contains(requirement.typeElement())).orElse(true);
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      ExecutableElement supertypeMethod = creatorDescriptor.setterMethods().get(requirement);
      MethodSpec.Builder method = MethodSpec.overriding(supertypeMethod, creatorType(), types);
      if (!supertypeMethod.getReturnType().getKind().equals(TypeKind.VOID)) {
        // Take advantage of covariant returns so that we don't have to worry about type variables
        method.returns(className);
      }
      return method;
    }
  }

  /**
   * Builder for a component builder class that is automatically generated for a root component that
   * does not have its own user-defined creator type (i.e. a {@code ComponentCreatorDescriptor}).
   */
  private final class BuilderForGeneratedRootComponentBuilder extends Builder {
    BuilderForGeneratedRootComponentBuilder(ComponentImplementation componentImplementation) {
      super(componentImplementation);
    }

    @Override
    protected ImmutableMap<ComponentRequirement, RequirementStatus> userSettableRequirements() {
      return Maps.toMap(
          setterMethods(),
          requirement ->
              componentConstructorRequirements().contains(requirement)
                  ? RequirementStatus.NEEDED
                  : RequirementStatus.UNNEEDED);
    }

    @Override
    protected Optional<Modifier> visibility() {
      return componentImplementation
          .componentDescriptor()
          .typeElement()
          .getModifiers()
          .contains(PUBLIC) ? Optional.of(PUBLIC) : Optional.empty();
    }

    @Override
    protected void setSupertype() {
      // There's never a supertype for a root component auto-generated builder type.
    }

    @Override
    protected void addConstructor() {
      classBuilder.addMethod(constructorBuilder().addModifiers(PRIVATE).build());
    }

    @Override
    protected ImmutableSet<ComponentRequirement> setterMethods() {
      return componentDescriptor().dependenciesAndConcreteModules();
    }

    @Override
    protected ImmutableMap<ComponentRequirement, String> factoryMethodParameters() {
      return ImmutableMap.of();
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      return methodBuilder("build");
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      String name = simpleVariableName(requirement.typeElement());
      return methodBuilder(name)
          .addModifiers(PUBLIC)
          .addParameter(TypeName.get(requirement.type()), name)
          .returns(className);
    }
  }

  /** Enumeration of statuses a component requirement may have in a creator. */
  enum RequirementStatus {
    /** An instance is needed to create the component. */
    NEEDED,

    /**
     * An instance is not needed to create the component, but the requirement is for a module owned
     * by the component. Setting the requirement is a no-op and any setter method should be marked
     * deprecated on the generated type as a warning to the user.
     */
    UNNEEDED,

    /**
     * The requirement may not be set in this creator because the module it is for is already
     * inherited from an ancestor component. Any setter method for it should throw an exception.
     */
    UNSETTABLE_REPEATED_MODULE,
    ;
  }
}
