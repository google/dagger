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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.ReferenceReleasingManagerFields.typedReleasableReferenceManagerDecoratorExpression;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.Scope;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link dagger.releasablereferences.ReleasableReferenceManager
 * Provider<ReleasableReferenceManager>} creation expression.
 *
 * <p>The {@code get()} method just returns the component's {@link
 * dagger.internal.ReferenceReleasingProviderManager} field.
 */
final class ReleasableReferenceManagerProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ContributionBinding binding;
  private final GeneratedComponentModel generatedComponentModel;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;

  ReleasableReferenceManagerProviderCreationExpression(
      ContributionBinding binding,
      GeneratedComponentModel generatedComponentModel,
      ReferenceReleasingManagerFields referenceReleasingManagerFields) {
    this.binding = checkNotNull(binding);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
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
                    .addCode("return $L;", releasableReferenceManagerExpression())
                    .build())
            .build());
  }

  private CodeBlock releasableReferenceManagerExpression() {
    // The scope is the value of the @ForReleasableReferences annotation.
    Scope scope = forReleasableReferencesAnnotationValue(binding.key().qualifier().get());

    if (MoreTypes.isTypeOf(TypedReleasableReferenceManager.class, binding.key().type())) {
      /* The key's type is TypedReleasableReferenceManager<M>, so return
       * new TypedReleasableReferenceManager(field, metadata). */
      TypeMirror metadataType =
          MoreTypes.asDeclared(binding.key().type()).getTypeArguments().get(0);
      return typedReleasableReferenceManagerDecoratorExpression(
          referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name()),
          scope.releasableReferencesMetadata(metadataType).get());
    } else {
      // The key's type is ReleasableReferenceManager, so return the field as is.
      return referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name());
    }
  }

  private Scope forReleasableReferencesAnnotationValue(AnnotationMirror annotation) {
    checkArgument(
        MoreTypes.isTypeOf(ForReleasableReferences.class, annotation.getAnnotationType()));
    return Scopes.scope(
        MoreElements.asType(MoreTypes.asDeclared(getTypeValue(annotation, "value")).asElement()));
  }
}
