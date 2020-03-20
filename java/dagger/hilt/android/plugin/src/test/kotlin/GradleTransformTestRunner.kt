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
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

/**
 * Testing utility class that sets up a simple Android project that applies the transform plugin.
 */
class GradleTransformTestRunner(val tempFolder: TemporaryFolder) {
  private val dependencies = mutableListOf<String>()
  private val activities = mutableListOf<String>()

  private var buildFile: File? = null
  private var manifestFile: File? = null

  init {
    tempFolder.newFolder("src", "main", "java", "minimal")
    tempFolder.newFolder("src", "main", "res")
  }

  // Adds project dependencies, e.g. "implementation <group>:<id>:<version>"
  fun addDependencies(vararg deps: String) {
    dependencies.addAll(deps)
  }

  // Adds an <activity> tag in the project's Android Manifest, e.g. "<activity name=".Foo"/>
  fun addActivities(vararg deps: String) {
    activities.addAll(deps)
  }

  // Adds a source package to the project. The package path is relative to 'src/main/java'.
  fun addSrcPackage(packagePath: String) {
    File(tempFolder.root, "src/main/java/$packagePath").mkdirs()
  }

  // Adds a source file to the project. The source path is relative to 'src/main/java'.
  fun addSrc(srcPath: String, srcContent: String): File {
    File(tempFolder.root, "src/main/java/${srcPath.substringBeforeLast(File.separator)}").mkdirs()
    return tempFolder.newFile("/src/main/java/$srcPath").apply { writeText(srcContent) }
  }

  // Adds a resource file to the project. The source path is relative to 'src/main/res'.
  fun addRes(resPath: String, resContent: String): File {
    File(tempFolder.root, "src/main/res/${resPath.substringBeforeLast(File.separator)}").mkdirs()
    return tempFolder.newFile("/src/main/res/$resPath").apply { writeText(resContent) }
  }

  // Executes a Gradle builds and expects it to succeed.
  fun build(): Result {
    setupFiles()
    return Result(tempFolder.root, createRunner().build())
  }

  // Executes a Gradle build and expects it to fail.
  fun buildAndFail(): Result {
    setupFiles()
    return Result(tempFolder.root, createRunner().buildAndFail())
  }

  private fun setupFiles() {
    writeBuildFile()
    writeAndroidManifest()
  }

  private fun writeBuildFile() {
    buildFile?.delete()
    buildFile = tempFolder.newFile("build.gradle").apply {
      writeText(
        """
        buildscript {
          repositories {
            google()
            jcenter()
          }
          dependencies {
            classpath 'com.android.tools.build:gradle:3.5.3'
          }
        }

        plugins {
          id 'com.android.application'
          id 'dagger.hilt.android.plugin'
        }

        android {
          compileSdkVersion 29
          buildToolsVersion "29.0.2"

          defaultConfig {
            applicationId "plugin.test"
            minSdkVersion 21
            targetSdkVersion 29
          }

          compileOptions {
              sourceCompatibility 1.8
              targetCompatibility 1.8
          }
        }

        repositories {
          mavenLocal()
          google()
          jcenter()
        }

        dependencies {
          ${dependencies.joinToString(separator = "\n")}
        }
        """.trimIndent()
      )
    }
  }

  private fun writeAndroidManifest() {
    manifestFile?.delete()
    manifestFile = tempFolder.newFile("/src/main/AndroidManifest.xml").apply {
      writeText(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="minimal">
            <application
                android:theme="@style/Theme.AppCompat.Light.DarkActionBar">
                ${activities.joinToString(separator = "\n")}
            </application>
        </manifest>
        """.trimIndent()
      )
    }
  }

  private fun createRunner() = GradleRunner.create()
    .withProjectDir(tempFolder.root)
    .withArguments("assembleDebug", "--stacktrace")
    .withPluginClasspath()
//    .withDebug(true) // Add this line to enable attaching a debugger to the gradle test invocation
    .forwardOutput()

  // Data class representing a Gradle Test run result.
  data class Result(
    private val projectRoot: File,
    private val buildResult: BuildResult
  ) {
    // Finds a task by name.
    fun getTask(name: String) = buildResult.task(name) ?: error("Task '$name' not found.")

    // Gets the full build output.
    fun getOutput() = buildResult.output

    // Finds a transformed file. The srcFilePath is relative to the app's package.
    fun getTransformedFile(srcFilePath: String) = File(
      projectRoot,
      "build/intermediates/transforms/AndroidEntryPointTransform/debug"
    ).listFiles()?.first { it.isDirectory }?.let { transformedDir ->
      File(transformedDir, "minimal/$srcFilePath").also {
        if (!it.exists()) {
          error("Unable to find transformed class ${it.path}")
        }
      }
    } ?: error("Unable to find transformed output directory.")
  }
}
