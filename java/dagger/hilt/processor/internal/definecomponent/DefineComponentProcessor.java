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

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.definecomponent.DefineComponentBuilderMetadatas.DefineComponentBuilderMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentMetadatas.DefineComponentMetadata;
import java.io.IOException;
import javax.annotation.processing.Processor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * A processor for {@link dagger.hilt.DefineComponent} and {@link
 * dagger.hilt.DefineComponent.Builder}.
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class DefineComponentProcessor extends BaseProcessor {
  // Note: these caches should be cleared between rounds.
  private DefineComponentMetadatas componentMetadatas;
  private DefineComponentBuilderMetadatas componentBuilderMetadatas;

  @Override
  public void preRoundProcess(XRoundEnv roundEnv) {
    componentMetadatas = DefineComponentMetadatas.create();
    componentBuilderMetadatas = DefineComponentBuilderMetadatas.create(componentMetadatas);
  }

  @Override
  public void postRoundProcess(XRoundEnv roundEnv) {
    componentMetadatas = null;
    componentBuilderMetadatas = null;
  }

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        ClassNames.DEFINE_COMPONENT.toString(), ClassNames.DEFINE_COMPONENT_BUILDER.toString());
  }

  @Override
  public void processEach(XTypeElement annotation, XElement element) throws Exception {
    if (annotation.getClassName().equals(ClassNames.DEFINE_COMPONENT)) {
      // TODO(bcorso): For cycles we currently process each element in the cycle. We should skip
      // processing of subsequent elements in a cycle, but this requires ensuring that the first
      // element processed is always the same so that our failure tests are stable.
      DefineComponentMetadata metadata = componentMetadatas.get(element);
      generateFile("component", metadata.component());
    } else if (annotation.getClassName().equals(ClassNames.DEFINE_COMPONENT_BUILDER)) {
      DefineComponentBuilderMetadata metadata = componentBuilderMetadatas.get(element);
      generateFile("builder", metadata.builder());
    } else {
      throw new AssertionError("Unhandled annotation type: " + annotation.getQualifiedName());
    }
  }

  private void generateFile(String member, XTypeElement typeElement) throws IOException {
    Processors.generateAggregatingClass(
        ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE,
        AnnotationSpec.builder(ClassNames.DEFINE_COMPONENT_CLASSES)
            .addMember(member, "$S", typeElement.getQualifiedName())
            .build(),
        typeElement,
        getClass());
  }
}
