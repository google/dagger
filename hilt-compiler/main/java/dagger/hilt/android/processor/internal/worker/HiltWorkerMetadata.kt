/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.worker

import androidx.room3.compiler.codegen.toJavaPoet
import androidx.room3.compiler.processing.ExperimentalProcessingApi
import androidx.room3.compiler.processing.XProcessingEnv
import androidx.room3.compiler.processing.XTypeElement
import com.squareup.javapoet.ClassName
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.ClassNames
import dagger.hilt.processor.internal.LazyString
import dagger.hilt.processor.internal.ProcessorErrors
import dagger.hilt.processor.internal.Processors
import dagger.internal.codegen.xprocessing.XAnnotations
import dagger.internal.codegen.xprocessing.XElements
import dagger.internal.codegen.xprocessing.XTypes

@OptIn(
  ExperimentalProcessingApi::class,
  com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview::class
)
internal class HiltWorkerMetadata
private constructor(
  val workerElement: XTypeElement,
  val isAssistedInject: Boolean,
) {
  val className = workerElement.asClassName().toJavaPoet()

  val assistedFactoryClassName: ClassName =
    ClassName.get(workerElement.packageName, "${className.simpleNames().joinToString("_")}_AssistedFactory")

  val modulesClassName =
    ClassName.get(
      workerElement.packageName,
      "${className.simpleNames().joinToString("_")}_HiltModules",
    )

  companion object {
    internal fun create(
      processingEnv: XProcessingEnv,
      workerElement: XTypeElement,
    ): HiltWorkerMetadata? {
      ProcessorErrors.checkState(
        XTypes.isSubtype(
          workerElement.type,
          processingEnv.requireType(AndroidClassNames.LISTENABLE_WORKER),
        ),
        workerElement,
        "@HiltWorker is only supported on types that subclass %s.",
        AndroidClassNames.LISTENABLE_WORKER,
      )

      val injectConstructors =
        workerElement.getConstructors().filter { constructor ->
          Processors.isAnnotatedWithInject(constructor) ||
            constructor.hasAnnotation(ClassNames.ASSISTED_INJECT)
        }

      ProcessorErrors.checkState(
        injectConstructors.size == 1,
        workerElement,
        "@HiltWorker annotated class should contain exactly one @Inject or @AssistedInject annotated constructor.",
      )

      val injectConstructor = injectConstructors.single()

      ProcessorErrors.checkState(
        !injectConstructor.isPrivate(),
        injectConstructor,
        "%s annotated constructors must not be private.",
        if (injectConstructor.hasAnnotation(ClassNames.ASSISTED_INJECT)) {
          "@Inject or @AssistedInject"
        } else {
          "@Inject"
        },
      )

      ProcessorErrors.checkState(
        !workerElement.isNested() || workerElement.isStatic(),
        workerElement,
        "@HiltWorker may only be used on inner classes if they are static.",
      )

      Processors.getScopeAnnotations(workerElement).let { scopeAnnotations ->
        ProcessorErrors.checkState(
          scopeAnnotations.isEmpty(),
          workerElement,
          "@HiltWorker classes should not be scoped. Found: %s",
          LazyString.of { scopeAnnotations.joinToString { XAnnotations.toStableString(it) } },
        )
      }

      val isAssistedInject = injectConstructor.hasAnnotation(ClassNames.ASSISTED_INJECT)

      return HiltWorkerMetadata(workerElement, isAssistedInject)
    }
  }
}
