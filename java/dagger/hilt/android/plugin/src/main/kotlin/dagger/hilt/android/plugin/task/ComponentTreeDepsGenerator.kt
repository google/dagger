package dagger.hilt.android.plugin.task

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import java.io.File
import javax.lang.model.element.Modifier

/**
 * Generates @ComponentTreeDeps annotated sources.
 */
// TODO(danysantiago): Replace by shared logic in RootProcessor
internal class ComponentTreeDepsGenerator(
  private val outputDir: File,
) {
  fun generate(componentTreeMetadata: ComponentTreeDepsMetadata) {
    val typeSpec = TypeSpec.classBuilder(componentTreeMetadata.className)
      .addAnnotation(
        AnnotationSpec.builder(COMPONENT_TREE_DEPS_ANNOTATION).apply {
          componentTreeMetadata.rootDeps.toClassNames().forEach {
            addMember("rootDeps", "\$T.class", it)
          }
          componentTreeMetadata.defineComponentDeps.toClassNames().forEach {
            addMember("defineComponentDeps", "\$T.class", it)
          }
          componentTreeMetadata.aliasOfDeps.toClassNames().forEach {
            addMember("aliasOfDeps", "\$T.class", it)
          }
          componentTreeMetadata.aggregatedDeps.toClassNames().forEach {
            addMember("aggregatedDeps", "\$T.class", it)
          }
          componentTreeMetadata.uninstallModulesDeps.toClassNames().forEach {
            addMember("uninstallModulesDeps", "\$T.class", it)
          }
          componentTreeMetadata.earlyEntryPointDeps.toClassNames().forEach {
            addMember("earlyEntryPointDeps", "\$T.class", it)
          }
        }.build()
      )
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .build()
    JavaFile.builder(componentTreeMetadata.className.packageName(), typeSpec)
      .build()
      .writeTo(outputDir)
  }

  private fun Collection<String>.toClassNames() = sorted().map { fqName ->
    ClassName.get(
      fqName.substringBeforeLast('.'),
      fqName.substringAfterLast('.')
    )
  }

  companion object {
    val COMPONENT_TREE_DEPS_ANNOTATION =
      ClassName.get("dagger.hilt.internal.componenttreedeps", "ComponentTreeDeps")
  }
}
