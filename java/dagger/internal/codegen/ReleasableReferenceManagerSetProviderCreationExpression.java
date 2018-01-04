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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ReferenceReleasingManagerFields.typedReleasableReferenceManagerDecoratorExpression;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.Scope;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link ReleasableReferenceManager Provider<Set<ReleasableReferenceManager>>} creation
 * expression.
 *
 * <p>A binding for {@code Set<ReleasableReferenceManager>} will include managers for all
 * reference-releasing scopes. A binding for {@code Set<TypedReleasableReferenceManager<M>>} will
 * include managers for all reference-releasing scopes whose metadata type is {@code M}.
 */
final class ReleasableReferenceManagerSetProviderCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ContributionBinding binding;
  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;

  ReleasableReferenceManagerSetProviderCreationExpression(
      ContributionBinding binding,
      GeneratedComponentModel generatedComponentModel,
      ReferenceReleasingManagerFields referenceReleasingManagerFields,
      BindingGraph graph) {
    this.binding = checkNotNull(binding);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
    this.graph = checkNotNull(graph);
  }

  @Override
  public CodeBlock creationExpression() {
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
                    .addCode(
                        "return new $T($T.asList($L));",
                        HashSet.class,
                        Arrays.class,
                        makeParametersCodeBlock(releasableReferenceManagerExpressions()))
                    .build())
            .build());
  }

  private ImmutableList<CodeBlock> releasableReferenceManagerExpressions() {
    SetType keyType = SetType.from(binding.key());
    ImmutableList.Builder<CodeBlock> managerExpressions = ImmutableList.builder();
    for (Scope scope : graph.scopesRequiringReleasableReferenceManagers()) {
      CodeBlock releasableReferenceManagerExpression =
          referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name());

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
    return managerExpressions.build();
  }
}
