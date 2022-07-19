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

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SPIPluginTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    File("src/test/data/spi-plugin")
      .copyRecursively(File(testProjectDir.root, "spi-plugin"))
    testProjectDir.newFile("settings.gradle").apply {
      writeText(
        """
        include ':spi-plugin'
        """.trimIndent()
      )
    }
    gradleRunner.addHiltOption("enableAggregatingTask = true")
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "annotationProcessor project(':spi-plugin')",
    )
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp.java",
      srcContent =
      """
        package minimal;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application {
        }
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
  }

  @Test
  fun verifyPluginWithHiltAggregation() {
    // Run the build expecting it to fail because the TestPlugin will report an error if it finds
    // the root component, the build not failing is an indication that the plugin is not being
    // discovered in Hilt's aggregation JavaCompileTask.
    val result = gradleRunner.buildAndFail()
    assertThat(result.getTask(":hiltJavaCompileDebug").outcome).isEqualTo(TaskOutcome.FAILED)
    assertThat(result.getOutput()).contains(
      "[spi.TestPlugin] Found component: minimal.MyApp_HiltComponents.SingletonC"
    )
  }
}