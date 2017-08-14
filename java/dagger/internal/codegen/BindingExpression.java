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
import static dagger.internal.codegen.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.MemberSelect.staticMemberSelect;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static dagger.internal.codegen.TypeNames.rawTypeName;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** The code expressions to declare, initialize, and/or access a binding in a component. */
abstract class BindingExpression {
  private final BindingKey bindingKey;

  BindingExpression(BindingKey bindingKey) {
    this.bindingKey = checkNotNull(bindingKey);
  }

  /** The key for which this instance can fulfill requests. */
  final BindingKey bindingKey() {
    return bindingKey;
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract CodeBlock getDependencyExpression(DependencyRequest request, ClassName requestingClass);

  /**
   * Returns an expression that evaluates to the value of a framework dependency.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract CodeBlock getDependencyExpression(
      FrameworkDependency frameworkDependency, ClassName requestingClass);

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@link InjectionMethods injection method}, each
   * parameter will be {@link Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  // TODO(b/64024402) Merge with getDependencyExpression(DependencyRequest, ClassName) if possible.
  CodeBlock getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, ClassName requestingClass) {
    CodeBlock.Builder argument = CodeBlock.builder();

    TypeMirror dependencyType = dependencyRequest.key().type();
    if (!isTypeAccessibleFrom(dependencyType, requestingClass.packageName())
        && isRawTypeAccessible(dependencyType, requestingClass.packageName())) {
      argument.add("($T) ", rawTypeName(TypeName.get(dependencyType)));
    }

    argument.add(getDependencyExpression(dependencyRequest, requestingClass));
    return argument.build();
  }

  /** Factory for building a {@link BindingExpression}. */
  static final class Factory {
    private final CompilerOptions compilerOptions;
    private final ClassName componentName;
    private final UniqueNameSet componentFieldNames;
    private final HasBindingExpressions hasBindingExpressions;
    private final ImmutableMap<BindingKey, String> subcomponentNames;
    private final BindingGraph graph;
    private final Elements elements;

    Factory(
        CompilerOptions compilerOptions,
        ClassName componentName,
        UniqueNameSet componentFieldNames,
        HasBindingExpressions hasBindingExpressions,
        ImmutableMap<BindingKey, String> subcomponentNames,
        BindingGraph graph,
        Elements elements) {
      this.compilerOptions = checkNotNull(compilerOptions);
      this.componentName = checkNotNull(componentName);
      this.componentFieldNames = checkNotNull(componentFieldNames);
      this.hasBindingExpressions = checkNotNull(hasBindingExpressions);
      this.subcomponentNames = checkNotNull(subcomponentNames);
      this.graph = checkNotNull(graph);
      this.elements = checkNotNull(elements);
    }

    /** Creates a binding expression for a field. */
    BindingExpression forField(ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.empty());
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return create(resolvedBindings, Optional.of(fieldSpec), memberSelect);
    }

    BindingExpression forProducerFromProviderField(ResolvedBindings resolvedBindings) {
      FieldSpec fieldSpec = generateFrameworkField(resolvedBindings, Optional.of(PRODUCER));
      MemberSelect memberSelect = MemberSelect.localField(componentName, fieldSpec.name);
      return new ProducerBindingExpression(
          resolvedBindings.bindingKey(),
          Optional.of(fieldSpec),
          hasBindingExpressions,
          memberSelect,
          true);
    }

    /**
     * Creates a binding expression for a static method call.
     */
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
      BindingKey bindingKey = resolvedBindings.bindingKey();
      switch (resolvedBindings.bindingType()) {
        case MEMBERS_INJECTION:
          return new MembersInjectorBindingExpression(
              bindingKey, fieldSpec, hasBindingExpressions, memberSelect);
        case PRODUCTION:
          return new ProducerBindingExpression(
              bindingKey, fieldSpec, hasBindingExpressions, memberSelect, false);
        case PROVISION:
          ProvisionBinding provisionBinding =
              (ProvisionBinding) resolvedBindings.contributionBinding();

          ProviderBindingExpression providerBindingExpression =
              new ProviderBindingExpression(
                  bindingKey, fieldSpec, hasBindingExpressions, memberSelect);

          switch (provisionBinding.bindingKind()) {
            case SUBCOMPONENT_BUILDER:
              return new SubcomponentBuilderBindingExpression(
                  providerBindingExpression, subcomponentNames.get(bindingKey));
            case SYNTHETIC_MULTIBOUND_SET:
              return new SetBindingExpression(
                  provisionBinding,
                  graph,
                  hasBindingExpressions,
                  providerBindingExpression,
                  elements);
            case SYNTHETIC_OPTIONAL_BINDING:
              return new OptionalBindingExpression(
                  provisionBinding, providerBindingExpression, hasBindingExpressions);
            case INJECTION:
            case PROVISION:
              if (!provisionBinding.scope().isPresent()
                  && !provisionBinding.requiresModuleInstance()
                  && provisionBinding.bindingElement().isPresent()) {
                return new SimpleMethodBindingExpression(
                    compilerOptions,
                    provisionBinding,
                    providerBindingExpression,
                    hasBindingExpressions);
              }
              // fall through
            default:
              return providerBindingExpression;
          }
        default:
          throw new AssertionError();
      }
    }
  }
}
