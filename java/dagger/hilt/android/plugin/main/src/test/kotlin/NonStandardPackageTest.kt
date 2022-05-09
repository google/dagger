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

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Test that things still work when a root class is in a package with non-standard capitalization.
 * https://github.com/google/dagger/issues/3329
 */
class NonStandardPackageTest {
  @get:Rule val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    gradleRunner.addHiltOption("enableAggregatingTask = true")
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "testImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "testAnnotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
  }

  @Test
  fun test_capitalizedPackage() {
    gradleRunner.addSrc(
      srcPath = "NonStandard/MyApp.java",
      srcContent =
        """
        package NonStandard;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application {}
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  // This one is useful since a lone capitalized package may also succeed just because it looks like
  // an enclosed class with no package.
  @Test
  fun test_capitalizedPackageWithLowercasePackages() {
    gradleRunner.addSrc(
      srcPath = "foo/NonStandard/bar/MyApp.java",
      srcContent =
        """
        package foo.NonStandard.bar;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application {}
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  @Test
  fun test_capitalizedPackageWithLowercasePackagesNested() {
    gradleRunner.addSrc(
      srcPath = "foo/NonStandard/bar/Foo.java",
      srcContent =
        """
        package foo.NonStandard.bar;

        import android.app.Application;

        public final class Foo {
          @dagger.hilt.android.HiltAndroidApp
          public class MyApp extends Application {}
        }
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".Foo")
    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  @Test
  fun test_lowerCaseClassName() {
    gradleRunner.addSrc(
      srcPath = "foo/myApp.java",
      srcContent =
        """
        package foo;

        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class myApp extends Application {}
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }

  @Test
  fun test_missingPackage() {
    gradleRunner.addSrc(
      // GradleTestRunner doesn't let you add files directly to the root so just put this in
      // some other directory. The source still doesn't have a package though.
      srcPath = "tmp/MyApp.java",
      srcContent =
        """
        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application { }
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
  }
}
