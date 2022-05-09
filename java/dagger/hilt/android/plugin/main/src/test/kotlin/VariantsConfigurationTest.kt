/*
 * Copyright (C) 2021 The Dagger Authors.
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

// Verifies the aggregated task is configured correctly in a multi-module flavored project.
class VariantsConfigurationTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @get:Rule
  val expect: Expect = Expect.create()

  @Before
  fun setup() {
    val projectRoot = testProjectDir.root
    File("src/test/data/flavored-project").copyRecursively(projectRoot)
  }

  @Test
  fun verifyFlavorConfiguration_demoDebug() {
    val result = runGradleTasks(":app:assembleMinApi21DemoDebug")
    expect.that(result.task(":app:assembleMinApi21DemoDebug")!!.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun verifyFlavorConfiguration_fullRelease() {
    val result = runGradleTasks(":app:assembleMinApi24FullRelease")
    expect.that(result.task(":app:assembleMinApi24FullRelease")!!.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  private fun runGradleTasks(vararg args: String): BuildResult {
    return GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(*args)
      .withPluginClasspath()
      .forwardOutput()
      .build()
  }
}
