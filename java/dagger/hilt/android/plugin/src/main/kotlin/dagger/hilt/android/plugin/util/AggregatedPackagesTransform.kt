package dagger.hilt.android.plugin.util

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
      input.isDirectory -> input.walkTopDown().filter { it.isFile }.forEach {
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
            val match = AGGREGATED_PACKAGES.any { aggregatedPackage ->
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
      val parentDirectory = file.parent
      val match = AGGREGATED_PACKAGES.any { aggregatedPackage ->
        parentDirectory.endsWith(aggregatedPackage)
      }
      if (match) {
        outputs.file(file)
      }
    }
  }

  companion object {
    // The list packages for generated classes used to pass information between compilation units.
    val AGGREGATED_PACKAGES = listOf(
      "dagger/hilt/android/internal/uninstallmodules/codegen",
      "dagger/hilt/internal/aggregatedroot/codegen",
      "dagger/hilt/internal/aggregatedrootsentinel/codegen",
      "dagger/hilt/processor/internal/aliasof/codegen",
      "dagger/hilt/processor/internal/definecomponent/codegen",
      "hilt_aggregated_deps",
    )

    // The output file name containing classes in the aggregated packages.
    val JAR_NAME = "hiltAggregated.jar"
  }
}
