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
      gradleRunner.addHiltOption(
        "enableAggregatingTask = false"
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

  // Verifies that androidTest variants aggregate transitive dependencies from testedVariant even
  // though AGP strips tested dependencies from the androidTest runtime classpath.
  @Test
  fun test_androidTestIncludesTestedDependencies() {
    File("src/test/data/android-libraryA")
      .copyRecursively(File(testProjectDir.root, "libraryA"))
    File("src/test/data/java-libraryB")
      .copyRecursively(File(testProjectDir.root, "libraryB"))
    File("src/test/data/android-libraryC")
      .copyRecursively(File(testProjectDir.root, "libraryC"))

    testProjectDir.newFile("settings.gradle").writeText(
      """
      include ':libraryA'
      include ':libraryB'
      include ':libraryC'
      """.trimIndent()
    )

    gradleRunner.addDependencies(
      "androidTestImplementation 'junit:junit:4.12'",
      "androidTestImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "androidTestAnnotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    File(testProjectDir.root, "src/androidTest/java/minimal/MyAndroidTest.java").apply {
      parentFile.mkdirs()
      writeText(
        """
        package minimal;

        import dagger.hilt.android.testing.HiltAndroidTest;
        import javax.inject.Inject;
        import liba.LibraryA;
        import org.junit.Test;

        @HiltAndroidTest
        public class MyAndroidTest {
          @Inject LibraryA libraryA;

          @Test
          public void test() {}
        }
        """.trimIndent()
      )
    }

    gradleRunner.runAdditionalTasks("assembleDebugAndroidTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebugAndroidTest").outcome)
  }

  // Verifies that without excluding or substituting unusedLibrary on unit test classpaths,
  // UnusedActivity is aggregated into the unit test component and fails missing-binding validation.
  @Test
  fun test_unitTestIncludesDependencyByDefault() {
    setupUnitTestWithUnusedLibrary()

    val assembleResult = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, assembleResult.getTask(":assembleDebug").outcome)

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val testResult = gradleRunner.buildAndFail()
    assertThat(testResult.getOutput()).contains("[Dagger/MissingBinding]")
    assertThat(testResult.getOutput()).contains("unused.UnusedActivity")
  }

  // Verifies that unit test variants respect module exclusions on unit test classpaths.
  @Test
  fun test_unitTestExcludeDependency() {
    setupUnitTestWithUnusedLibrary()
    gradleRunner.addAdditionalClosure(
      """
      configurations.matching {
        it.name == 'debugUnitTestCompileClasspath' || it.name == 'debugUnitTestRuntimeClasspath'
      }.configureEach {
        exclude(module: 'unusedLibrary')
      }
      """.trimIndent()
    )

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":testDebugUnitTest").outcome)
  }

  // Verifies that unit test variants respect dependency substitutions on unit test classpaths.
  @Test
  fun test_unitTestSubstituteDependency() {
    setupUnitTestWithUnusedLibrary(includeStubLibrary = true)
    gradleRunner.addAdditionalClosure(
      """
      configurations.matching {
        it.name == 'debugUnitTestCompileClasspath' || it.name == 'debugUnitTestRuntimeClasspath'
      }.configureEach {
        resolutionStrategy.dependencySubstitution {
          substitute(project(':unusedLibrary')).using(project(':stubUnusedLibrary'))
        }
      }
      """.trimIndent()
    )

    gradleRunner.runAdditionalTasks("testDebugUnitTest")
    val result = gradleRunner.build()
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":assembleDebug").outcome)
    assertEquals(TaskOutcome.SUCCESS, result.getTask(":testDebugUnitTest").outcome)
  }

  private fun setupUnitTestWithUnusedLibrary(includeStubLibrary: Boolean = false) {
    File("src/test/data/android-libraryA")
      .copyRecursively(File(testProjectDir.root, "libraryA"))
    File("src/test/data/java-libraryB")
      .copyRecursively(File(testProjectDir.root, "libraryB"))
    File("src/test/data/android-libraryC")
      .copyRecursively(File(testProjectDir.root, "libraryC"))

    createAndroidLibrary(
      dirName = "unusedLibrary",
      namespace = "unused",
      applyHilt = true,
      dependenciesBlock =
        """
        implementation 'androidx.appcompat:appcompat:1.1.0'
        implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'
        """.trimIndent(),
    )
    File(testProjectDir.root, "unusedLibrary/src/main/java/unused/UnusedActivity.java").apply {
      parentFile.mkdirs()
      writeText(
        """
        package unused;

        import androidx.appcompat.app.AppCompatActivity;
        import dagger.hilt.android.AndroidEntryPoint;
        import javax.inject.Inject;
        import javax.inject.Named;

        @AndroidEntryPoint
        public class UnusedActivity extends AppCompatActivity {
          @Inject @Named("unused") String unusedString;
        }
        """.trimIndent()
      )
    }

    if (includeStubLibrary) {
      createAndroidLibrary(
        dirName = "stubUnusedLibrary",
        namespace = "unused.stub",
        applyHilt = false,
      )
      File(testProjectDir.root, "stubUnusedLibrary/src/main/java/unused/stub/Stub.java").apply {
        parentFile.mkdirs()
        writeText(
          """
          package unused.stub;

          public final class Stub {}
          """.trimIndent()
        )
      }
    }

    val includedProjects = buildList {
      add("include ':libraryA'")
      add("include ':libraryB'")
      add("include ':libraryC'")
      add("include ':unusedLibrary'")
      if (includeStubLibrary) {
        add("include ':stubUnusedLibrary'")
      }
    }
    testProjectDir.newFile("settings.gradle").writeText(includedProjects.joinToString("\n"))

    gradleRunner.addDependencies(
      "implementation project(':unusedLibrary')",
      "testImplementation 'junit:junit:4.12'",
      "testImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'",
      "testAnnotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.addSrc(
      srcPath = "minimal/UnusedModule.java",
      srcContent =
        """
        package minimal;

        import dagger.Module;
        import dagger.Provides;
        import dagger.hilt.InstallIn;
        import dagger.hilt.components.SingletonComponent;
        import javax.inject.Named;

        @Module
        @InstallIn(SingletonComponent.class)
        public final class UnusedModule {
          @Provides
          @Named("unused")
          static String provideUnusedString() {
            return "unused";
          }
        }
        """.trimIndent()
    )
    gradleRunner.addTestSrc(
      srcPath = "minimal/MyUnitTest.java",
      srcContent =
        """
        package minimal;

        import dagger.hilt.android.testing.HiltAndroidTest;
        import dagger.hilt.android.testing.UninstallModules;
        import javax.inject.Inject;
        import liba.LibraryA;
        import org.junit.Test;

        @HiltAndroidTest
        @UninstallModules(UnusedModule.class)
        public class MyUnitTest {
          @Inject LibraryA libraryA;

          @Test
          public void test() {}
        }
        """.trimIndent()
    )
  }

  private fun createAndroidLibrary(
    dirName: String,
    namespace: String,
    applyHilt: Boolean,
    dependenciesBlock: String = "",
  ) {
    val libDir = File(testProjectDir.root, dirName).apply { mkdirs() }
    File(libDir, "src/main").mkdirs()
    File(libDir, "src/main/AndroidManifest.xml").writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      </manifest>
      """.trimIndent()
    )
    val hiltPluginLine = if (applyHilt) "id 'com.google.dagger.hilt.android'" else ""
    File(libDir, "build.gradle").writeText(
      """
      plugins {
        id 'com.android.library'
        $hiltPluginLine
      }

      android {
        compileSdkVersion 36
        buildToolsVersion "36.0.0"

        defaultConfig {
          minSdkVersion 21
          targetSdkVersion 36
        }
        compileOptions {
          sourceCompatibility JavaVersion.VERSION_11
          targetCompatibility JavaVersion.VERSION_11
        }
        namespace = "$namespace"
      }

      dependencies {
        implementation(platform('org.jetbrains.kotlin:kotlin-bom:2.2.0'))
        $dependenciesBlock
      }
      """.trimIndent()
    )
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
