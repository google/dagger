/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.definecomponent;


import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.definecomponent.DefineComponentBuilderMetadatas.DefineComponentBuilderMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentMetadatas.DefineComponentMetadata;

/**
 * A processor for {@link dagger.hilt.DefineComponent} and {@link
 * dagger.hilt.DefineComponent.Builder}.
 */
public final class DefineComponentProcessingStep extends BaseProcessingStep {
  // Note: these caches should be cleared between rounds.
  private DefineComponentMetadatas componentMetadatas;
  private DefineComponentBuilderMetadatas componentBuilderMetadatas;

  public DefineComponentProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  public void preProcess(XProcessingEnv env, XRoundEnv round) {
    componentMetadatas = DefineComponentMetadatas.create();
    componentBuilderMetadatas = DefineComponentBuilderMetadatas.create(componentMetadatas);
  }

  @Override
  public void postProcess(XProcessingEnv env, XRoundEnv round) {
    componentMetadatas = null;
    componentBuilderMetadatas = null;
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.DEFINE_COMPONENT, ClassNames.DEFINE_COMPONENT_BUILDER);
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    if (annotation.equals(ClassNames.DEFINE_COMPONENT)) {
      // TODO(bcorso): For cycles we currently process each element in the cycle. We should skip
      // processing of subsequent elements in a cycle, but this requires ensuring that the first
      // element processed is always the same so that our failure tests are stable.
      DefineComponentMetadata metadata = componentMetadatas.get(element);
      generateFile("component", metadata.component());
    } else if (annotation.equals(ClassNames.DEFINE_COMPONENT_BUILDER)) {
      DefineComponentBuilderMetadata metadata = componentBuilderMetadatas.get(element);
      generateFile("builder", metadata.builder());
    } else {
      throw new AssertionError("Unhandled annotation type: " + annotation.canonicalName());
    }
  }

  private void generateFile(String member, XTypeElement typeElement) {
    Processors.generateAggregatingClass(
        ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE,
        AnnotationSpec.builder(ClassNames.DEFINE_COMPONENT_CLASSES)
            .addMember(member, "$S", typeElement.getQualifiedName())
            .build(),
        typeElement,
        getClass());
  }
}
