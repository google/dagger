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

import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// Test that verifies compile classpath aggregation done by the plugin.
@RunWith(Parameterized::class)
class CompileClasspathTest(private val pluginFlagName: String) {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    if (pluginFlagName == "enableExperimentalClasspathAggregation") {
      gradleRunner.addAndroidOption(
        "lintOptions.checkReleaseBuilds = false"
      )
    }
    gradleRunner.addHiltOption(
      "$pluginFlagName = true"
    )
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "implementation project(':libraryA')",
    )
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp.java",
      srcContent =
        """
        package minimal;

        import android.app.Application;
        import liba.LibraryA;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application {
          @javax.inject.Inject
          LibraryA libraryA;
        }
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
  }

  // Verifies that library B and library C injected classes are available in the root classpath.
  @Test
  fun test_injectClasses() {
    File("src/test/data/android-libraryA")
      .copyRecursively(File(testProjectDir.root, "libraryA"))
    File("src/test/data/java-libraryB")
      .copyRecursively(File(testProjectDir.root, "libraryB"))
    File("src/test/data/android-libraryC")
      .copyRecursively(File(testProjectDir.root, "libraryC"))

    testProjectDir.newFile("settings.gradle").apply {
      writeText(
        """
        include ':libraryA'
        include ':libraryB'
        include ':libraryC'
        """.trimIndent()
      )
    }

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  // Verifies that library B Hilt module is available in the root classpath.
  @Test
  fun test_injectClassesFromModules() {
    File("src/test/data/java-libraryA")
      .copyRecursively(File(testProjectDir.root, "libraryA"))
    File("src/test/data/java-libraryB")
      .copyRecursively(File(testProjectDir.root, "libraryB"))

    testProjectDir.newFile("settings.gradle").apply {
      writeText(
        """
        include ':libraryA'
        include ':libraryB'
        """.trimIndent()
      )
    }

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters() = listOf(
      "enableExperimentalClasspathAggregation",
      "enableAggregatingTask"
    )
  }
}
