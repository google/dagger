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
import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.addOriginatingElement
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
 *     @LazyClassKey(pkg.$)
 *     @HiltViewModelMap
 *     public abstract ViewModel bind($ vm)
 *   }
 *   @Module
 *   @InstallIn(ActivityRetainedComponent.class)
 *   public static final class KeyModule {
 *     private static String className = "pkg.$";
 *     @Provides
 *     @IntoMap
 *     @HiltViewModelMap.KeySet
 *     @LazyClassKey(pkg.$)
 *     public static boolean provide() {
 *       return true;
 *     }
 *   }
 * }
 * ```
 */
@OptIn(
    ExperimentalProcessingApi::class,
    com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview::class
)
internal class ViewModelModuleGenerator(
  private val processingEnv: XProcessingEnv,
  private val viewModelMetadata: ViewModelMetadata,
) {
  fun generate() {
    val modulesTypeSpec =
      TypeSpec.classBuilder(viewModelMetadata.modulesClassName)
        .apply {
          addOriginatingElement(viewModelMetadata.viewModelElement)
          Processors.addGeneratedAnnotation(this, processingEnv, ViewModelProcessor::class.java)
          addAnnotation(
            AnnotationSpec.builder(ClassNames.ORIGINATING_ELEMENT)
              .addMember(
                "topLevelClass",
                "$T.class",
                viewModelMetadata.className.topLevelClassName(),
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
      JavaFile.builder(viewModelMetadata.modulesClassName.packageName(), modulesTypeSpec).build()
    )
  }

  private fun getBindsModuleTypeSpec() =
    createModuleTypeSpec(
        className = "BindsModule",
        component = AndroidClassNames.VIEW_MODEL_COMPONENT,
      )
      .addModifiers(Modifier.ABSTRACT)
      .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
      .addMethod(
        if (viewModelMetadata.assistedFactory.asClassName() != XTypeName.ANY_OBJECT) {
          getAssistedViewModelBindsMethod()
        } else {
          getViewModelBindsMethod()
        }
      )
      .build()

  private fun getViewModelBindsMethod() =
    MethodSpec.methodBuilder("binds")
      .addAnnotation(ClassNames.BINDS)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", viewModelMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_VIEW_MODEL_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .returns(AndroidClassNames.VIEW_MODEL)
      .addParameter(viewModelMetadata.className, "vm")
      .build()

  private fun getKeyModuleTypeSpec() =
    createModuleTypeSpec(
        className = "KeyModule",
        component = AndroidClassNames.ACTIVITY_RETAINED_COMPONENT,
      )
      .addModifiers(Modifier.FINAL)
      .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
      .addMethod(getViewModelKeyProvidesMethod())
      .build()

  private fun getViewModelKeyProvidesMethod() =
    MethodSpec.methodBuilder("provide")
      .addAnnotation(ClassNames.PROVIDES)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", viewModelMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_VIEW_MODEL_KEYS_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(Boolean::class.java)
      .addStatement("return true")
      .build()

  /**
   * Should generate:
   * ```
   * @Binds
   * @IntoMap
   * @LazyClassKey(pkg.FooViewModel.class)
   * @HiltViewModelAssistedMap
   * public abstract Object bind(FooViewModelAssistedFactory factory);
   * ```
   *
   * So that we have a HiltViewModelAssistedMap that maps from fully qualified ViewModel names to
   * its assisted factory instance.
   */
  private fun getAssistedViewModelBindsMethod() =
    MethodSpec.methodBuilder("bind")
      .addAnnotation(ClassNames.BINDS)
      .addAnnotation(ClassNames.INTO_MAP)
      .addAnnotation(
        AnnotationSpec.builder(ClassNames.LAZY_CLASS_KEY)
          .addMember("value", "$T.class", viewModelMetadata.className)
          .build()
      )
      .addAnnotation(AndroidClassNames.HILT_VIEW_MODEL_ASSISTED_FACTORY_MAP_QUALIFIER)
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .addParameter(viewModelMetadata.assistedFactoryClassName, "factory")
      .returns(TypeName.OBJECT)
      .build()

  private fun createModuleTypeSpec(className: String, component: ClassName) =
    TypeSpec.classBuilder(className)
      .addOriginatingElement(viewModelMetadata.viewModelElement)
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
  }
}
