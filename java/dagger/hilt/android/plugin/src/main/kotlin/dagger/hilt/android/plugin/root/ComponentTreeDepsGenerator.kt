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
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr
import java.io.File
import javax.lang.model.element.Modifier

/** Generates @ComponentTreeDeps annotated sources. */
internal class ComponentTreeDepsGenerator(
  private val proxies: Map<ClassName, ClassName>,
  private val outputDir: File,
) {
  fun generate(componentTree: ComponentTreeDepsIr) {
    val typeSpec = TypeSpec.classBuilder(componentTree.name)
      .addAnnotation(
        AnnotationSpec.builder(COMPONENT_TREE_DEPS_ANNOTATION).apply {
          componentTree.rootDeps.toMaybeProxies().forEach {
            addMember("rootDeps", "\$T.class", it)
          }
          componentTree.defineComponentDeps.toMaybeProxies().forEach {
            addMember("defineComponentDeps", "\$T.class", it)
          }
          componentTree.aliasOfDeps.toMaybeProxies().forEach {
            addMember("aliasOfDeps", "\$T.class", it)
          }
          componentTree.aggregatedDeps.toMaybeProxies().forEach {
            addMember("aggregatedDeps", "\$T.class", it)
          }
          componentTree.uninstallModulesDeps.toMaybeProxies().forEach {
            addMember("uninstallModulesDeps", "\$T.class", it)
          }
          componentTree.earlyEntryPointDeps.toMaybeProxies().forEach {
            addMember("earlyEntryPointDeps", "\$T.class", it)
          }
        }.build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .build()
    JavaFile.builder(componentTree.name.packageName(), typeSpec)
      .build()
      .writeTo(outputDir)
  }

  private fun Collection<ClassName>.toMaybeProxies() =
    sorted().map { fqName -> proxies[fqName] ?: fqName }

  companion object {
    val COMPONENT_TREE_DEPS_ANNOTATION: ClassName =
      ClassName.get("dagger.hilt.internal.componenttreedeps", "ComponentTreeDeps")
  }
}
