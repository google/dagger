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

import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.XElement
import androidx.room.compiler.processing.XProcessingEnv
import com.google.common.collect.ImmutableSet
import com.squareup.javapoet.ClassName
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.BaseProcessingStep
import dagger.internal.codegen.xprocessing.XElements

@OptIn(ExperimentalProcessingApi::class)
/** Annotation processor for @ViewModelInject. */
class ViewModelProcessingStep(env: XProcessingEnv) : BaseProcessingStep(env) {

  override fun annotationClassNames() = ImmutableSet.of(AndroidClassNames.HILT_VIEW_MODEL)

  override fun processEach(annotation: ClassName, element: XElement) {
    val typeElement = XElements.asTypeElement(element)
    ViewModelMetadata.create(processingEnv(), typeElement)?.let { viewModelMetadata ->
      ViewModelModuleGenerator(processingEnv(), viewModelMetadata).generate()
    }
  }
}
