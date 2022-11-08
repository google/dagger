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

package dagger.hilt.android.plugin.util

import org.gradle.api.Project

fun getAndroidComponentsExtension(project: Project): AndroidComponentsExtensionCompat {
  val version = SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION
  return when {
    version >= SimpleAGPVersion(7, 2) -> {
      AndroidComponentsExtensionCompatApi72Impl(project)
    }
    version >= SimpleAGPVersion(7, 1) -> {
      AndroidComponentsExtensionCompatApi71Impl(project)
    }
    version >= SimpleAGPVersion(7, 0) -> {
      AndroidComponentsExtensionCompatApi70Impl(project)
    }
    else -> {
      error("Android Gradle Plugin $version is not supported")
    }
  }
}
