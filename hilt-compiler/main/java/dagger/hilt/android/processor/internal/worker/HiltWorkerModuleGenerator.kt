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

import androidx.room3.compiler.processing.ExperimentalProcessingApi
import androidx.room3.compiler.processing.XProcessingEnv
import androidx.room3.compiler.processing.addOriginatingElement
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.ClassNames
import dagger.hilt.processor.internal.Processors
import javax.lang.model.element.Modifier

@OptIn(
  ExperimentalProcessingApi::class,
  com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview::class
)
internal class HiltWorkerModuleGenerator(
  private val processingEnv: XProcessingEnv,
  private val workerMetadata: HiltWorkerMetadata,
) {
  fun generate() {
    generateModules()

    if (workerMetadata.isAssistedInject) {
      generateAssistedFactory()
    }
  }

  private fun generateModules() {
    val modulesTypeSpec =
      TypeSpec.classBuilder(workerMetadata.modulesClassName)
        .apply {
          addOriginatingElement(workerMetadata.workerElement)
          Processors.addGeneratedAnnotation(this, processingEnv, HiltWorkerProcessor::class.java)
          addAnnotation(
            AnnotationSpec.builder(ClassNames.ORIGINATING_ELEMENT)
              .addMember(
                "topLevelClass",
                "$T.class",
                workerMetadata.className.topLevelClassName(),
              )
              .build()
          )
          addModifiers(Modifier.PUBLIC, Modifier.FINAL)
          addType(getBindsModuleTypeSpec())
          addType(getKeyModuleTypeSpec())
          addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        }
        .build()

    processingEnv.filer.write(
      JavaFile.builder(workerMetadata.modulesClassName.packageName(), modulesTypeSpec).build()
    )
  }

  private fun getBindsModuleTypeSpec() =
    createModuleTypeSpec("BindsModule")
      .addModifiers(Modifier.ABSTRACT)
      .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
      .addMethod(
        if (workerMetadata.isAssistedInject) getAssistedWorkerBindsMethod()
        else getWorkerBindsMethod()
      )
      .build()

  private fun getWorkerBindsMethod() =
    MethodSpec.methodBuilder("binds")
      .addAnnotation(ClassNames.BINDS)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", workerMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_WORKER_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .returns(AndroidClassNames.LISTENABLE_WORKER)
      .addParameter(workerMetadata.className, "worker")
      .build()

  private fun getAssistedWorkerBindsMethod() =
    MethodSpec.methodBuilder("bind")
      .addAnnotation(ClassNames.BINDS)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", workerMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_WORKER_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .addParameter(workerMetadata.assistedFactoryClassName, "factory")
      .returns(TypeName.OBJECT)
      .build()

  private fun getKeyModuleTypeSpec() =
    createModuleTypeSpec("KeyModule")
      .addModifiers(Modifier.FINAL)
      .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
      .addMethod(getWorkerKeyProvidesMethod())
      .build()

  private fun getWorkerKeyProvidesMethod() =
    MethodSpec.methodBuilder("provide")
      .addAnnotation(ClassNames.PROVIDES)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", workerMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_WORKER_KEYS_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(Boolean::class.java)
      .addStatement("return true")
      .build()

  private fun createModuleTypeSpec(innerClassName: String) =
    TypeSpec.classBuilder(innerClassName)
      .addOriginatingElement(workerMetadata.workerElement)
      .addAnnotation(ClassNames.MODULE)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.INSTALL_IN)
          .addMember("value", "$T.class", ClassNames.SINGLETON_COMPONENT)
          .build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)

  /**
   * Generates an @AssistedFactory interface for the worker class.
   *
   * Should generate:
   * ```
   * @AssistedFactory
   * interface PeriodicReminderWorker_AssistedFactory {
   *   PeriodicReminderWorker create(Context context, WorkerParameters params);
   * }
   * ```
   */
  private fun generateAssistedFactory() {
    val factoryTypeSpec =
      TypeSpec.interfaceBuilder(workerMetadata.assistedFactoryClassName)
        .apply {
          addOriginatingElement(workerMetadata.workerElement)
          Processors.addGeneratedAnnotation(this, processingEnv, HiltWorkerProcessor::class.java)
          addAnnotation(ClassNames.ASSISTED_FACTORY)
          addModifiers(Modifier.PUBLIC)
          addMethod(getCreateMethod())
        }
        .build()

    processingEnv.filer.write(
      JavaFile.builder(
        workerMetadata.assistedFactoryClassName.packageName(), factoryTypeSpec
      ).build()
    )
  }

  private fun getCreateMethod() =
    MethodSpec.methodBuilder("create")
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .addParameter(AndroidClassNames.CONTEXT, "context")
      .addParameter(AndroidClassNames.WORKER_PARAMETERS, "workerParameters")
      .returns(workerMetadata.className)
      .build()
}
