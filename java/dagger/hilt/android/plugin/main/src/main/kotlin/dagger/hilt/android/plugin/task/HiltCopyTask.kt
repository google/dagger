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

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CacheableTask
abstract class HiltCopyTask : Copy() {

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val classesDirectories: ListProperty<Directory>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val classesJars: ListProperty<RegularFile>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val testedClassesDirectories: ListProperty<Directory>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val testedClassesJars: ListProperty<RegularFile>
}