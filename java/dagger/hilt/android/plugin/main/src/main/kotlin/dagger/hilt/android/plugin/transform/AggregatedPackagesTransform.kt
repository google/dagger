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

package dagger.hilt.android.plugin.transform

import dagger.hilt.android.plugin.root.AggregatedAnnotation
import dagger.hilt.android.plugin.util.forEachZipEntry
import dagger.hilt.android.plugin.util.isClassFile
import dagger.hilt.android.plugin.util.isJarFile
import dagger.hilt.android.plugin.util.walkInPlatformIndependentOrder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath

/**
 * A transform that outputs classes and jars containing only classes in key aggregating Hilt
 * packages that are used to pass dependencies between compilation units.
 */
@CacheableTransform
abstract class AggregatedPackagesTransform : TransformAction<TransformParameters.None> {
  // TODO(danysantiago): Make incremental by using InputChanges and try to use @CompileClasspath
  @get:Classpath
  @get:InputArtifact
  abstract val inputArtifactProvider: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val input = inputArtifactProvider.get().asFile
    when {
      input.isFile -> transformFile(outputs, input)
      input.isDirectory -> input.walkInPlatformIndependentOrder().filter { it.isFile }.forEach {
        transformFile(outputs, it)
      }
      else -> error("File/directory does not exist: ${input.absolutePath}")
    }
  }

  private fun transformFile(outputs: TransformOutputs, file: File) {
    if (file.isJarFile()) {
      var atLeastOneEntry = false
      // TODO(danysantiago): This is an in-memory buffer stream, consider using a temp file.
      val tmpOutputStream = ByteArrayOutputStream()
      ZipOutputStream(tmpOutputStream).use { outputStream ->
        ZipInputStream(file.inputStream()).forEachZipEntry { inputStream, inputEntry ->
          if (inputEntry.isClassFile()) {
            val parentDirectory = inputEntry.name.substringBeforeLast('/')
            val match = AggregatedAnnotation.AGGREGATED_PACKAGES.any { aggregatedPackage ->
              parentDirectory.endsWith(aggregatedPackage)
            }
            if (match) {
              outputStream.putNextEntry(ZipEntry(inputEntry.name))
              inputStream.copyTo(outputStream)
              outputStream.closeEntry()
              atLeastOneEntry = true
            }
          }
        }
      }
      if (atLeastOneEntry) {
        outputs.file(JAR_NAME).outputStream().use { tmpOutputStream.writeTo(it) }
      }
    } else if (file.isClassFile()) {
      // If transforming a file, check if the parent directory matches one of the known aggregated
      // packages structure. File and Path APIs are used to avoid OS-specific issues when comparing
      // paths.
      val parentDirectory: File = file.parentFile
      val match = AggregatedAnnotation.AGGREGATED_PACKAGES.any { aggregatedPackage ->
        parentDirectory.endsWith(aggregatedPackage)
      }
      if (match) {
        outputs.file(file)
      }
    }
  }

  companion object {
    // The output file name containing classes in the aggregated packages.
    val JAR_NAME = "hiltAggregated.jar"
  }
}
