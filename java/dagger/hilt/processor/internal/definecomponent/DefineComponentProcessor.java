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

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.definecomponent.DefineComponentBuilderMetadatas.DefineComponentBuilderMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentMetadatas.DefineComponentMetadata;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * A processor for {@link dagger.hilt.DefineComponent} and {@link
 * dagger.hilt.DefineComponent.Builder}.
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class DefineComponentProcessor extends BaseProcessor {
  private final DefineComponentMetadatas componentMetadatas = DefineComponentMetadatas.create();
  private final DefineComponentBuilderMetadatas componentBuilderMetadatas =
      DefineComponentBuilderMetadatas.create(componentMetadatas);

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        ClassNames.DEFINE_COMPONENT.toString(), ClassNames.DEFINE_COMPONENT_BUILDER.toString());
  }

  @Override
  protected void processEach(TypeElement annotation, Element element) throws Exception {
    if (ClassName.get(annotation).equals(ClassNames.DEFINE_COMPONENT)) {
      // TODO(bcorso): For cycles we currently process each element in the cycle. We should skip
      // processing of subsequent elements in a cycle, but this requires ensuring that the first
      // element processed is always the same so that our failure tests are stable.
      DefineComponentMetadata metadata = componentMetadatas.get(element);
      generateFile("component", metadata.component());
    } else if (ClassName.get(annotation).equals(ClassNames.DEFINE_COMPONENT_BUILDER)) {
      DefineComponentBuilderMetadata metadata = componentBuilderMetadatas.get(element);
      generateFile("builder", metadata.builder());
    } else {
      throw new AssertionError("Unhandled annotation type: " + annotation);
    }
  }

  private void generateFile(String member, TypeElement typeElement) throws IOException {
    Processors.generateAggregatingClass(
        ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE,
        AnnotationSpec.builder(ClassNames.DEFINE_COMPONENT_CLASSES)
            .addMember(member, "$S", typeElement.getQualifiedName())
            .build(),
        typeElement,
        getClass(),
        getProcessingEnv());
  }
}
