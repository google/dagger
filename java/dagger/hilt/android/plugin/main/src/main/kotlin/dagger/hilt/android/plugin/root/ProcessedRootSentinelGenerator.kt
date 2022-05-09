/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.plugin.root

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import java.io.File
import javax.lang.model.element.Modifier

internal class ProcessedRootSentinelGenerator(
  private val outputDir: File,
) {

  fun generate(processedRootName: ClassName) {
    val className = ClassName.get(
      PROCESSED_ROOT_SENTINEL_GEN_PACKAGE,
      "_" + processedRootName.toString().replace('.', '_')
    )
    val typeSpec = TypeSpec.classBuilder(className)
      .addAnnotation(
        AnnotationSpec.builder(PROCESSED_ROOT_SENTINEL_ANNOTATION)
          .addMember("roots", "\$S", processedRootName)
          .build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .build()
    JavaFile.builder(PROCESSED_ROOT_SENTINEL_GEN_PACKAGE, typeSpec)
      .build()
      .writeTo(outputDir)
  }

  companion object {
    val PROCESSED_ROOT_SENTINEL_GEN_PACKAGE = "dagger.hilt.internal.processedrootsentinel.codegen"
    val PROCESSED_ROOT_SENTINEL_ANNOTATION =
      ClassName.get("dagger.hilt.internal.processedrootsentinel", "ProcessedRootSentinel")
  }
}
