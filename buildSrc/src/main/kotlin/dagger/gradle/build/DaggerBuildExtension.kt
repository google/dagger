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

/** Extension for [DaggerConventionPlugin] that's responsible for holding configuration options. */
abstract class DaggerBuildExtension {
  /** The type of project */
  var type: ProjectType = ProjectType.LIBRARY

  /** Whether the project artifacts are published or not */
  var isPublished = false
}

enum class ProjectType {
  LIBRARY,
  PROCESSOR,
  TEST,
}

/**
 * Extension for [DaggerConventionPlugin] created when the shadow plugin is applied.
 *
 * Relocation rules can be specified vis this extension:
 * ```
 * shading {
 *   relocate("com.google.auto.common", "dagger.spi.internal.shaded.auto.common")
 * }
 * ```
 */
abstract class ShadeExtension {
  internal val rules = mutableMapOf<String, String>()

  fun relocate(fromPackage: String, toPackage: String) {
    check(!rules.containsKey(fromPackage)) { "Duplicate shading rule declared for $fromPackage" }
    rules[fromPackage] = toPackage
  }
}
