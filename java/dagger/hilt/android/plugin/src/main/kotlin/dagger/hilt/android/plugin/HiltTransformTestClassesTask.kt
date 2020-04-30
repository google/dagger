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
package dagger.hilt.android.plugin

import com.android.build.gradle.api.UnitTestVariant
import dagger.hilt.android.plugin.util.isClassFile
import dagger.hilt.android.plugin.util.isJarFile
import java.io.File
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

/**
 * Task that transform classes used by host-side unit tests. See b/37076369
 */
@Suppress("UnstableApiUsage")
abstract class HiltTransformTestClassesTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  @get:Classpath
  abstract val compiledClasses: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  internal interface Parameters : WorkParameters {
    val name: Property<String>
    val compiledClasses: ConfigurableFileCollection
    val outputDir: DirectoryProperty
  }

  abstract class WorkerAction : WorkAction<Parameters> {
    override fun execute() {
      val outputDir = parameters.outputDir.asFile.get()
      outputDir.deleteRecursively()
      outputDir.mkdirs()

      val classTransformer = AndroidEntryPointClassTransformer(
        taskName = parameters.name.get(),
        allInputs = parameters.compiledClasses.files.toList(),
        sourceRootOutputDir = outputDir,
        copyNonTransformed = false
      )
      // Parse the classpath in reverse so that we respect overwrites, if it ever happens.
      parameters.compiledClasses.files.reversed().forEach {
        if (it.isDirectory) {
          it.walkTopDown().forEach { file ->
            if (file.isClassFile()) {
              classTransformer.transformFile(file)
            }
          }
        } else if (it.isJarFile()) {
          classTransformer.transformJarContents(it)
        }
      }
    }
  }

  @TaskAction
  fun transformClasses() {
    workerExecutor.noIsolation().submit(WorkerAction::class.java) {
      it.compiledClasses.from(compiledClasses)
      it.outputDir.set(outputDir)
      it.name.set(name)
    }
  }

  internal class ConfigAction(
    private val outputDir: File,
    private val inputClasspath: FileCollection
  ) : Action<HiltTransformTestClassesTask> {
    override fun execute(transformTask: HiltTransformTestClassesTask) {
      transformTask.description = "Transforms AndroidEntryPoint annotated classes for JUnit tests."
      transformTask.outputDir.set(outputDir)
      transformTask.compiledClasses.from(inputClasspath)
    }
  }

  companion object {

    private const val TASK_PREFIX = "hiltTransformForJUnit"

    fun create(
      project: Project,
      unitTestVariant: UnitTestVariant,
      extension: HiltExtension
    ) {
      if (!extension.enableTransformForLocalTests) {
        // Not enabled, nothing to do here.
        return
      }
      val outputDir =
        project.buildDir.resolve("intermediates/hilt/${unitTestVariant.dirName}Output")
      val outputFileCollection = project.objects.fileCollection()
      val classpathKey = unitTestVariant.registerPreJavacGeneratedBytecode(
        outputFileCollection.from(outputDir)
      )
      val hiltTransformProvider = project.tasks.register(
        "$TASK_PREFIX${unitTestVariant.name.capitalize()}",
        HiltTransformTestClassesTask::class.java,
        ConfigAction(outputDir, unitTestVariant.getCompileClasspath(classpathKey))
      )
      outputFileCollection.builtBy(hiltTransformProvider)
    }
  }
}
