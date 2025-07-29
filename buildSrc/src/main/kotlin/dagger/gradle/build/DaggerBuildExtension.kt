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

package dagger.gradle.build

import org.gradle.api.Action

/** Extension for [DaggerConventionPlugin] that's responsible for holding configuration options. */
abstract class DaggerBuildExtension {

  internal val relocateRules = mutableMapOf<String, String>()

  /** The type of project */
  var type: SoftwareType = SoftwareType.JVM_LIBRARY

  /** Whether the project artifacts are published or not */
  var isPublished = false

  /** Shading configuration. */
  fun shading(configure: Action<ShadingSpec>) {
    configure.execute(
      object : ShadingSpec {
        override fun relocate(fromPackage: String, toPackage: String) {
          check(!relocateRules.containsKey(fromPackage)) {
            "Duplicate shading rule declared for $fromPackage"
          }
          relocateRules[fromPackage] = toPackage
        }
      }
    )
  }
}

/**
 * DSL for specifying relocation rules.
 *
 * Example usage:
 * ```
 * daggerBuild {
 *   shading {
 *     relocate("com.google.auto.common", "dagger.spi.internal.shaded.auto.common")
 *   }
 * }
 * ```
 */
interface ShadingSpec {
  fun relocate(fromPackage: String, toPackage: String)
}

enum class SoftwareType {
  ANDROID_LIBRARY,
  JVM_LIBRARY,
  PROCESSOR,
  TEST,
}
