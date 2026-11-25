/*
 * Copyright (C) 2026 The Dagger Authors.
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Verifies dependency aggregation for `androidTest` and `unitTest` variants across a transitive
 * dependency chain (`:app` -> `implementation(':libA')` -> `implementation(':libB')`), where
 * `:libB` defines a `@InstallIn(SingletonComponent.class)` module providing `String`.
 */
@RunWith(Parameterized::class)
class TestClasspathAggregationTest(
  private val backend: Backend,
  private val isLegacyClasspathAggregation: Boolean,
) {
  @get:Rule val testProjectDir = TemporaryFolder()

  private lateinit var gradleRunner: GradleTestRunner
  private lateinit var settingsFile: File

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    if (isLegacyClasspathAggregation) {
      // enableAggregatingTask defaults to true and takes precedence over the deprecated
      // enableExperimentalClasspathAggregation flag, so disable it here to exercise the legacy
      // configureCompileClasspath() path.
      gradleRunner.addHiltOption(
        "enableAggregatingTask = false",
        "enableExperimentalClasspathAggregation = true",
      )
    }
    when (backend) {
      Backend.JAVAC -> {}
      Backend.KAPT -> {
        gradleRunner.addPluginId("com.android.legacy-kapt")
        gradleRunner.addAdditionalClosure("kotlin { jvmToolchain(11) }")
      }
      Backend.KSP -> {
        gradleRunner.addPluginId("com.google.devtools.ksp")
        gradleRunner.addAdditionalClosure("kotlin { jvmToolchain(11) }")
      }
    }

    settingsFile =
      testProjectDir.newFile("settings.gradle").apply {
        writeText(
          """
          include ':libA'
          include ':libB'
          """
            .trimIndent()
        )
      }

    createJavaLibrary(dirName = "libA", dependenciesBlock = "implementation project(':libB')")
    createJavaLibrary(
      dirName = "libB",
      dependenciesBlock =
        """
        implementation 'com.google.dagger:hilt-core:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'
        """
          .trimIndent(),
    )
    writeFile(
      "libB/src/main/java/libb/LibBModule.java",
      """
      package libb;

      import dagger.Module;
      import dagger.Provides;
      import dagger.hilt.InstallIn;
      import dagger.hilt.components.SingletonComponent;

      @Module
      @InstallIn(SingletonComponent.class)
      public interface LibBModule {
        @Provides
        static String provideString() {
          return "from_libB";
        }
      }
      """
        .trimIndent(),
    )

    // Test processor configurations (testAnnotationProcessor, kaptTest, kspTest, etc.) do not
    // inherit from the main processor configuration, so each source set declares hilt-compiler.
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "${backend.mainProcessorConfig} 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "implementation project(':libA')",
      "testImplementation 'junit:junit:4.12'",
      "testImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "${backend.unitTestProcessorConfig} 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
      "androidTestImplementation 'junit:junit:4.12'",
      "androidTestImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "${backend.androidTestProcessorConfig} 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp.java",
      srcContent =
        """
        package minimal;

        import android.app.Application;
        import dagger.hilt.android.HiltAndroidApp;
        import javax.inject.Inject;

        @HiltAndroidApp
        public class MyApp extends Application {
          @Inject String string;
        }
        """
          .trimIndent(),
    )
    gradleRunner.setAppClassName(".MyApp")
  }

  // Verifies that androidTest aggregates LibBModule (which provides String) from transitive
  // ':libB'. Because ':libB' is hidden behind ':libA's implementation boundary (absent from
  // debugAndroidTestCompileClasspath) and AGP omits app dependencies from
  // debugAndroidTestRuntimeClasspath, ':assembleDebugAndroidTest' only succeeds if Hilt includes
  // testedVariant's configurations for AndroidTest variants.
  @Test
  fun test_androidTestIncludesTestedDependencies() {
    writeFile(
      "src/androidTest/java/minimal/MyAndroidTest.java",
      """
      package minimal;

      import dagger.hilt.android.testing.HiltAndroidTest;
      import javax.inject.Inject;
      import org.junit.Test;

      @HiltAndroidTest
      public class MyAndroidTest {
        @Inject String string;

        @Test
        public void test() {}
      }
      """
        .trimIndent(),
    )

    gradleRunner.runAdditionalTasks("assembleDebugAndroidTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebugAndroidTest").outcome)
  }

  // Verifies that unitTest aggregates LibBModule (which provides String) from transitive ':libB' by
  // default through the unit test runtime classpath, without needing a direct testImplementation
  // dependency on ':libB'.
  @Test
  fun test_unitTestIncludesTestedDependencies() {
    gradleRunner.addTestSrc(
      srcPath = "minimal/MyUnitTest.java",
      srcContent =
        """
        package minimal;

        import dagger.hilt.android.testing.HiltAndroidTest;
        import javax.inject.Inject;
        import org.junit.Test;

        @HiltAndroidTest
        public class MyUnitTest {
          @Inject String string;

          @Test
          public void test() {}
        }
        """
          .trimIndent(),
    )

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":testDebugUnitTest").outcome)
  }

  // Verifies that excluding ':libB' from the unit test classpaths removes LibBModule from
  // MyUnitTest's component (causing a [Dagger/MissingBinding] error for String), while
  // ':assembleDebug' still succeeds using LibBModule from the app classpath.
  @Test
  fun test_unitTestExcludeDependency() {
    gradleRunner.addAdditionalClosure(
      """
      configurations.matching {
        it.name == 'debugUnitTestCompileClasspath' || it.name == 'debugUnitTestRuntimeClasspath'
      }.configureEach {
        exclude(module: 'libB')
      }
      """
        .trimIndent()
    )
    gradleRunner.addTestSrc(
      srcPath = "minimal/MyUnitTest.java",
      srcContent =
        """
        package minimal;

        import dagger.hilt.android.testing.HiltAndroidTest;
        import javax.inject.Inject;
        import org.junit.Test;

        @HiltAndroidTest
        public class MyUnitTest {
          @Inject String string;

          @Test
          public void test() {}
        }
        """
          .trimIndent(),
    )

    val assembleResult = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, assembleResult.getTask(":assembleDebug").outcome)

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val testResult = gradleRunner.buildAndFail()
    assertThat(testResult.getOutput()).contains("[Dagger/MissingBinding]")
    assertThat(testResult.getOutput())
      .contains("String cannot be provided without an @Inject constructor")
  }

  // Verifies that substituting ':libB' with ':fakeLibB' (which provides both String and Integer) on
  // the unit test classpaths replaces LibBModule with FakeLibBModule in MyUnitTest's component:
  // injecting String succeeds without [Dagger/DuplicateBindings], injecting Integer succeeds
  // without [Dagger/MissingBinding], and ':assembleDebug' still succeeds using ':libB'.
  @Test
  fun test_unitTestSubstituteDependency() {
    settingsFile.appendText("\ninclude ':fakeLibB'\n")
    createJavaLibrary(
      dirName = "fakeLibB",
      dependenciesBlock =
        """
        implementation 'com.google.dagger:hilt-core:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'
        """
          .trimIndent(),
    )
    writeFile(
      "fakeLibB/src/main/java/fakelibb/FakeLibBModule.java",
      """
      package fakelibb;

      import dagger.Module;
      import dagger.Provides;
      import dagger.hilt.InstallIn;
      import dagger.hilt.components.SingletonComponent;

      @Module
      @InstallIn(SingletonComponent.class)
      public interface FakeLibBModule {
        @Provides
        static String provideString() {
          return "from_fakeLibB";
        }

        @Provides
        static Integer provideInteger() {
          return 1;
        }
      }
      """
        .trimIndent(),
    )
    gradleRunner.addAdditionalClosure(
      """
      configurations.matching {
        it.name == 'debugUnitTestCompileClasspath' || it.name == 'debugUnitTestRuntimeClasspath'
      }.configureEach {
        resolutionStrategy.dependencySubstitution {
          substitute(project(':libB')).using(project(':fakeLibB'))
        }
      }
      """
        .trimIndent()
    )
    gradleRunner.addTestSrc(
      srcPath = "minimal/MyUnitTest.java",
      srcContent =
        """
        package minimal;

        import dagger.hilt.android.testing.HiltAndroidTest;
        import javax.inject.Inject;
        import org.junit.Test;

        @HiltAndroidTest
        public class MyUnitTest {
          @Inject String string;
          @Inject Integer integer;

          @Test
          public void test() {}
        }
        """
          .trimIndent(),
    )

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":testDebugUnitTest").outcome)
  }

  private fun createJavaLibrary(dirName: String, dependenciesBlock: String) {
    writeFile(
      "$dirName/build.gradle",
      """
      plugins {
        id 'java-library'
      }

      java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
      }

      dependencies {
        $dependenciesBlock
      }
      """
        .trimIndent(),
    )
  }

  private fun writeFile(relativePath: String, content: String) {
    File(testProjectDir.root, relativePath).apply {
      parentFile.mkdirs()
      writeText(content)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "backend = {0}, isLegacyClasspathAggregation = {1}")
    fun params() =
      Backend.entries.flatMap { backend ->
        listOf(false, true).map { isLegacy -> arrayOf<Any>(backend, isLegacy) }
      }

    enum class Backend(
      val mainProcessorConfig: String,
      val unitTestProcessorConfig: String,
      val androidTestProcessorConfig: String,
    ) {
      JAVAC("annotationProcessor", "testAnnotationProcessor", "androidTestAnnotationProcessor"),
      KAPT("kapt", "kaptTest", "kaptAndroidTest"),
      KSP("ksp", "kspTest", "kspAndroidTest"),
    }
  }
}
