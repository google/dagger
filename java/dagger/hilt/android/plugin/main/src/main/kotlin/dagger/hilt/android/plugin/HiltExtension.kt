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

/** Configuration options for the Hilt Gradle Plugin */
interface HiltExtension {

  /**
   * If set to `true`, Hilt will adjust the compile classpath such that it includes transitive
   * dependencies, ignoring `api` or `implementation` boundaries during compilation. You should
   * enable this option if your project has multiple level of transitive dependencies that contain
   * injected classes or entry points. The default value is `false`.
   *
   * This option should be enable as a last resort to avoid classpath issues if
   * [enableAggregatingTask] (set to `true` by default) causes issues.
   *
   * See https://github.com/google/dagger/issues/1991 for more context.
   */
  var enableExperimentalClasspathAggregation: Boolean

  /**
   * If set to `true`, Hilt will register a transform task that will rewrite `@AndroidEntryPoint`
   * annotated classes before the host-side JVM tests run. You should enable this option if you are
   * running Robolectric UI tests as part of your JUnit tests.
   *
   * This flag is not necessary when com.android.tools.build:gradle:4.2.0+ is used.
   */
  @Deprecated("Since Hilt Android Gradle plugin requires the usage of the Android " +
      "Gradle plugin (AGP) version 7.0 or higher this option is no longer necessary and has no " +
      "effect in the configuration.")
  var enableTransformForLocalTests: Boolean

  /**
   * If set to `true`, Hilt will perform module and entry points aggregation in a task instead of an
   * aggregating annotation processor. Enabling this flag improves incremental build times. The
   * default value is `true`.
   *
   * When this flag is enabled, 'enableExperimentalClasspathAggregation' has no effect since
   * classpath aggregation is already performed by the aggregation task.
   */
  var enableAggregatingTask: Boolean

  /**
   * If set to `true`, Hilt will disable cross compilation root validation. The default value is
   * `false`.
   *
   * See [documentation](https://dagger.dev/hilt/flags#disable-cross-compilation-root-validation)
   * for more information.
   */
  var disableCrossCompilationRootValidation: Boolean

  /**
   * If set to `true`, the Hilt Gradle Plugin will not validated that both the Hilt runtime
   * dependency and compiler dependency are applied to the project. The default value is `false`.
   */
  var disableDependencyCheck: Boolean
}

internal open class HiltExtensionImpl : HiltExtension {
  override var enableExperimentalClasspathAggregation: Boolean = false
  @Deprecated("Since Hilt Android Gradle plugin requires the usage of the Android " +
      "Gradle plugin (AGP) version 7.0 or higher this option is no longer necessary and has no " +
      "effect in the configuration.")
  override var enableTransformForLocalTests: Boolean = false
  override var enableAggregatingTask: Boolean = true
  override var disableCrossCompilationRootValidation: Boolean = false
  override var disableDependencyCheck: Boolean = false
}
