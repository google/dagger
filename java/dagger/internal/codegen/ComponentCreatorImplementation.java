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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
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
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** The implementation of a component creator type. */
final class ComponentCreatorImplementation {
  private final TypeSpec componentCreatorClass;
  private final ClassName name;
  private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

  private ComponentCreatorImplementation(
      TypeSpec componentCreatorClass,
      ClassName name,
      ImmutableMap<ComponentRequirement, FieldSpec> builderFields) {
    this.componentCreatorClass = componentCreatorClass;
    this.name = name;
    this.builderFields = builderFields;
  }

  TypeSpec componentCreatorClass() {
    return componentCreatorClass;
  }

  ClassName name() {
    return name;
  }

  ImmutableMap<ComponentRequirement, FieldSpec> builderFields() {
    return builderFields;
  }

  static Optional<ComponentCreatorImplementation> create(
      ComponentImplementation componentImplementation,
      BindingGraph graph,
      Elements elements,
      Types types) {
    if (componentImplementation.superclassImplementation().isPresent()
        && componentImplementation.isAbstract()) {
      // The component builder in ahead-of-time mode is generated with the base subcomponent
      // implementation, with the exception of the build method since that requires invoking the
      // constructor of a subclass component implementation. Intermediate component implementations,
      // because they still can't invoke the eventual constructor and have no additional extensions
      // to the builder, can ignore generating a builder implementation.
      return Optional.empty();
    }
    return graph.componentDescriptor().hasCreator()
        ? Optional.of(
            new CreatorImplementationFactory(componentImplementation, graph, elements, types)
                .create())
        : Optional.empty();
  }

  /** Factory for creating a {@link ComponentCreatorImplementation} instance. */
  private static final class CreatorImplementationFactory {
    // TODO(cgdecker): Possibly extract this to another top-level type,
    // ComponentCreatorImplementationFactory, to match the separation between
    // ComponentImplementation and ComponentImplementationFactory

    static final String NOOP_BUILDER_METHOD_JAVADOC =
        "This module is declared, but an instance is not used in the component. This method is a "
            + "no-op. For more, see https://google.github.io/dagger/unused-modules.\n";

    final BindingGraph graph;
    final TypeSpec.Builder componentCreatorClass;
    final ComponentImplementation componentImplementation;
    final Elements elements;
    final Types types;

    CreatorImplementationFactory(
        ComponentImplementation componentImplementation,
        BindingGraph graph,
        Elements elements,
        Types types) {
      this.componentImplementation = componentImplementation;
      this.componentCreatorClass = classBuilder(componentImplementation.getCreatorName());
      this.graph = graph;
      this.elements = elements;
      this.types = types;
    }

    ComponentCreatorImplementation create() {
      if (!componentImplementation.isNested()) {
        componentCreatorClass.addModifiers(STATIC);
      }
      if (creatorDescriptor().isPresent()) {
        if (componentImplementation.isAbstract()) {
          // The component creator class of a top-level component implementation in ahead-of-time
          // subcomponents mode must be public, not protected, because the creator's subclass will
          // be a sibling of the component subclass implementation, not nested.
          componentCreatorClass.addModifiers(
              componentImplementation.isNested() ? PROTECTED : PUBLIC);
        } else {
          componentCreatorClass.addModifiers(PRIVATE);
        }
        setSupertype();
      } else {
        componentCreatorClass
            .addModifiers(PUBLIC)
            .addMethod(constructorBuilder().addModifiers(PRIVATE).build());
      }

      ImmutableMap<ComponentRequirement, FieldSpec> builderFields = builderFields();

      if (componentImplementation.isAbstract()) {
        componentCreatorClass.addModifiers(ABSTRACT);
      } else {
        componentCreatorClass.addModifiers(FINAL);
        componentCreatorClass.addMethod(factoryMethod(builderFields));
      }

      if (!componentImplementation.baseImplementation().isPresent()) {
        componentCreatorClass.addFields(builderFields.values());
      }

      // TODO(ronshapiro): this should be switched with factoryMethod(), but that currently breaks
      // compile-testing tests that rely on the order of the methods
      componentCreatorClass.addMethods(builderMethods(builderFields));

      return new ComponentCreatorImplementation(
          componentCreatorClass.build(), componentImplementation.getCreatorName(), builderFields);
    }

    /** Set the superclass being extended or interface being implemented for this creator. */
    void setSupertype() {
      if (componentImplementation.baseImplementation().isPresent()) {
        // If there's a superclass, extend the creator defined there.
        componentCreatorClass.superclass(
            componentImplementation.baseImplementation().get().getCreatorName());
      } else {
        addSupertype(componentCreatorClass, creatorDescriptor().get().typeElement());
      }
    }

    /**
     * Computes fields for each of the {@link ComponentRequirement}s}. Regardless of creator spec,
     * there is always one field per requirement.
     *
     * <p>If the base implementation's creator is being generated in ahead-of-time-subcomponents
     * mode, this uses {@link BindingGraph#possiblyNecessaryRequirements()} since Dagger doesn't
     * know what modules may end up being unused. Otherwise, we use the {@link
     * BindingGraph#componentRequirements() necessary component requirements}.
     */
    ImmutableMap<ComponentRequirement, FieldSpec> builderFields() {
      UniqueNameSet fieldNames = new UniqueNameSet();
      ImmutableMap.Builder<ComponentRequirement, FieldSpec> builderFields = ImmutableMap.builder();
      Modifier modifier = componentImplementation.isAbstract() ? PUBLIC : PRIVATE;
      for (ComponentRequirement componentRequirement : componentRequirements()) {
        String name = fieldNames.getUniqueName(componentRequirement.variableName());
        builderFields.put(
            componentRequirement,
            FieldSpec.builder(TypeName.get(componentRequirement.type()), name, modifier).build());
      }
      return builderFields.build();
    }

    MethodSpec factoryMethod(ImmutableMap<ComponentRequirement, FieldSpec> builderFields) {
      MethodSpec.Builder factoryMethod;
      if (creatorDescriptor().isPresent()) {
        ExecutableElement factoryMethodElement = creatorDescriptor().get().factoryMethod();
        // Note: we don't use the factoryMethodElement.getReturnType() as the return type
        // because it might be a type variable.  We make use of covariant returns to allow
        // us to return the component type, which will always be valid.
        factoryMethod =
            methodBuilder(factoryMethodElement.getSimpleName().toString())
                .addAnnotation(Override.class);
      } else {
        factoryMethod = methodBuilder("build");
      }
      factoryMethod.returns(ClassName.get(graph.componentTypeElement())).addModifiers(PUBLIC);

      builderFields.forEach(
          (requirement, field) -> {
            switch (requirement.nullPolicy(elements, types)) {
              case NEW:
                factoryMethod
                    .beginControlFlow("if ($N == null)", field)
                    .addStatement("this.$N = new $T()", field, field.type)
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
              default:
                throw new AssertionError(requirement);
            }
          });
      factoryMethod.addStatement("return new $T(this)", componentImplementation.name());
      return factoryMethod.build();
    }

    /**
     * Computes the methods that set each of parameters on the builder. If the {@link
     * ComponentCreatorDescriptor} is present, it will tailor the methods to match the descriptor.
     */
    ImmutableSet<MethodSpec> builderMethods(
        ImmutableMap<ComponentRequirement, FieldSpec> builderFields) {
      ImmutableSet<ComponentRequirement> componentRequirements = componentRequirements();
      ImmutableSet.Builder<MethodSpec> methods = ImmutableSet.builder();
      // TODO(ronshapiro): extract two separate methods: builderMethodsForBuilderSpec and
      // builderMethodsForGeneratedTopLevelComponentBuilder()
      if (creatorDescriptor().isPresent()) {
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
        ComponentCreatorDescriptor creatorDescriptor = creatorDescriptor().get();
        for (ComponentRequirement requirement : creatorDescriptor.requirements()) {
          ExecutableElement method = creatorDescriptor.elementForRequirement(requirement);
          MethodSpec.Builder builderMethod = addBuilderMethodFromSpec(method);
          VariableElement parameterElement = getOnlyElement(method.getParameters());
          String parameterName = parameterNames.getUniqueName(parameterElement.getSimpleName());

          TypeName argType =
              parameterElement.asType().getKind().isPrimitive()
                  // Primitives need to use the original (unresolved) type to avoid boxing.
                  ? TypeName.get(parameterElement.asType())
                  // Otherwise we use the full resolved type.
                  : TypeName.get(requirement.type());

          builderMethod.addParameter(argType, parameterName);

          if (componentRequirements.contains(requirement)) {
            if (hasBaseCreatorImplementation) {
              continue;
            }
            // required type
            builderMethod.addStatement(
                "this.$N = $L",
                builderFields.get(requirement),
                requirement
                        .nullPolicy(elements, types)
                        .equals(ComponentRequirement.NullPolicy.ALLOW)
                    ? parameterName
                    : CodeBlock.of("$T.checkNotNull($L)", Preconditions.class, parameterName));
            addBuilderMethodReturnStatementForSpec(method, builderMethod);
          } else if (graph.ownedModuleTypes().contains(requirement.typeElement())) {
            if (hasBaseCreatorImplementation) {
              continue;
            }
            // owned, but not required
            builderMethod.addJavadoc(NOOP_BUILDER_METHOD_JAVADOC);
            addBuilderMethodReturnStatementForSpec(method, builderMethod);
          } else {
            // neither owned nor required, so it must be an inherited module
            builderMethod.addStatement(
                "throw new $T($T.format($S, $T.class.getCanonicalName()))",
                UnsupportedOperationException.class,
                String.class,
                "%s cannot be set because it is inherited from the enclosing component",
                TypeNames.rawTypeName(TypeName.get(requirement.type())));
          }

          methods.add(builderMethod.build());
        }
      } else {
        for (ComponentRequirement requirement :
            graph.componentDescriptor().dependenciesAndConcreteModules()) {
          String componentRequirementName = simpleVariableName(requirement.typeElement());
          MethodSpec.Builder builderMethod =
              methodBuilder(componentRequirementName)
                  .returns(componentImplementation.getCreatorName())
                  .addModifiers(PUBLIC)
                  .addParameter(TypeName.get(requirement.type()), componentRequirementName);
          if (componentRequirements.contains(requirement)) {
            builderMethod.addStatement(
                "this.$N = $T.checkNotNull($L)",
                builderFields.get(requirement),
                Preconditions.class,
                componentRequirementName);
          } else {
            builderMethod.addStatement(
                "$T.checkNotNull($L)", Preconditions.class, componentRequirementName);
            builderMethod.addJavadoc("@deprecated " + NOOP_BUILDER_METHOD_JAVADOC);
            builderMethod.addAnnotation(Deprecated.class);
          }
          builderMethod.addStatement("return this");
          methods.add(builderMethod.build());
        }
      }
      return methods.build();
    }

    private ImmutableSet<ComponentRequirement> componentRequirements() {
      return !componentImplementation.superclassImplementation().isPresent()
              && componentImplementation.isAbstract()
          ? graph.possiblyNecessaryRequirements()
          : graph.componentRequirements();
    }

    MethodSpec.Builder addBuilderMethodFromSpec(ExecutableElement method) {
      TypeMirror returnType = method.getReturnType();
      MethodSpec.Builder builderMethod =
          methodBuilder(method.getSimpleName().toString())
              .addAnnotation(Override.class)
              .addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
      // If the return type is void, we add a method with the void return type.
      // Otherwise we use the generated builder name and take advantage of covariant returns
      // (so that we don't have to worry about setter methods that return type variables).
      if (!returnType.getKind().equals(VOID)) {
        builderMethod.returns(componentImplementation.getCreatorName());
      }
      return builderMethod;
    }

    static void addBuilderMethodReturnStatementForSpec(
        ExecutableElement specMethod, MethodSpec.Builder builderMethod) {
      if (!specMethod.getReturnType().getKind().equals(VOID)) {
        builderMethod.addStatement("return this");
      }
    }

    Optional<ComponentCreatorDescriptor> creatorDescriptor() {
      return graph.componentDescriptor().creatorDescriptor();
    }
  }
}
