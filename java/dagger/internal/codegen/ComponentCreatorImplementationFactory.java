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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.ModuleProxies.newModuleInstance;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
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
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
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
  Optional<ComponentCreatorImplementation> create(ComponentImplementation componentImplementation) {
    if (!componentImplementation.graph().componentDescriptor().hasCreator()) {
      return Optional.empty();
    }

    if (componentImplementation.superclassImplementation().isPresent()
        && componentImplementation.isAbstract()) {
      // The component builder in ahead-of-time mode is generated with the base subcomponent
      // implementation, with the exception of the build method since that requires invoking the
      // constructor of a subclass component implementation. Intermediate component implementations,
      // because they still can't invoke the eventual constructor and have no additional extensions
      // to the builder, can ignore generating a builder implementation.
      return Optional.empty();
    }

    Builder builder =
        componentImplementation.graph().componentDescriptor().creatorDescriptor().isPresent()
            ? new BuilderForCreatorDescriptor(componentImplementation)
            : new BuilderForGeneratedRootComponentBuilder(componentImplementation);
    return Optional.of(builder.build());
  }

  private static ImmutableMap<ComponentRequirement, String> requirementNames(
      ComponentImplementation componentImplementation) {
    // If the base implementation's creator is being generated in ahead-of-time-subcomponents
    // mode, this uses possiblyNecessaryRequirements() since Dagger doesn't know what modules may
    // end up being unused. Otherwise, we use the necessary component requirements.
    ImmutableSet<ComponentRequirement> requirements =
        componentImplementation.isAbstract()
                && !componentImplementation.superclassImplementation().isPresent()
            ? componentImplementation.graph().possiblyNecessaryRequirements()
            : componentImplementation.graph().componentRequirements();

    if (componentImplementation.baseImplementation().isPresent()) {
      // If there's a base implementation, retain the same names for the requirements, but filter
      // for currently used component requirements.
      ComponentCreatorImplementation baseCreatorImplementation =
          componentImplementation.baseImplementation().get().creatorImplementation().get();
      return ImmutableMap.copyOf(
          Maps.filterKeys(baseCreatorImplementation.requirementNames(), requirements::contains));
    }

    UniqueNameSet names = new UniqueNameSet();
    return Maps.toMap(requirements, requirement -> names.getUniqueName(requirement.variableName()));
  }

  private abstract class Builder {
    final ComponentImplementation componentImplementation;
    final TypeSpec.Builder componentCreatorClass;
    private final ImmutableMap<ComponentRequirement, String> requirementNames;

    Builder(ComponentImplementation componentImplementation) {
      this.componentImplementation = componentImplementation;
      this.componentCreatorClass = classBuilder(componentImplementation.getCreatorName());
      this.requirementNames = requirementNames(componentImplementation);
    }

    /** Builds the {@link ComponentCreatorImplementation}. */
    ComponentCreatorImplementation build() {
      setModifiers();
      setSupertype();
      addFields();
      addConstructor();
      addSetterMethods();
      addFactoryMethod();
      return ComponentCreatorImplementation.create(
          componentCreatorClass.build(),
          componentImplementation.getCreatorName(),
          requirementNames);
    }

    /** Returns the name of the creator class being generated. */
    final ClassName className() {
      return componentImplementation.getCreatorName();
    }

    /** Returns the binding graph for the component. */
    final BindingGraph graph() {
      return componentImplementation.graph();
    }

    /** Returns the {@link ComponentRequirement}s that are actually required by the component. */
    final ImmutableSet<ComponentRequirement> componentRequirements() {
      return requirementNames.keySet();
    }

    /**
     * Returns whether the given {@code requirement} is for a module type owned by the component.
     */
    final boolean isOwnedModule(ComponentRequirement requirement) {
      return graph().ownedModuleTypes().contains(requirement.typeElement());
    }

    private final void setModifiers() {
      componentCreatorClass.addModifiers(visibility());
      if (!componentImplementation.isNested()) {
        componentCreatorClass.addModifiers(STATIC);
      }
      componentCreatorClass.addModifiers(componentImplementation.isAbstract() ? ABSTRACT : FINAL);
    }

    /** Returns the visibility modifier the generated class should have. */
    protected abstract Modifier visibility();

    /** Sets the superclass being extended or interface being implemented for this creator. */
    protected abstract void setSupertype();

    /** Adds a constructor for the creator type, if needed. */
    protected abstract void addConstructor();

    private void addFields() {
      if (!componentImplementation.baseImplementation().isPresent()) {
        componentRequirements().stream()
            .map(this::toFieldSpec)
            .forEach(componentCreatorClass::addField);
      }
    }

    private FieldSpec toFieldSpec(ComponentRequirement requirement) {
      // Fields in an abstract creator class need to be visible from subclasses.
      Modifier modifier = componentImplementation.isAbstract() ? PROTECTED : PRIVATE;
      return FieldSpec.builder(
              TypeName.get(requirement.type()), requirementNames.get(requirement), modifier)
          .build();
    }

    private void addSetterMethods() {
      setterMethodRequirements().stream()
          .map(this::createSetterMethod)
          .forEach(componentCreatorClass::addMethod);
    }

    /** The set of requirements that need a setter method. */
    protected abstract ImmutableSet<ComponentRequirement> setterMethodRequirements();

    /** Creates a new setter method builder, with no method body, for the given requirement. */
    protected abstract MethodSpec.Builder setterMethodBuilder(
        ComponentRequirement requirement);

    private MethodSpec createSetterMethod(ComponentRequirement requirement) {
      if (componentRequirements().contains(requirement)) {
        return normalSetterMethod(requirement);
      } else if (isOwnedModule(requirement)) {
        return noopSetterMethod(requirement);
      } else {
        return inheritedModuleSetterMethod(requirement);
      }
    }

    private MethodSpec normalSetterMethod(ComponentRequirement requirement) {
      MethodSpec.Builder method = setterMethodBuilder(requirement);
      ParameterSpec parameter = parameter(method.build());
      method.addStatement(
          "this.$N = $L",
          requirementNames.get(requirement),
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
                  + "This method is a no-op. For more, see https://google.github.io/dagger/unused-modules.\n")
          .addStatement("$T.checkNotNull($N)", Preconditions.class, parameter);
      return maybeReturnThis(method);
    }

    private MethodSpec inheritedModuleSetterMethod(ComponentRequirement requirement) {
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
      if (!componentImplementation.isAbstract()) {
        componentCreatorClass.addMethod(factoryMethod());
      }
    }

    MethodSpec factoryMethod() {
      MethodSpec.Builder factoryMethod = factoryMethodBuilder();
      factoryMethod.returns(ClassName.get(graph().componentTypeElement())).addModifiers(PUBLIC);

      componentRequirements().forEach(
          requirement -> {
            FieldSpec field = toFieldSpec(requirement);
            switch (requirement.nullPolicy(elements, types)) {
              case NEW:
                checkState(requirement.kind().isModule());
                factoryMethod
                    .beginControlFlow("if ($N == null)", field)
                    .addStatement(
                        "this.$N = $L",
                        field,
                        newModuleInstance(
                            requirement.typeElement(), componentImplementation.name(), elements))
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
          });
      factoryMethod.addStatement(
          "return new $T($L)", componentImplementation.name(), componentConstructorArgs());
      return factoryMethod.build();
    }

    /** Returns a builder for the creator's factory method. */
    protected abstract MethodSpec.Builder factoryMethodBuilder();

    private CodeBlock componentConstructorArgs() {
      return componentRequirements().stream()
          .map(requirement -> CodeBlock.of("$L", requirementNames.get(requirement)))
          .collect(toParametersCodeBlock());
    }
  }

  /** Builder for a creator type defined by a {@code ComponentCreatorDescriptor}. */
  private final class BuilderForCreatorDescriptor extends Builder {
    final ComponentCreatorDescriptor creatorDescriptor;

    BuilderForCreatorDescriptor(ComponentImplementation componentImplementation) {
      super(componentImplementation);
      this.creatorDescriptor =
          componentImplementation.componentDescriptor().creatorDescriptor().get();
    }

    @Override
    protected Modifier visibility() {
      if (componentImplementation.isAbstract()) {
        // The component creator class of a top-level component implementation in ahead-of-time
        // subcomponents mode must be public, not protected, because the creator's subclass will
        // be a sibling of the component subclass implementation, not nested.
        return componentImplementation.isNested() ? PROTECTED : PUBLIC;
      }
      return PRIVATE;
    }

    @Override
    protected void setSupertype() {
      if (componentImplementation.baseImplementation().isPresent()) {
        // If there's a superclass, extend the creator defined there.
        componentCreatorClass.superclass(
            componentImplementation.baseImplementation().get().getCreatorName());
      } else {
        addSupertype(componentCreatorClass, creatorDescriptor.typeElement());
      }
    }

    @Override
    protected void addConstructor() {
      // Just use the implicit no-arg public constructor.
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      ExecutableElement factoryMethodElement = creatorDescriptor.factoryMethod();
      // Note: we don't use the factoryMethodElement.getReturnType() as the return type
      // because it might be a type variable.  We make use of covariant returns to allow
      // us to return the component type, which will always be valid.
      return methodBuilder(factoryMethodElement.getSimpleName().toString())
          .addAnnotation(Override.class);
    }

    @Override
    protected ImmutableSet<ComponentRequirement> setterMethodRequirements() {
      return ImmutableSet.copyOf(
          Sets.filter(creatorDescriptor.requirements(), this::requiresSetterMethod));
    }

    private boolean requiresSetterMethod(ComponentRequirement requirement) {
      // TODO(cgdecker): Document this better; it does what was being done before, but this
      // explanation is lacking.
      // We generate a method that throws UOE for an inherited module regardless of whether there's
      // a base creator implementation or not.
      return !hasBaseCreatorImplementation() || isInheritedModule(requirement);
    }

    private boolean isInheritedModule(ComponentRequirement requirement) {
      return !componentRequirements().contains(requirement) && !isOwnedModule(requirement);
    }

    private boolean hasBaseCreatorImplementation() {
      // In ahead-of-time subcomponents mode, all builder methods are defined at the base
      // implementation. The only case where a method needs to be overridden is for a repeated
      // module, which is unknown at the point when a base implementation is generated. We do this
      // at the root for simplicity (and as an aside, repeated modules are never used in google
      // as of 11/28/18, and thus the additional cost of including these methods at the root is
      // negligible).
      return !componentImplementation.isAbstract()
          && componentImplementation.baseImplementation().isPresent();
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      ExecutableElement supertypeMethod = creatorDescriptor.elementForRequirement(requirement);
      MethodSpec.Builder method =
          MethodSpec.overriding(
              supertypeMethod, asDeclared(creatorDescriptor.typeElement().asType()), types);
      if (!supertypeMethod.getReturnType().getKind().equals(TypeKind.VOID)) {
        // Take advantage of covariant returns so that we don't have to worry about setter methods
        // that return type variables.
        method.returns(className());
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
    protected Modifier visibility() {
      return PUBLIC;
    }

    @Override
    protected void setSupertype() {
      // There's never a supertype for a root component auto-generated builder type.
    }

    @Override
    protected void addConstructor() {
      componentCreatorClass.addMethod(constructorBuilder().addModifiers(PRIVATE).build());
    }

    @Override
    protected MethodSpec.Builder factoryMethodBuilder() {
      return methodBuilder("build");
    }

    @Override
    protected ImmutableSet<ComponentRequirement> setterMethodRequirements() {
      return graph().componentDescriptor().dependenciesAndConcreteModules();
    }

    @Override
    protected MethodSpec.Builder setterMethodBuilder(ComponentRequirement requirement) {
      String name = simpleVariableName(requirement.typeElement());
      return methodBuilder(name)
          .addModifiers(PUBLIC)
          .addParameter(TypeName.get(requirement.type()), name)
          .returns(className());
    }
  }
}
