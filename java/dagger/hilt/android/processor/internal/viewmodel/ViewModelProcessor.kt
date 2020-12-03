/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.viewmodel

import com.google.auto.common.MoreElements
import com.google.auto.service.AutoService
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.BaseProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType

/**
 * Annotation processor for @ViewModelInject.
 */
@AutoService(Processor::class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.ISOLATING)
class ViewModelProcessor : BaseProcessor() {

  private val parsedElements = mutableSetOf<TypeElement>()

  override fun getSupportedAnnotationTypes() = setOf(
    AndroidClassNames.HILT_VIEW_MODEL.toString()
  )

  override fun getSupportedSourceVersion() = SourceVersion.latest()

  override fun processEach(annotation: TypeElement, element: Element) {
    val typeElement = MoreElements.asType(element)
    if (parsedElements.add(typeElement)) {
      ViewModelMetadata.create(
        processingEnv,
        typeElement,
      )?.let { viewModelMetadata ->
        ViewModelModuleGenerator(
          processingEnv,
          viewModelMetadata
        ).generate()
      }
    }
  }

  override fun postRoundProcess(roundEnv: RoundEnvironment?) {
    parsedElements.clear()
  }
}
