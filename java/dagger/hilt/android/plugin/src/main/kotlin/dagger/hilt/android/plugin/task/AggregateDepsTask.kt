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

package dagger.hilt.android.plugin.task

import dagger.hilt.android.plugin.root.AggregatedElementProxyGenerator
import dagger.hilt.android.plugin.root.ComponentTreeDepsGenerator
import dagger.hilt.android.plugin.root.ProcessedRootSentinelGenerator
import dagger.hilt.processor.internal.root.ir.AggregatedRootIrValidator
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIrCreator
import javax.inject.Inject
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
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory

/**
 * Aggregates Hilt component dependencies from the compile classpath and outputs Java sources
 * with shareable component trees.
 *
 * The [compileClasspath] input is expected to contain jars or classes transformed by
 * [dagger.hilt.android.plugin.util.AggregatedPackagesTransform].
 */
@CacheableTask
abstract class AggregateDepsTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

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

  @get:Input
  abstract val crossCompilationRootValidationDisabled: Property<Boolean>

  @TaskAction
  internal fun taskAction(@Suppress("UNUSED_PARAMETER") inputs: InputChanges) {
    workerExecutor.noIsolation().submit(WorkerAction::class.java) {
      it.compileClasspath.from(compileClasspath)
      it.asmApiVersion.set(asmApiVersion)
      it.outputDir.set(outputDir)
      it.testEnvironment.set(testEnvironment)
      it.crossCompilationRootValidationDisabled.set(crossCompilationRootValidationDisabled)
    }
  }

  internal interface Parameters : WorkParameters {
    val compileClasspath: ConfigurableFileCollection
    val asmApiVersion: Property<Int>
    val outputDir: DirectoryProperty
    val testEnvironment: Property<Boolean>
    val crossCompilationRootValidationDisabled: Property<Boolean>
  }

  abstract class WorkerAction : WorkAction<Parameters> {
    override fun execute() {
      // Logger is not an injectable service yet: https://github.com/gradle/gradle/issues/16991
      val logger = LoggerFactory.getLogger(AggregateDepsTask::class.java)
      val aggregator = Aggregator.from(
        logger = logger,
        asmApiVersion = parameters.asmApiVersion.getOrNull() ?: Opcodes.ASM7,
        input = parameters.compileClasspath
      )
      val rootsToProcess = AggregatedRootIrValidator.rootsToProcess(
        isCrossCompilationRootValidationDisabled =
          parameters.crossCompilationRootValidationDisabled.get(),
        processedRoots = aggregator.processedRoots,
        aggregatedRoots = aggregator.aggregatedRoots
      )
      if (rootsToProcess.isEmpty()) {
        return
      }
      val componentTrees = ComponentTreeDepsIrCreator.components(
        isTest = parameters.testEnvironment.get(),
        isSharedTestComponentsEnabled = true,
        aggregatedRoots = rootsToProcess,
        defineComponentDeps = aggregator.defineComponentDeps,
        aliasOfDeps = aggregator.aliasOfDeps,
        aggregatedDeps = aggregator.aggregatedDeps,
        aggregatedUninstallModulesDeps = aggregator.uninstallModulesDeps,
        aggregatedEarlyEntryPointDeps = aggregator.earlyEntryPointDeps,
      )
      ComponentTreeDepsGenerator(
        proxies = aggregator.allAggregatedDepProxies.associate { it.value to it.fqName },
        outputDir = parameters.outputDir.get().asFile
      ).let { generator ->
        componentTrees.forEach { generator.generate(it) }
      }
      AggregatedElementProxyGenerator(parameters.outputDir.get().asFile).let { generator ->
        (aggregator.allAggregatedDepProxies - aggregator.aggregatedDepProxies).forEach {
          generator.generate(it)
        }
      }
      ProcessedRootSentinelGenerator(parameters.outputDir.get().asFile).let { generator ->
        rootsToProcess.map { it.root }.forEach { generator.generate(it) }
      }
    }
  }
}
