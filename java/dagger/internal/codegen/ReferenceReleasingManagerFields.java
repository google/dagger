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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.REFERENCE_RELEASING_MANAGER_FIELD;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER_MANAGER;
import static dagger.internal.codegen.TypeNames.TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.internal.TypedReleasableReferenceManagerDecorator;
import dagger.model.Scope;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;

/**
 * Manages the {@link dagger.internal.ReferenceReleasingProviderManager} fields and the logic for
 * releasable bindings in the graph.
 *
 * <p>This class should only be created once at the root component and reused by all subcomponents.
 * This is because, currently, all {@link dagger.internal.ReferenceReleasingProviderManager} fields
 * are stored in the root component.
 */
public class ReferenceReleasingManagerFields {
  /**
   * The member-selects for {@link dagger.internal.ReferenceReleasingProviderManager} fields,
   * indexed by their {@code CanReleaseReferences @CanReleaseReferences} scope.
   */
  private final Map<Scope, MemberSelect> referenceReleasingManagerFields = new HashMap<>();

  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;

  ReferenceReleasingManagerFields(
      BindingGraph graph, GeneratedComponentModel generatedComponentModel) {
    this.graph = checkNotNull(graph);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    checkArgument(graph.componentDescriptor().kind().isTopLevel());
  }

  /**
   * Returns an expression that evaluates to a {@link TypedReleasableReferenceManagerDecorator} that
   * decorates the {@code managerExpression} to supply {@code metadata}.
   */
  static CodeBlock typedReleasableReferenceManagerDecoratorExpression(
      CodeBlock managerExpression, AnnotationMirror metadata) {
    return CodeBlock.of(
        "new $T<$T>($L, $L)",
        TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR,
        metadata.getAnnotationType(),
        managerExpression,
        new AnnotationExpression(metadata).getAnnotationInstanceExpression());
  }

  /**
   * Returns {@code true} if {@code scope} is in {@link
   * BindingGraph#scopesRequiringReleasableReferenceManagers()} for the root graph.
   */
  boolean requiresReleasableReferences(Scope scope) {
    return graph.scopesRequiringReleasableReferenceManagers().contains(scope);
  }

  /**
   * The member-select expression for the {@link dagger.internal.ReferenceReleasingProviderManager}
   * object for a scope.
   */
  CodeBlock getExpression(Scope scope, ClassName requestingClass) {
    return reentrantComputeIfAbsent(
            referenceReleasingManagerFields, scope, this::createReferenceReleasingManagerField)
        .getExpressionFor(requestingClass);
  }

  private MemberSelect createReferenceReleasingManagerField(Scope scope) {
    FieldSpec field = referenceReleasingProxyManagerField(scope);
    generatedComponentModel.addField(REFERENCE_RELEASING_MANAGER_FIELD, field);
    return localField(generatedComponentModel.name(), field.name);
  }

  private FieldSpec referenceReleasingProxyManagerField(Scope scope) {
    String fieldName =
        UPPER_CAMEL.to(LOWER_CAMEL, scope.scopeAnnotationElement().getSimpleName() + "References");
    return FieldSpec.builder(
            REFERENCE_RELEASING_PROVIDER_MANAGER,
            generatedComponentModel.getUniqueFieldName(fieldName))
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
}
