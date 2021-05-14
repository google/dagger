package dagger.hilt.android.plugin.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.objectweb.asm.Opcodes

/**
 * Aggregates Hilt component dependencies from the compile classpath and outputs Java sources
 * with shareable component trees.
 *
 * The [compileClasspath] input is expected to contain jars or classes transformed by
 * [dagger.hilt.android.plugin.util.AggregatedPackagesTransform].
 */
@CacheableTask
abstract class AggregateDepsTask : DefaultTask() {

  // TODO(danysantiago): Make @Incremental and try to use @CompileClasspath
  @get:Classpath
  abstract val compileClasspath: ConfigurableFileCollection

  @get:Input
  @get:Optional
  abstract val asmApiVersion: Property<Int>

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  abstract val testEnvironment: Property<Boolean>

  @TaskAction
  internal fun taskAction(inputs: InputChanges) {
    // TODO(danysantiago): Use Worker API, https://docs.gradle.org/current/userguide/worker_api.html
    val componentTrees = ComponentTreeDepsAggregator(
      logger = logger,
      asmApiVersion = asmApiVersion.getOrNull() ?: Opcodes.ASM7,
      isTestEnvironment = testEnvironment.get()
    ).process(compileClasspath)
    ComponentTreeDepsGenerator(outputDir.get().asFile).apply {
      componentTrees.forEach { generate(it) }
    }
  }
}
