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
import dagger.hilt.processor.internal.root.ir.AggregatedElementProxyIr
import java.io.File
import javax.lang.model.element.Modifier

internal class AggregatedElementProxyGenerator(
  private val outputDir: File,
) {

  fun generate(aggregatedElementProxy: AggregatedElementProxyIr) {
    val typeSpec = TypeSpec.classBuilder(aggregatedElementProxy.fqName)
      .addAnnotation(
        AnnotationSpec.builder(AGGREGATED_ELEMENT_PROXY_ANNOTATION)
          .addMember("value", "\$T.class", aggregatedElementProxy.value)
          .build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .build()
    JavaFile.builder(aggregatedElementProxy.fqName.packageName(), typeSpec)
      .build()
      .writeTo(outputDir)
  }

  companion object {
    val AGGREGATED_ELEMENT_PROXY_ANNOTATION =
      ClassName.get("dagger.hilt.android.internal.legacy", "AggregatedElementProxy")
  }
}
