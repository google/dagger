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
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/** Factory for creating {@link ComponentCreatorImplementation} instances. */
final class ComponentCreatorImplementationFactory {
  private static final String NOOP_BUILDER_METHOD_JAVADOC =
      "This module is declared, but an instance is not used in the component. This method is a "
          + "no-op. For more, see https://google.github.io/dagger/unused-modules.\n";

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
    final BindingGraph graph;
    final TypeSpec.Builder componentCreatorClass;
    final ImmutableMap<ComponentRequirement, String> requirementNames;
    final ImmutableSet<ComponentRequirement> requirements;

    Builder(ComponentImplementation componentImplementation) {
      this.componentImplementation = componentImplementation;
      this.graph = componentImplementation.graph();
      this.componentCreatorClass = classBuilder(componentImplementation.getCreatorName());
      this.requirementNames = requirementNames(componentImplementation);
      this.requirements = requirementNames.keySet();
    }

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
        requirements.stream().map(this::toFieldSpec).forEach(componentCreatorClass::addField);
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
      componentCreatorClass.addMethods(setterMethods());
    }

    private void addFactoryMethod() {
      if (!componentImplementation.isAbstract()) {
        componentCreatorClass.addMethod(factoryMethod());
      }
    }

    MethodSpec factoryMethod() {
      MethodSpec.Builder factoryMethod = factoryMethodBuilder();
      factoryMethod.returns(ClassName.get(graph.componentTypeElement())).addModifiers(PUBLIC);

      requirements.forEach(
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
      return requirements.stream()
          .map(requirement -> CodeBlock.of("$L", requirementNames.get(requirement)))
          .collect(toParametersCodeBlock());
    }

    /**
     * Computes the methods that set each field on the builder. If the {@link
     * ComponentCreatorDescriptor} is present, it will tailor the methods to match the descriptor.
     */
    protected abstract ImmutableSet<MethodSpec> setterMethods();
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
    protected ImmutableSet<MethodSpec> setterMethods() {
      ImmutableSet.Builder<MethodSpec> methods = ImmutableSet.builder();

      // In ahead-of-time subcomponents mode, all builder methods are defined at the base
      // implementation. The only case where a method needs to be overridden is for a repeated
      // module, which is unknown at the point when a base implementation is generated. We do this
      // at the root for simplicity (and as an aside, repeated modules are never used in google
      // as of 11/28/18, and thus the additional cost of including these methods at the root is
      // negligible).
      boolean hasBaseCreatorImplementation =
          !componentImplementation.isAbstract()
              && componentImplementation.baseImplementation().isPresent();

      UniqueNameSet parameterNames = new UniqueNameSet();
      for (ComponentRequirement requirement : creatorDescriptor.requirements()) {
        ExecutableElement method = creatorDescriptor.elementForRequirement(requirement);
        MethodSpec.Builder setterMethod = setterMethod(method);
        VariableElement parameterElement = getOnlyElement(method.getParameters());
        String parameterName = parameterNames.getUniqueName(parameterElement.getSimpleName());

        TypeName argType =
            parameterElement.asType().getKind().isPrimitive()
                // Primitives need to use the original (unresolved) type to avoid boxing.
                ? TypeName.get(parameterElement.asType())
                // Otherwise we use the full resolved type.
                : TypeName.get(requirement.type());

        setterMethod.addParameter(argType, parameterName);

        if (requirements.contains(requirement)) {
          if (hasBaseCreatorImplementation) {
            continue;
          }
          // required type
          setterMethod.addStatement(
              "this.$N = $L",
              requirementNames.get(requirement),
              requirement.nullPolicy(elements, types).equals(ComponentRequirement.NullPolicy.ALLOW)
                  ? parameterName
                  : CodeBlock.of("$T.checkNotNull($L)", Preconditions.class, parameterName));
          addSetterMethodReturnStatementForSpec(method, setterMethod);
        } else if (graph.ownedModuleTypes().contains(requirement.typeElement())) {
          if (hasBaseCreatorImplementation) {
            continue;
          }
          // owned, but not required
          setterMethod.addJavadoc(NOOP_BUILDER_METHOD_JAVADOC);
          addSetterMethodReturnStatementForSpec(method, setterMethod);
        } else {
          // neither owned nor required, so it must be an inherited module
          setterMethod.addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UnsupportedOperationException.class,
              String.class,
              "%s cannot be set because it is inherited from the enclosing component",
              TypeNames.rawTypeName(TypeName.get(requirement.type())));
        }

        methods.add(setterMethod.build());
      }

      return methods.build();
    }

    private MethodSpec.Builder setterMethod(ExecutableElement method) {
      TypeMirror returnType = method.getReturnType();
      MethodSpec.Builder setterMethod =
          methodBuilder(method.getSimpleName().toString())
              .addAnnotation(Override.class)
              .addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
      // If the return type is void, we add a method with the void return type.
      // Otherwise we use the generated builder name and take advantage of covariant returns
      // (so that we don't have to worry about setter methods that return type variables).
      if (!returnType.getKind().equals(VOID)) {
        setterMethod.returns(componentImplementation.getCreatorName());
      }
      return setterMethod;
    }

    private void addSetterMethodReturnStatementForSpec(
        ExecutableElement specMethod, MethodSpec.Builder setterMethod) {
      if (!specMethod.getReturnType().getKind().equals(VOID)) {
        setterMethod.addStatement("return this");
      }
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
    protected ImmutableSet<MethodSpec> setterMethods() {
      ImmutableSet.Builder<MethodSpec> methods = ImmutableSet.builder();

      for (ComponentRequirement requirement :
          graph.componentDescriptor().dependenciesAndConcreteModules()) {
        String componentRequirementName = simpleVariableName(requirement.typeElement());
        MethodSpec.Builder setterMethod =
            methodBuilder(componentRequirementName)
                .returns(componentImplementation.getCreatorName())
                .addModifiers(PUBLIC)
                .addParameter(TypeName.get(requirement.type()), componentRequirementName);
        if (requirements.contains(requirement)) {
          setterMethod.addStatement(
              "this.$N = $T.checkNotNull($L)",
              requirementNames.get(requirement),
              Preconditions.class,
              componentRequirementName);
        } else {
          setterMethod.addStatement(
              "$T.checkNotNull($L)", Preconditions.class, componentRequirementName);
          setterMethod.addJavadoc("@deprecated " + NOOP_BUILDER_METHOD_JAVADOC);
          setterMethod.addAnnotation(Deprecated.class);
        }
        setterMethod.addStatement("return this");
        methods.add(setterMethod.build());
      }

      return methods.build();
    }
  }
}
