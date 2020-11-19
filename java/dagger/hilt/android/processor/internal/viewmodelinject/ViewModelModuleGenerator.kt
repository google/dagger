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

package dagger.hilt.android.processor.internal.viewmodelinject

import com.google.auto.common.GeneratedAnnotationSpecs
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
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
 *   public static final class ProvidesModule {
 *     @Provides
 *     @IntoMap
 *     @StringKey("pkg.$")
 *     @InternalViewModelInjectMap
 *     public static ViewModel provide(dep1, Dep1, dep2, Dep2, ...) {
 *       return new $(dep1, dep2, ...)
 *     }
 *   }
 *   @Module
 *   @InstallIn(ActivityRetainedComponent.class)
 *   public static final class KeyModule {
 *     @Provides
 *     @IntoSet
 *     @InternalViewModelInjectMap.KeySet
 *     public static String provide() {
 *      return "pkg.$";
 *     }
 *   }
 * }
 * ```
 */
internal class ViewModelModuleGenerator(
  private val processingEnv: ProcessingEnvironment,
  private val injectedViewModel: ViewModelInjectMetadata
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
      .addType(getProviderModuleTypeSpec())
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

  private fun getProviderModuleTypeSpec() =
    createModuleTypeSpec(
      className = "ProvidesModule",
      component = AndroidClassNames.VIEW_MODEL_COMPONENT
    ).addMethod(getViewModelProvidesMethod()).build()

  private fun getViewModelProvidesMethod() =
    MethodSpec.methodBuilder("provide")
      .addAnnotation(ClassNames.PROVIDES)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.STRING_KEY)
          .addMember("value", S, injectedViewModel.className.reflectionName())
          .build()
      )
      .addAnnotation(AndroidClassNames.VIEW_MODEL_INJECT_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(AndroidClassNames.VIEW_MODEL)
      .apply {
        injectedViewModel.dependencyRequests.forEach { dependency ->
          addParameter(
            ParameterSpec.builder(dependency.type, dependency.name)
              .apply {
                dependency.qualifier?.let { addAnnotation(it) }
              }.build()
          )
        }
        val constructorArgs = injectedViewModel.dependencyRequests.map {
          CodeBlock.of(L, it.name)
        }
        addStatement(
          "return new $T($L)",
          injectedViewModel.className,
          CodeBlock.join(constructorArgs, ",$W")
        )
      }
      .build()

  private fun getKeyModuleTypeSpec() =
    createModuleTypeSpec(
      className = "KeyModule",
      component = AndroidClassNames.ACTIVITY_RETAINED_COMPONENT
    ).addMethod(getViewModelKeyProvidesMethod()).build()

  private fun getViewModelKeyProvidesMethod() =
    MethodSpec.methodBuilder("provide")
      .addAnnotation(ClassNames.PROVIDES)
      .addAnnotation(ClassNames.INTO_SET)
      .addAnnotation(AndroidClassNames.VIEW_MODEL_INJECT_MAP_KEYS_QUALIFIER)
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
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
      .addMethod(
        MethodSpec.constructorBuilder()
          .addModifiers(Modifier.PRIVATE)
          .build()
      )

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
        ViewModelInjectProcessor::class.java
      ).ifPresent { generatedAnnotation ->
        addAnnotation(generatedAnnotation)
      }
    }
  }
}
