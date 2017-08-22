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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Models the generated code for a component builder. */
final class ComponentBuilder {
  private final TypeSpec typeSpec;
  private final ClassName name;
  private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

  private ComponentBuilder(
      TypeSpec typeSpec,
      ClassName name,
      ImmutableMap<ComponentRequirement, FieldSpec> builderFields) {
    this.typeSpec = typeSpec;
    this.name = name;
    this.builderFields = builderFields;
  }

  TypeSpec typeSpec() {
    return typeSpec;
  }

  ClassName name() {
    return name;
  }

  ImmutableMap<ComponentRequirement, FieldSpec> builderFields() {
    return builderFields;
  }

  static ComponentBuilder create(
      ClassName componentName,
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentNames,
      Elements elements,
      Types types) {
    return new Creator(componentName, graph, subcomponentNames, elements, types).create();
  }

  private static final class Creator {
    private static final String NOOP_BUILDER_METHOD_JAVADOC =
        "This module is declared, but an instance is not used in the component. This method is a "
            + "no-op. For more, see https://google.github.io/dagger/unused-modules.\n";
    private final BindingGraph graph;
    private final TypeSpec.Builder builder;
    private final ClassName componentName;
    private final ClassName builderName;
    private final Elements elements;
    private final Types types;
    private ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

    Creator(
        ClassName componentName,
        BindingGraph graph,
        ImmutableMap<ComponentDescriptor, String> subcomponentNames,
        Elements elements,
        Types types) {
      this.componentName = componentName;
      if (graph.componentDescriptor().kind().isTopLevel()) {
        builderName = componentName.nestedClass("Builder");
        builder = classBuilder(builderName).addModifiers(STATIC);
      } else {
        builderName =
            componentName.peerClass(subcomponentNames.get(graph.componentDescriptor()) + "Builder");
        builder = classBuilder(builderName);
      }
      this.graph = graph;
      this.elements = elements;
      this.types = types;
    }

    ComponentBuilder create() {
      if (builderSpec().isPresent()) {
        builder.addModifiers(PRIVATE);
        addSupertype(builder, builderSpec().get().builderDefinitionType());
      } else {
        builder.addModifiers(PUBLIC).addMethod(constructorBuilder().addModifiers(PRIVATE).build());
      }

      builderFields = builderFields(graph);

      builder
          .addModifiers(FINAL)
          .addFields(builderFields.values())
          .addMethod(buildMethod())
          // TODO(ronshapiro): this should be switched with buildMethod(), but that currently breaks
          // compile-testing tests that rely on the order of the methods
          .addMethods(builderMethods());

      return new ComponentBuilder(builder.build(), builderName, builderFields);
    }

    /**
     * Computes fields for each of the {@linkplain BindingGraph#componentRequirements component
     * requirements}. Regardless of builder spec, there is always one field per requirement.
     */
    private static ImmutableMap<ComponentRequirement, FieldSpec> builderFields(BindingGraph graph) {
      UniqueNameSet fieldNames = new UniqueNameSet();
      ImmutableMap.Builder<ComponentRequirement, FieldSpec> builderFields = ImmutableMap.builder();
      for (ComponentRequirement componentRequirement : graph.componentRequirements()) {
        String name = fieldNames.getUniqueName(componentRequirement.variableName());
        builderFields.put(
            componentRequirement,
            FieldSpec.builder(TypeName.get(componentRequirement.type()), name, PRIVATE).build());
      }
      return builderFields.build();
    }

    private MethodSpec buildMethod() {
      MethodSpec.Builder buildMethod;
      if (builderSpec().isPresent()) {
        ExecutableElement specBuildMethod = builderSpec().get().buildMethod();
        // Note: we don't use the specBuildMethod.getReturnType() as the return type
        // because it might be a type variable.  We make use of covariant returns to allow
        // us to return the component type, which will always be valid.
        buildMethod =
            methodBuilder(specBuildMethod.getSimpleName().toString()).addAnnotation(Override.class);
      } else {
        buildMethod = methodBuilder("build");
      }
      buildMethod.returns(ClassName.get(graph.componentType())).addModifiers(PUBLIC);

      builderFields.forEach(
          (requirement, builderField) -> {
            switch (requirement.nullPolicy(elements, types)) {
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
                throw new AssertionError(requirement);
            }
          });
      buildMethod.addStatement("return new $T(this)", componentName);
      return buildMethod.build();
    }

    /**
     * Computes the methods that set each of parameters on the builder. If the {@link BuilderSpec}
     * is present, it will tailor the methods to match the spec.
     */
    private ImmutableSet<MethodSpec> builderMethods() {
      ImmutableSet<ComponentRequirement> componentRequirements = graph.componentRequirements();
      ImmutableSet.Builder<MethodSpec> methods = ImmutableSet.builder();
      if (builderSpec().isPresent()) {
        UniqueNameSet parameterNames = new UniqueNameSet();
        for (BuilderRequirementMethod requirementMethod :
            builderSpec().get().requirementMethods()) {
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
          methods.add(builderMethod.build());
        }
      } else {
        for (ComponentRequirement componentRequirement : graph.availableDependencies()) {
          String componentRequirementName = simpleVariableName(componentRequirement.typeElement());
          MethodSpec.Builder builderMethod =
              methodBuilder(componentRequirementName)
                  .returns(builderName)
                  .addModifiers(PUBLIC)
                  .addParameter(
                      TypeName.get(componentRequirement.type()), componentRequirementName);
          if (componentRequirements.contains(componentRequirement)) {
            builderMethod.addStatement(
                "this.$N = $T.checkNotNull($L)",
                builderFields.get(componentRequirement),
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

    private MethodSpec.Builder addBuilderMethodFromSpec(ExecutableElement method) {
      TypeMirror returnType = method.getReturnType();
      MethodSpec.Builder builderMethod =
          methodBuilder(method.getSimpleName().toString())
              .addAnnotation(Override.class)
              .addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
      // If the return type is void, we add a method with the void return type.
      // Otherwise we use the generated builder name and take advantage of covariant returns
      // (so that we don't have to worry about setter methods that return type variables).
      if (!returnType.getKind().equals(VOID)) {
        builderMethod.returns(builderName);
      }
      return builderMethod;
    }

    private static void addBuilderMethodReturnStatementForSpec(
        ExecutableElement specMethod, MethodSpec.Builder builderMethod) {
      if (!specMethod.getReturnType().getKind().equals(VOID)) {
        builderMethod.addStatement("return this");
      }
    }

    private Optional<BuilderSpec> builderSpec() {
      return graph.componentDescriptor().builderSpec();
    }
  }
}
