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

import com.google.auto.common.GeneratedAnnotationSpecs
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.ClassNames
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.util.Elements

/**
 * Source generator to support Hilt injection of ViewModels.
 *
 * Should generate:
 * ```
 * public final class $_HiltModules {
 *   @Module
 *   @InstallIn(ViewModelComponent.class)
 *   public static abstract class BindsModule {
 *     @Binds
 *     @IntoMap
 *     @StringKey("pkg.$")
 *     @HiltViewModelMap
 *     public abstract ViewModel bind($ vm)
 *   }
 *   @Module
 *   @InstallIn(ActivityRetainedComponent.class)
 *   public static final class KeyModule {
 *     @Provides
 *     @IntoSet
 *     @HiltViewModelMap.KeySet
 *     public static String provide() {
 *      return "pkg.$";
 *     }
 *   }
 * }
 * ```
 */
internal class ViewModelModuleGenerator(
  private val processingEnv: ProcessingEnvironment,
  private val injectedViewModel: ViewModelMetadata
) {
  fun generate() {
    val modulesTypeSpec = TypeSpec.classBuilder(injectedViewModel.modulesClassName)
      .addOriginatingElement(injectedViewModel.typeElement)
      .addGeneratedAnnotation(processingEnv.elementUtils, processingEnv.sourceVersion)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.ORIGINATING_ELEMENT)
          .addMember(
            "topLevelClass",
            "$T.class",
            injectedViewModel.className.topLevelClassName()
          )
          .build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .addType(getBindsModuleTypeSpec())
      .addType(getKeyModuleTypeSpec())
      .addMethod(
        MethodSpec.constructorBuilder()
          .addModifiers(Modifier.PRIVATE)
          .build()
      )
      .build()
    JavaFile.builder(injectedViewModel.modulesClassName.packageName(), modulesTypeSpec)
      .build()
      .writeTo(processingEnv.filer)
  }

  private fun getBindsModuleTypeSpec() = createModuleTypeSpec(
    className = "BindsModule",
    component = AndroidClassNames.VIEW_MODEL_COMPONENT
  )
    .addModifiers(Modifier.ABSTRACT)
    .addMethod(
      MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .build()
    )
    .addMethod(getViewModelBindsMethod())
    .build()

  private fun getViewModelBindsMethod() =
    MethodSpec.methodBuilder("binds")
      .addAnnotation(ClassNames.BINDS)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.STRING_KEY)
          .addMember("value", S, injectedViewModel.className.reflectionName())
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_VIEW_MODEL_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .returns(AndroidClassNames.VIEW_MODEL)
      .addParameter(injectedViewModel.className, "vm")
      .build()

  private fun getKeyModuleTypeSpec() = createModuleTypeSpec(
    className = "KeyModule",
    component = AndroidClassNames.ACTIVITY_RETAINED_COMPONENT
  )
    .addModifiers(Modifier.FINAL)
    .addMethod(
      MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .build()
    )
    .addMethod(getViewModelKeyProvidesMethod())
    .build()

  private fun getViewModelKeyProvidesMethod() =
    MethodSpec.methodBuilder("provide")
      .addAnnotation(ClassNames.PROVIDES)
      .addAnnotation(ClassNames.INTO_SET)
      .addAnnotation(AndroidClassNames.HILT_VIEW_MODEL_KEYS_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(String::class.java)
      .addStatement("return $S", injectedViewModel.className.reflectionName())
      .build()

  private fun createModuleTypeSpec(className: String, component: ClassName) =
    TypeSpec.classBuilder(className)
      .addOriginatingElement(injectedViewModel.typeElement)
      .addAnnotation(ClassNames.MODULE)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.INSTALL_IN)
          .addMember("value", "$T.class", component)
          .build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)

  companion object {

    const val L = "\$L"
    const val T = "\$T"
    const val N = "\$N"
    const val S = "\$S"
    const val W = "\$W"

    private fun TypeSpec.Builder.addGeneratedAnnotation(
      elements: Elements,
      sourceVersion: SourceVersion
    ) = apply {
      GeneratedAnnotationSpecs.generatedAnnotationSpec(
        elements,
        sourceVersion,
        ViewModelProcessor::class.java
      ).ifPresent { generatedAnnotation ->
        addAnnotation(generatedAnnotation)
      }
    }
  }
}
