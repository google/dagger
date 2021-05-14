package dagger.hilt.android.plugin.util

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath

/**
 * A transform that registers the input file (usually a jar or a class) as an output and thus
 * changing from one artifact type to another.
 */
@CacheableTransform
abstract class CopyTransform : TransformAction<TransformParameters.None> {
  @get:Classpath
  @get:InputArtifact
  abstract val inputArtifactProvider: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val input = inputArtifactProvider.get().asFile
    when {
      input.isDirectory -> outputs.dir(input)
      input.isFile -> outputs.file(input)
      else -> error("File/directory does not exist: ${input.absolutePath}")
    }
  }
}
