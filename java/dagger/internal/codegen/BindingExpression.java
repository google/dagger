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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.FrameworkInstanceBindingExpression.producerFromProviderBindingExpression;
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import java.util.Optional;
import javax.lang.model.util.Elements;

/** A factory of code expressions used to access a single binding in a component. */
abstract class BindingExpression {
  private final ResolvedBindings resolvedBindings;
  private final ClassName componentName;

  BindingExpression(ResolvedBindings resolvedBindings, ClassName componentName) {
    this.resolvedBindings = checkNotNull(resolvedBindings);
    this.componentName = checkNotNull(componentName);
  }

  /** The binding this instance uses to fulfill requests. */
  final ResolvedBindings resolvedBindings() {
    return resolvedBindings;
  }

  /** The name of the component owning this binding expression. */
  final ClassName componentName() {
    return componentName;
  }

  /**
   * Returns an expression that evaluates to the value of a request for a given kind of dependency
   * on this binding.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract CodeBlock getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass);

  /** Factory for building a {@link BindingExpression}. */
  static final class Factory {
    private final CompilerOptions compilerOptions;
    private final ClassName componentName;
    private final UniqueNameSet componentFieldNames;
    private final ComponentBindingExpressions componentBindingExpressions;
    private final ComponentRequirementFields componentRequirementFields;
    private final GeneratedComponentModel generatedComponentModel;
    private final ImmutableMap<BindingKey, String> subcomponentNames;
    private final BindingGraph graph;
    private final Elements elements;
    private final OptionalFactories optionalFactories;

    Factory(
        CompilerOptions compilerOptions,
        ClassName componentName,
        UniqueNameSet componentFieldNames,
        ComponentBindingExpressions componentBindingExpressions,
        ComponentRequirementFields componentRequirementFields,
        GeneratedComponentModel generatedComponentModel,
        ImmutableMap<BindingKey, String> subcomponentNames,
        BindingGraph graph,
        Elements elements,
        OptionalFactories optionalFactories) {
      this.compilerOptions = checkNotNull(compilerOptions);
      this.componentName = checkNotNull(componentName);
      this.componentFieldNames = checkNotNull(componentFieldNames);
      this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
      this.componentRequirementFields = checkNotNull(componentRequirementFields);
      this.generatedComponentModel = checkNotNull(generatedComponentModel);
      this.subcomponentNames = checkNotNull(subcomponentNames);
      this.graph = checkNotNull(graph);
      this.elements = checkNotNull(elements);
      this.optionalFactories = checkNotNull(optionalFactories);
    }

    /** Creates a binding expression for a field. */
    BindingExpression forField(ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.empty());
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return create(resolvedBindings, Optional.of(fieldSpec), memberSelect);
    }

    FrameworkInstanceBindingExpression forProducerFromProviderField(
        ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.of(PRODUCER));
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return producerFromProviderBindingExpression(
          resolvedBindings,
          componentName,
          Optional.of(fieldSpec),
          generatedComponentModel,
          memberSelect,
          componentBindingExpressions,
          componentRequirementFields,
          compilerOptions,
          graph,
          optionalFactories);
    }

    /** Creates a binding expression for a static method call. */
    Optional<BindingExpression> forStaticMethod(ResolvedBindings resolvedBindings) {
      return staticMemberSelect(resolvedBindings)
          .map(memberSelect -> create(resolvedBindings, Optional.empty(), memberSelect));
    }

    /**
     * Adds a field representing the resolved bindings, optionally forcing it to use a particular
     * binding type (instead of the type the resolved bindings would typically use).
     */
    private FieldSpec generateFrameworkField(
        ResolvedBindings resolvedBindings, Optional<ClassName> frameworkClass) {
      boolean useRawType = useRawType(resolvedBindings);

      FrameworkField contributionBindingField =
          FrameworkField.forResolvedBindings(resolvedBindings, frameworkClass);
      FieldSpec.Builder contributionField =
          FieldSpec.builder(
              useRawType
                  ? contributionBindingField.type().rawType
                  : contributionBindingField.type(),
              componentFieldNames.getUniqueName(contributionBindingField.name()));
      contributionField.addModifiers(PRIVATE);
      if (useRawType) {
        contributionField.addAnnotation(AnnotationSpecs.suppressWarnings(RAWTYPES));
      }

      return contributionField.build();
    }

    private boolean useRawType(ResolvedBindings resolvedBindings) {
      Optional<String> bindingPackage = resolvedBindings.bindingPackage();
      return bindingPackage.isPresent()
          && !bindingPackage.get().equals(componentName.packageName());
    }

    private BindingExpression create(
        ResolvedBindings resolvedBindings,
        Optional<FieldSpec> fieldSpec,
        MemberSelect memberSelect) {
      FrameworkInstanceBindingExpression bindingExpression =
          FrameworkInstanceBindingExpression.create(
              resolvedBindings,
              componentName,
              fieldSpec,
              generatedComponentModel,
              memberSelect,
              componentBindingExpressions,
              componentRequirementFields,
              compilerOptions,
              graph,
              optionalFactories);

      if (!resolvedBindings.bindingType().equals(BindingType.PROVISION)) {
        return bindingExpression;
      }

      ProvisionBinding provisionBinding = (ProvisionBinding) resolvedBindings.contributionBinding();
      switch (provisionBinding.bindingKind()) {
        case COMPONENT:
          return new ComponentInstanceBindingExpression(bindingExpression, componentName);
        case COMPONENT_DEPENDENCY:
          return new BoundInstanceBindingExpression(
              bindingExpression,
              ComponentRequirement.forDependency(provisionBinding.key().type()),
              componentRequirementFields);
        case COMPONENT_PROVISION:
          return new ComponentProvisionBindingExpression(
              bindingExpression,
              provisionBinding,
              graph,
              componentRequirementFields,
              compilerOptions);
        case SUBCOMPONENT_BUILDER:
          return new SubcomponentBuilderBindingExpression(
              bindingExpression, subcomponentNames.get(resolvedBindings.bindingKey()));
        case SYNTHETIC_MULTIBOUND_SET:
          return new SetBindingExpression(
              provisionBinding, graph, componentBindingExpressions, bindingExpression, elements);
        case SYNTHETIC_MULTIBOUND_MAP:
          return new MapBindingExpression(
              provisionBinding, graph, componentBindingExpressions, bindingExpression, elements);
        case SYNTHETIC_OPTIONAL_BINDING:
          return new OptionalBindingExpression(
              provisionBinding, bindingExpression, componentBindingExpressions);
        case BUILDER_BINDING:
              return new BoundInstanceBindingExpression(
                  bindingExpression,
                  ComponentRequirement.forBinding(provisionBinding),
                  componentRequirementFields);
        case INJECTION:
        case PROVISION:
          if (!provisionBinding.scope().isPresent()
              && provisionBinding.bindingElement().isPresent()) {
            Optional<ComponentRequirement> moduleRequirement =
                provisionBinding.requiresModuleInstance()
                    ? Optional.of(
                        ComponentRequirement.forModule(
                            provisionBinding.contributingModule().get().asType()))
                    : Optional.empty();
            return new SimpleMethodBindingExpression(
                compilerOptions,
                provisionBinding,
                bindingExpression,
                componentBindingExpressions,
                generatedComponentModel,
                moduleRequirement,
                componentRequirementFields);
          }
          // fall through
        default:
          return bindingExpression;
      }
    }
  }
}
