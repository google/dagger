/*
 * Copyright (C) 2022 The Dagger Authors.
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

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault

/**
 * A transform that registers the input file (usually a jar or a class) as an output and thus
 * changing from one artifact type to another.
 */
@DisableCachingByDefault(because = "Copying files does not benefit from caching")
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
