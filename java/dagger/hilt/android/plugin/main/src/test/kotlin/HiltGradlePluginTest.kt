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

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Functional test of the plugin
 *
 * To run these tests first deploy artifacts to local maven via util/install-local-snapshot.sh.
 */
class HiltGradlePluginTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
  }

  // Verify plugin configuration fails when runtime dependency is missing but plugin is applied.
  @Test
  fun test_missingLibraryDep() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'"
    )

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput()).contains(
      "The Hilt Android Gradle plugin is applied but no " +
        "com.google.dagger:hilt-android dependency was found."
    )
  }

  // Verify plugin configuration fails when compiler dependency is missing but plugin is applied.
  @Test
  fun test_missingCompilerDep() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'"
    )

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput()).contains(
      "The Hilt Android Gradle plugin is applied but no " +
        "com.google.dagger:hilt-compiler dependency was found."
    )
  }

  @Test
  fun test_non_application_project() {
    gradleRunner.addHiltOption(
      "enableAggregatingTask = true"
    )
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp.java",
      srcContent =
          """
          package minimal;

          import android.app.Application;

          @dagger.hilt.android.HiltAndroidApp
          public class MyApp extends Application { }
          """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    gradleRunner.setIsAppProject(false)

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput()).contains(
      "Application class, minimal.MyApp, annotated with @HiltAndroidApp must be defined in a "
          + "Gradle android application module (i.e. contains a build.gradle file with "
          + "`plugins { id 'com.android.application' }`)."
    )
  }
}
