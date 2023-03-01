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
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XTypeElement
import com.squareup.javapoet.ClassName
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.ClassNames
import dagger.hilt.processor.internal.ProcessorErrors
import dagger.hilt.processor.internal.Processors
import dagger.internal.codegen.xprocessing.XAnnotations
import dagger.internal.codegen.xprocessing.XTypes

/** Data class that represents a Hilt injected ViewModel */
@OptIn(ExperimentalProcessingApi::class)
internal class ViewModelMetadata private constructor(val typeElement: XTypeElement) {
  val className = typeElement.className

  val modulesClassName =
    ClassName.get(
      typeElement.packageName,
      "${className.simpleNames().joinToString("_")}_HiltModules"
    )

  companion object {
    internal fun create(
      processingEnv: XProcessingEnv,
      typeElement: XTypeElement,
    ): ViewModelMetadata? {
      ProcessorErrors.checkState(
        XTypes.isSubtype(typeElement.type, processingEnv.requireType(AndroidClassNames.VIEW_MODEL)),
        typeElement,
        "@HiltViewModel is only supported on types that subclass %s.",
        AndroidClassNames.VIEW_MODEL
      )

      typeElement
        .getConstructors()
        .filter { constructor ->
          ProcessorErrors.checkState(
            !constructor.hasAnnotation(ClassNames.ASSISTED_INJECT),
            constructor,
            "ViewModel constructor should be annotated with @Inject instead of @AssistedInject."
          )
          constructor.hasAnnotation(ClassNames.INJECT)
        }
        .let { injectConstructors ->
          ProcessorErrors.checkState(
            injectConstructors.size == 1,
            typeElement,
            "@HiltViewModel annotated class should contain exactly one @Inject " +
              "annotated constructor."
          )

          injectConstructors.forEach { injectConstructor ->
            ProcessorErrors.checkState(
              !injectConstructor.isPrivate(),
              injectConstructor,
              "@Inject annotated constructors must not be private."
            )
          }
        }

      ProcessorErrors.checkState(
        !typeElement.isNested() || typeElement.isStatic(),
        typeElement,
        "@HiltViewModel may only be used on inner classes if they are static."
      )

      Processors.getScopeAnnotations(typeElement).let { scopeAnnotations ->
        ProcessorErrors.checkState(
          scopeAnnotations.isEmpty(),
          typeElement,
          "@HiltViewModel classes should not be scoped. Found: %s",
          scopeAnnotations.joinToString { XAnnotations.toStableString(it) }
        )
      }

      return ViewModelMetadata(typeElement)
    }
  }
}
