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

  @get:Rule val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
  }

  // Verify plugin configuration fails when runtime dependency is missing but plugin is applied.
  @Test
  fun test_missingLibraryDep() {
    gradleRunner.addDependencies("implementation 'androidx.appcompat:appcompat:1.1.0'")

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput())
      .contains(
        "The Hilt Android Gradle plugin is applied but no " +
          "com.google.dagger:hilt-android dependency was found."
      )
  }

  // Verify plugin configuration fails when compiler dependency is missing but plugin is applied.
  @Test
  fun test_missingCompilerDep() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
    )

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput())
      .contains(
        "The Hilt Android Gradle plugin is applied but no " +
          "com.google.dagger:hilt-compiler dependency was found."
      )
  }

  @Test
  fun test_non_application_project() {
    gradleRunner.addHiltOption("enableAggregatingTask = true")
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
        """
          .trimIndent(),
    )
    gradleRunner.setAppClassName(".MyApp")
    gradleRunner.setIsAppProject(false)

    val result = gradleRunner.buildAndFail()
    assertThat(result.getOutput())
      .contains(
        "Application class, minimal.MyApp, annotated with @HiltAndroidApp must be defined in a " +
          "Gradle android application module (i.e. contains a build.gradle file with " +
          "`plugins { id 'com.android.application' }`)."
      )
  }

  // Verify that compiler options configured by the Hilt plugin (using fastInit
  // as a representative toggleable flag) are correctly propagated to the
  // Hilt aggregation compilation task (hiltJavaCompile).
  @Test
  fun test_compilerOptionPropagation_fastInitDisabled() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.runAdditionalTasks("-Pdagger.hilt.fastInit=false")
    gradleRunner.runAdditionalTasks("verifyHiltArgs")

    gradleRunner.addAdditionalClosure(
      """
      tasks.register("verifyHiltArgs") {
          doLast {
              def hiltCompileTask = tasks.getByName("hiltJavaCompileDebug")
              def args = []
              hiltCompileTask.options.compilerArgumentProviders.each { provider ->
                  args.addAll(provider.asArguments())
              }
              if (args.contains("-Adagger.fastInit=enabled")) {
                  throw new GradleException("Expected fastInit to be disabled (absent), but args were: " + args)
              }
          }
      }
      """
        .trimIndent()
    )

    gradleRunner.build()
  }

  @Test
  fun test_compilerOptionPropagation_fastInitDefault() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.runAdditionalTasks("verifyHiltArgs")

    gradleRunner.addAdditionalClosure(
      """
      tasks.register("verifyHiltArgs") {
          doLast {
              def hiltCompileTask = tasks.getByName("hiltJavaCompileDebug")
              def args = []
              hiltCompileTask.options.compilerArgumentProviders.each { provider ->
                  args.addAll(provider.asArguments())
              }
              if (!args.contains("-Adagger.fastInit=enabled")) {
                  throw new GradleException("Expected fastInit to be enabled by default, but args were: " + args)
              }
          }
      }
      """
        .trimIndent()
    )

    gradleRunner.build()
  }

  @Test
  fun test_kmp_android_library() {
    val projectFolder = testProjectDir.root
    File(projectFolder, "build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.multiplatform' version '2.1.0'
        id 'com.android.kotlin.multiplatform.library' version '9.0.0'
        id 'com.google.devtools.ksp'
        id 'com.google.dagger.hilt.android'
      }

      kotlin {
        androidLibrary {
          namespace 'minimal.kmp'
          compileSdk 35
          minSdk 21
        }
      }

      allprojects {
        repositories {
          mavenLocal()
          google()
          mavenCentral()
        }
      }

      dependencies {
        add("androidMainImplementation", "com.google.dagger:hilt-android:LOCAL-SNAPSHOT")
        add("ksp", "com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT")
        add("androidMainImplementation", "javax.inject:javax.inject:1")
      }
      """.trimIndent()
    )
    val result = org.gradle.testkit.runner.GradleRunner.create()
      .withProjectDir(projectFolder)
      .withArguments("assemble")
      .withPluginClasspath()
      .forwardOutput()
      .build()

    assertThat(result.output).contains("BUILD SUCCESSFUL")
  }
}
