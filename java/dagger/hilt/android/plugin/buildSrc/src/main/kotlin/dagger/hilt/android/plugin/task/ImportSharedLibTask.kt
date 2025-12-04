/*
 * Copyright (C) 2025 The Dagger Authors.
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

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class ImportSharedLibTask @Inject constructor(
  private val execOperations: ExecOperations
) : DefaultTask() {

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun execute() {
    val bazelOutput = ByteArrayOutputStream()
    val buildResult = execOperations.exec {
      this.commandLine(BUILD_CMD, "build", "import-shared-lib")
      this.standardOutput = bazelOutput
      this.errorOutput = bazelOutput
    }
    buildResult.assertNormalExitValue()

    val genFilesDir = project.buildFile.parentFile.findFileInPath(BUILD_DIR)
      ?: throw GradleException("Couldn't find build folder '$BUILD_DIR'.")

    val libPath = bazelOutput.toString().split('\n').find { it.contains("$BUILD_DIR/")}?.trim()
      ?: throw GradleException("Couldn't find library path in $BUILD_CMD's output ($BUILD_DIR).")

    val inputFile = project.file("$genFilesDir/$libPath")
    val outputFile = outputDir.file(inputFile.name).get().asFile
    inputFile.inputStream().use { input ->
      outputFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
  }

  companion object {
    const val BUILD_CMD = "bazel"
    const val BUILD_DIR = "bazel-bin"

    /** Finds the file in the current directory, its parent directories, or returns null. */
    private fun File?.findFileInPath(fileName: String): File? {
      if (this == null || !isDirectory) {
        return null
      }
      return if (File(this, fileName).exists()) {
        this
      } else {
        parentFile.findFileInPath(fileName)
      }
    }
  }
}