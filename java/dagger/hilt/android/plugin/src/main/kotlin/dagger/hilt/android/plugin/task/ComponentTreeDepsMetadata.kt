package dagger.hilt.android.plugin.task

import com.squareup.javapoet.ClassName

/**
 * Represents the data for a class annotated with `@ComponentTreeDeps`.
 */
// TODO(danysantiago): Replace by shared logic in RootProcessor
internal data class ComponentTreeDepsMetadata(
  val className: ClassName,
  val rootDeps: Set<String>,
  val defineComponentDeps: Set<String>,
  val aliasOfDeps: Set<String>,
  val aggregatedDeps: Set<String>,
  val uninstallModulesDeps: Set<String>,
  val earlyEntryPointDeps: Set<String>
) {

  companion object {
    const val TESTING_ROOT_PACKAGE = "dagger.hilt.android.internal.testing.root"
  }
}
