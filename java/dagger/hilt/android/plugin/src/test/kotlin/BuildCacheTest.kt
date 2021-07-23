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

import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*
import kotlin.random.Random

// Test that verifies the hilt class transform does not break the Gradle's remote build cache.
class BuildCacheTest {
  @get:Rule
  val gradleHomeFolder = TemporaryFolder()

  @get:Rule
  val firstProjectDir = TemporaryFolder()

  lateinit var firstGradleRunner: GradleTestRunner

  @get:Rule
  val secondProjectDir = TemporaryFolder()

  lateinit var secondGradleRunner: GradleTestRunner

  private val testId = UUID.randomUUID().toString()

  @Before
  fun setup() {
    firstGradleRunner = createGradleRunner(firstProjectDir)
    secondGradleRunner = createGradleRunner(secondProjectDir)
  }

  private fun createGradleRunner(folder: TemporaryFolder): GradleTestRunner {
    val gradleRunner = GradleTestRunner(folder)
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'",
    )
    gradleRunner.runAdditionalTasks("--build-cache")
    gradleRunner.addSrc(
      srcPath = "minimal/MyApp.java",
      srcContent =
      """
        package minimal;
        
        import android.app.Application;

        @dagger.hilt.android.HiltAndroidApp
        public class MyApp extends Application {
          // random id inserted into the code to ensure that the first is never a cache hit and the second 
          // run always is
          public static String RANDOM_ID = "$testId";
        }
        """.trimIndent()
    )
    gradleRunner.setAppClassName(".MyApp")
    return gradleRunner
  }

  // Verifies that library B and library C injected classes are available in the root classpath.
  @Test
  fun test_buildCacheHitOnRelocatedProject() {
    val firstResult = firstGradleRunner.build()
    assertEquals(firstResult.getTask(":transformDebugClassesWithAsm").outcome, SUCCESS)

    val secondResult = secondGradleRunner.build()
    val cacheableTasks = listOf(
      ":checkDebugAarMetadata",
      ":checkDebugDuplicateClasses",
      ":compileDebugJavaWithJavac",
      ":compressDebugAssets",
      ":extractDeepLinksDebug",
      ":generateDebugBuildConfig",
      ":generateDebugResValues",
      ":javaPreCompileDebug",
      ":mergeDebugAssets",
      ":mergeDebugJavaResource",
      ":mergeDebugJniLibFolders",
      ":mergeDebugNativeLibs",
      ":mergeDebugShaders",
      ":mergeExtDexDebug",
      ":mergeLibDexDebug",
      ":mergeProjectDexDebug",
      ":processDebugManifestForPackage",
      ":transformDebugClassesWithAsm",
      ":validateSigningDebug",
      ":writeDebugAppMetadata",
      ":writeDebugSigningConfigVersions",
    )

    val tasksFromCache = secondResult.tasks.filter { it.outcome == FROM_CACHE }.map { it.path }.sorted()
    assertEquals(cacheableTasks, tasksFromCache)
  }
}
