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

import androidx.room.compiler.codegen.XTypeName
import androidx.room.compiler.codegen.toJavaPoet
import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XTypeElement
import com.squareup.javapoet.ClassName
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.ClassNames
import dagger.hilt.processor.internal.HiltCompilerOptions
import dagger.hilt.processor.internal.ProcessorErrors
import dagger.hilt.processor.internal.Processors
import dagger.internal.codegen.xprocessing.XAnnotations
import dagger.internal.codegen.xprocessing.XElements
import dagger.internal.codegen.xprocessing.XTypeElements
import dagger.internal.codegen.xprocessing.XTypes

/** Data class that represents a Hilt injected ViewModel */
@OptIn(
  ExperimentalProcessingApi::class,
  com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview::class
)
internal class ViewModelMetadata
private constructor(val viewModelElement: XTypeElement, val assistedFactory: XTypeElement) {
  val className = viewModelElement.asClassName().toJavaPoet()

  val assistedFactoryClassName: ClassName = assistedFactory.asClassName().toJavaPoet()

  val modulesClassName =
    ClassName.get(
      viewModelElement.packageName,
      "${className.simpleNames().joinToString("_")}_HiltModules",
    )

  companion object {

    private const val ASSISTED_FACTORY_VALUE = "assistedFactory"

    fun getAssistedFactoryMethods(factory: XTypeElement?): List<XMethodElement> {
      return XTypeElements.getAllNonPrivateInstanceMethods(factory)
        .filter { it.isAbstract() }
        .filter { !it.isJavaDefault() }
    }

    internal fun create(
      processingEnv: XProcessingEnv,
      viewModelElement: XTypeElement,
    ): ViewModelMetadata? {
      ProcessorErrors.checkState(
        XTypes.isSubtype(
          viewModelElement.type,
          processingEnv.requireType(AndroidClassNames.VIEW_MODEL),
        ),
        viewModelElement,
        "@HiltViewModel is only supported on types that subclass %s.",
        AndroidClassNames.VIEW_MODEL,
      )

      val isAssistedInjectFeatureEnabled =
        HiltCompilerOptions.isAssistedInjectViewModelsEnabled(viewModelElement)

      val assistedFactoryType =
        viewModelElement
          .requireAnnotation(AndroidClassNames.HILT_VIEW_MODEL)
          .getAsType(ASSISTED_FACTORY_VALUE)
      val assistedFactory = assistedFactoryType.typeElement!!

      if (assistedFactoryType.asTypeName() != XTypeName.ANY_OBJECT) {
        ProcessorErrors.checkState(
          isAssistedInjectFeatureEnabled,
          viewModelElement,
          "Specified assisted factory %s for %s in @HiltViewModel but compiler option 'enableAssistedInjectViewModels' was not enabled.",
          assistedFactoryType.asTypeName().toJavaPoet(),
          XElements.toStableString(viewModelElement),
        )

        ProcessorErrors.checkState(
          assistedFactory.hasAnnotation(ClassNames.ASSISTED_FACTORY),
          viewModelElement,
          "Class %s is not annotated with @AssistedFactory.",
          assistedFactoryType.asTypeName().toJavaPoet(),
        )

        val assistedFactoryMethod = getAssistedFactoryMethods(assistedFactory).singleOrNull()

        ProcessorErrors.checkState(
          assistedFactoryMethod != null,
          assistedFactory,
          "Cannot find assisted factory method in %s.",
          XElements.toStableString(assistedFactory),
        )

        val assistedFactoryMethodType = assistedFactoryMethod!!.asMemberOf(assistedFactoryType)

        ProcessorErrors.checkState(
          assistedFactoryMethodType.returnType
            .asTypeName()
            .equalsIgnoreNullability(viewModelElement.asClassName()),
          assistedFactoryMethod,
          "Class %s must have a factory method that returns a %s. Found %s.",
          XElements.toStableString(assistedFactory),
          XElements.toStableString(viewModelElement),
          XTypes.toStableString(assistedFactoryMethodType.returnType),
        )
      }

      val injectConstructors =
        viewModelElement.getConstructors().filter { constructor ->
          if (isAssistedInjectFeatureEnabled) {
            Processors.isAnnotatedWithInject(constructor) ||
              constructor.hasAnnotation(ClassNames.ASSISTED_INJECT)
          } else {
            ProcessorErrors.checkState(
              !constructor.hasAnnotation(ClassNames.ASSISTED_INJECT),
              constructor,
              "ViewModel constructor should be annotated with @Inject instead of @AssistedInject.",
            )
            Processors.isAnnotatedWithInject(constructor)
          }
        }

      val injectAnnotationsMessage =
        if (isAssistedInjectFeatureEnabled) {
          "@Inject or @AssistedInject"
        } else {
          "@Inject"
        }

      ProcessorErrors.checkState(
        injectConstructors.size == 1,
        viewModelElement,
        "@HiltViewModel annotated class should contain exactly one %s annotated constructor.",
        injectAnnotationsMessage,
      )

      val injectConstructor = injectConstructors.single()

      ProcessorErrors.checkState(
        !injectConstructor.isPrivate(),
        injectConstructor,
        "%s annotated constructors must not be private.",
        injectAnnotationsMessage,
      )

      if (injectConstructor.hasAnnotation(ClassNames.ASSISTED_INJECT)) {
        // If "enableAssistedInjectViewModels" is not enabled we'll get error:
        // "ViewModel constructor should be annotated with @Inject instead of @AssistedInject."

        ProcessorErrors.checkState(
          assistedFactoryType.asTypeName() != XTypeName.ANY_OBJECT,
          viewModelElement,
          "%s must have a valid assisted factory specified in @HiltViewModel when used with assisted injection. Found %s.",
          XElements.toStableString(viewModelElement),
          XTypes.toStableString(assistedFactoryType),
        )
      } else {
        ProcessorErrors.checkState(
          assistedFactoryType.asTypeName() == XTypeName.ANY_OBJECT,
          injectConstructor,
          "Found assisted factory %s in @HiltViewModel but the constructor was annotated with @Inject instead of @AssistedInject.",
          XTypes.toStableString(assistedFactoryType),
        )
      }

      ProcessorErrors.checkState(
        !viewModelElement.isNested() || viewModelElement.isStatic(),
        viewModelElement,
        "@HiltViewModel may only be used on inner classes if they are static.",
      )

      Processors.getScopeAnnotations(viewModelElement).let { scopeAnnotations ->
        ProcessorErrors.checkState(
          scopeAnnotations.isEmpty(),
          viewModelElement,
          "@HiltViewModel classes should not be scoped. Found: %s",
          scopeAnnotations.joinToString { XAnnotations.toStableString(it) },
        )
      }

      return ViewModelMetadata(viewModelElement, assistedFactory)
    }
  }
}
