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

import com.google.common.truth.Expect
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

const val TASK = ":app:hiltJavaCompileDebug"

@RunWith(Parameterized::class)
class AGPCompatibilityTest(
  private val agpVersion: String
) {
  @get:Rule val testProjectDir = TemporaryFolder()

  @get:Rule val expect: Expect = Expect.create()

  @Before
  fun setup() {
    val projectRoot = testProjectDir.root
    File("src/test/data/simple-project-for-agp-test").copyRecursively(projectRoot)
    testProjectDir.newFile("build.gradle").apply {
      writeText(
        """
        plugins {
          id 'com.android.application' version '$agpVersion' apply false
          id 'com.android.library' version '$agpVersion' apply false
          id 'com.google.dagger.hilt.android' version 'LOCAL-SNAPSHOT' apply false
        }

        // `dependencyResolutionManagement` in settings.gradle is not supported by Gradle 6.7.1
        allprojects {
          repositories {
            mavenLocal()
            google()
            mavenCentral()
          }
        }
      """.trimIndent()
      )
    }
  }

  @Test
  fun test() {
    val result = runGradleTasks(TASK)
    expect.that(result.task(TASK)!!.outcome)
        .isEqualTo(TaskOutcome.SUCCESS)
  }

  private fun runGradleTasks(vararg args: String): BuildResult {
    // Here we use Hilt Gradle Plugin in mavenLocal() so no withPluginClasspath()
    val gradleRunner =
      GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments(*args)
        .forwardOutput()
    return gradleRunner.build()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "agpVersion = {0}")
    fun parameters() =
      listOf(
        arrayOf("7.2.0"),
        arrayOf("7.1.0"),
        arrayOf("7.0.0"),
      )
  }
}
