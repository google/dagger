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

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrossCompilationRootValidationTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    gradleRunner.addHiltOption(
      "enableAggregatingTask = true"
    )
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "testImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "testAnnotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
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
  }

  @Test
  fun multipleAppRootsFailure() {
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp2.java",
      srcContent =
      """
        package minimal;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp2 extends Application { }
        """.trimIndent()
    )

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput()).contains(
      "Cannot process multiple app roots in the same compilation unit: " +
        "minimal.MyApp, minimal.MyApp2"
    )
  }

  @Test
  fun testRootsAndAppRootsFailure() {
    gradleRunner.addTestSrc(
      srcPath = "minimal/MyTest.java",
      srcContent =
      """
        package minimal;

        @dagger.hilt.android.testing.HiltAndroidTest
        public class MyTest { }
        """.trimIndent()
    )
    gradleRunner.addTestSrc(
      srcPath = "minimal/BadApp.java",
      srcContent =
      """
        package minimal;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class BadApp extends Application { }
        """.trimIndent()
    )

    gradleRunner.runAdditionalTasks("testDebug")
    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput()).contains(
      "Cannot process test roots and app roots in the same compilation unit:"
    )
    assertThat(result.getOutput()).contains(
      "App root in this compilation unit: minimal.BadApp"
    )
    assertThat(result.getOutput()).contains(
      "Test roots in this compilation unit: minimal.MyTest"
    )
  }
}
