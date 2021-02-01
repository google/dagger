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

import com.google.common.truth.Expect
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests to verify Gradle annotation processor incremental compilation.
 *
 * To run these tests first deploy artifacts to local maven via util/install-local-snapshot.sh.
 */
class IncrementalProcessorTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  @get:Rule
  val expect: Expect = Expect.create()

  // Original source files
  private lateinit var srcApp: File
  private lateinit var srcActivity1: File
  private lateinit var srcActivity2: File
  private lateinit var srcModule1: File
  private lateinit var srcModule2: File

  // Generated source files
  private lateinit var genHiltApp: File
  private lateinit var genHiltActivity1: File
  private lateinit var genHiltActivity2: File
  private lateinit var genAppInjector: File
  private lateinit var genActivityInjector1: File
  private lateinit var genActivityInjector2: File
  private lateinit var genAppInjectorDeps: File
  private lateinit var genActivityInjectorDeps1: File
  private lateinit var genActivityInjectorDeps2: File
  private lateinit var genModuleDeps1: File
  private lateinit var genModuleDeps2: File
  private lateinit var genHiltComponents: File
  private lateinit var genDaggerHiltApplicationComponent: File

  // Compiled classes
  private lateinit var classSrcApp: File
  private lateinit var classSrcActivity1: File
  private lateinit var classSrcActivity2: File
  private lateinit var classSrcModule1: File
  private lateinit var classSrcModule2: File
  private lateinit var classGenHiltApp: File
  private lateinit var classGenHiltActivity1: File
  private lateinit var classGenHiltActivity2: File
  private lateinit var classGenAppInjector: File
  private lateinit var classGenActivityInjector1: File
  private lateinit var classGenActivityInjector2: File
  private lateinit var classGenAppInjectorDeps: File
  private lateinit var classGenActivityInjectorDeps1: File
  private lateinit var classGenActivityInjectorDeps2: File
  private lateinit var classGenModuleDeps1: File
  private lateinit var classGenModuleDeps2: File
  private lateinit var classGenHiltComponents: File
  private lateinit var classGenDaggerHiltApplicationComponent: File

  // Timestamps of files
  private lateinit var fileToTimestampMap: Map<File, Long>

  // Sets of files that have changed/not changed/deleted
  private lateinit var changedFiles: Set<File>
  private lateinit var unchangedFiles: Set<File>
  private lateinit var deletedFiles: Set<File>

  @Before
  fun setup() {
    val projectRoot = testProjectDir.root
    // copy test project
    File("src/test/data/simple-project").copyRecursively(projectRoot)

    // set up build file
    File(projectRoot, "build.gradle").writeText(
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
      }

      android {
        compileSdkVersion 30
        buildToolsVersion "30.0.2"

        defaultConfig {
          applicationId "hilt.simple"
          minSdkVersion 21
          targetSdkVersion 30
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
        implementation 'androidx.appcompat:appcompat:1.1.0'
        implementation 'com.google.dagger:dagger:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:dagger-compiler:LOCAL-SNAPSHOT'
        implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'
      }
      """.trimIndent()
    )

    // Compute file paths
    srcApp = File(projectRoot, "$SRC_DIR/simple/SimpleApp.java")
    srcActivity1 = File(projectRoot, "$SRC_DIR/simple/Activity1.java")
    srcActivity2 = File(projectRoot, "$SRC_DIR/simple/Activity2.java")
    srcModule1 = File(projectRoot, "$SRC_DIR/simple/Module1.java")
    srcModule2 = File(projectRoot, "$SRC_DIR/simple/Module2.java")

    genHiltApp = File(projectRoot, "$GEN_SRC_DIR/simple/Hilt_SimpleApp.java")
    genHiltActivity1 = File(projectRoot, "$GEN_SRC_DIR/simple/Hilt_Activity1.java")
    genHiltActivity2 = File(projectRoot, "$GEN_SRC_DIR/simple/Hilt_Activity2.java")
    genAppInjector = File(projectRoot, "$GEN_SRC_DIR/simple/SimpleApp_GeneratedInjector.java")
    genActivityInjector1 = File(projectRoot, "$GEN_SRC_DIR/simple/Activity1_GeneratedInjector.java")
    genActivityInjector2 = File(projectRoot, "$GEN_SRC_DIR/simple/Activity2_GeneratedInjector.java")
    genAppInjectorDeps = File(
      projectRoot,
      "$GEN_SRC_DIR/hilt_aggregated_deps/simple_SimpleApp_GeneratedInjectorModuleDeps.java"
    )
    genActivityInjectorDeps1 = File(
      projectRoot,
      "$GEN_SRC_DIR/hilt_aggregated_deps/simple_Activity1_GeneratedInjectorModuleDeps.java"
    )
    genActivityInjectorDeps2 = File(
      projectRoot,
      "$GEN_SRC_DIR/hilt_aggregated_deps/simple_Activity2_GeneratedInjectorModuleDeps.java"
    )
    genModuleDeps1 = File(
      projectRoot,
      "$GEN_SRC_DIR/hilt_aggregated_deps/simple_Module1ModuleDeps.java"
    )
    genModuleDeps2 = File(
      projectRoot,
      "$GEN_SRC_DIR/hilt_aggregated_deps/simple_Module2ModuleDeps.java"
    )
    genHiltComponents = File(
      projectRoot,
      "$GEN_SRC_DIR/simple/SimpleApp_HiltComponents.java"
    )
    genDaggerHiltApplicationComponent = File(
      projectRoot,
      "$GEN_SRC_DIR/simple/DaggerSimpleApp_HiltComponents_SingletonC.java"
    )

    classSrcApp = File(projectRoot, "$CLASS_DIR/simple/SimpleApp.class")
    classSrcActivity1 = File(projectRoot, "$CLASS_DIR/simple/Activity1.class")
    classSrcActivity2 = File(projectRoot, "$CLASS_DIR/simple/Activity2.class")
    classSrcModule1 = File(projectRoot, "$CLASS_DIR/simple/Module1.class")
    classSrcModule2 = File(projectRoot, "$CLASS_DIR/simple/Module2.class")
    classGenHiltApp = File(projectRoot, "$CLASS_DIR/simple/Hilt_SimpleApp.class")
    classGenHiltActivity1 = File(projectRoot, "$CLASS_DIR/simple/Hilt_Activity1.class")
    classGenHiltActivity2 = File(projectRoot, "$CLASS_DIR/simple/Hilt_Activity2.class")
    classGenAppInjector = File(projectRoot, "$CLASS_DIR/simple/SimpleApp_GeneratedInjector.class")
    classGenActivityInjector1 = File(
      projectRoot,
      "$CLASS_DIR/simple/Activity1_GeneratedInjector.class"
    )
    classGenActivityInjector2 = File(
      projectRoot,
      "$CLASS_DIR/simple/Activity2_GeneratedInjector.class"
    )
    classGenAppInjectorDeps = File(
      projectRoot,
      "$CLASS_DIR/hilt_aggregated_deps/simple_SimpleApp_GeneratedInjectorModuleDeps.class"
    )
    classGenActivityInjectorDeps1 = File(
      projectRoot,
      "$CLASS_DIR/hilt_aggregated_deps/simple_Activity1_GeneratedInjectorModuleDeps.class"
    )
    classGenActivityInjectorDeps2 = File(
      projectRoot,
      "$CLASS_DIR/hilt_aggregated_deps/simple_Activity2_GeneratedInjectorModuleDeps.class"
    )
    classGenModuleDeps1 = File(
      projectRoot,
      "$CLASS_DIR/hilt_aggregated_deps/simple_Module1ModuleDeps.class"
    )
    classGenModuleDeps2 = File(
      projectRoot,
      "$CLASS_DIR/hilt_aggregated_deps/simple_Module2ModuleDeps.class"
    )
    classGenHiltComponents = File(
      projectRoot,
      "$CLASS_DIR/simple/SimpleApp_HiltComponents.class"
    )
    classGenDaggerHiltApplicationComponent = File(
      projectRoot,
      "$CLASS_DIR/simple/DaggerSimpleApp_HiltComponents_SingletonC.class"
    )
  }

  @Test
  fun firstFullBuild() {
    // This test verifies the results of the first full (non-incremental) build. The other tests
    // verify the results of the second incremental build based on different change scenarios.
    val result = runFullBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    assertFilesExist(
      genHiltApp,
      genHiltActivity1,
      genHiltActivity2,
      genAppInjector,
      genActivityInjector1,
      genActivityInjector2,
      genAppInjectorDeps,
      genActivityInjectorDeps1,
      genActivityInjectorDeps2,
      genModuleDeps1,
      genModuleDeps2,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    assertFilesExist(
      classSrcApp,
      classSrcActivity1,
      classSrcActivity2,
      classSrcModule1,
      classSrcModule2,
      classGenHiltApp,
      classGenHiltActivity1,
      classGenHiltActivity2,
      classGenAppInjector,
      classGenActivityInjector1,
      classGenActivityInjector2,
      classGenAppInjectorDeps,
      classGenActivityInjectorDeps1,
      classGenActivityInjectorDeps2,
      classGenModuleDeps1,
      classGenModuleDeps2,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  @Test
  fun changeActivitySource() {
    runFullBuild()

    // Change Activity 1 source
    searchAndReplace(
      srcActivity1, "// Insert-change",
      """
      @Override
      public void onResume() {
        super.onResume();
      }
      """.trimIndent()
    )

    val result = runIncrementalBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * Only activity 1 sources are re-generated, isolation in modules and from other activities
    // * Root classes along with components are always re-generated (aggregated processor)
    assertChangedFiles(
      FileType.JAVA,
      genHiltApp,
      genHiltActivity1,
      genAppInjector,
      genActivityInjector1,
      genAppInjectorDeps,
      genActivityInjectorDeps1,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    // * Gen sources from activity 1 are re-compiled
    // * All aggregating processor gen sources are re-compiled
    assertChangedFiles(
      FileType.CLASS,
      classSrcActivity1,
      classGenHiltApp,
      classGenHiltActivity1,
      classGenAppInjector,
      classGenActivityInjector1,
      classGenAppInjectorDeps,
      classGenActivityInjectorDeps1,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  @Test
  fun changeModuleSource() {
    runFullBuild()

    // Change Module 1 source
    searchAndReplace(
      srcModule1, "// Insert-change",
      """
      @Provides
      static double provideDouble() {
        return 10.10;
      }
      """.trimIndent()
    )

    val result = runIncrementalBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * Only module 1 sources are re-generated, isolation from other modules
    // * Root classes along with components are always re-generated (aggregated processor)
    assertChangedFiles(
      FileType.JAVA,
      genHiltApp,
      genAppInjector,
      genAppInjectorDeps,
      genModuleDeps1,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    // * Gen sources from module 1 are re-compiled
    // * All aggregating processor gen sources are re-compiled
    assertChangedFiles(
      FileType.CLASS,
      classSrcModule1,
      classGenHiltApp,
      classGenAppInjector,
      classGenAppInjectorDeps,
      classGenModuleDeps1,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  @Test
  fun changeAppSource() {
    runFullBuild()

    // Change Application source
    searchAndReplace(
      srcApp, "// Insert-change",
      """
      @Override
      public void onCreate() {
        super.onCreate();
      }
      """.trimIndent()
    )

    val result = runIncrementalBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * No modules or activities (or any other non-root) classes should be generated
    // * Root classes along with components are always re-generated (aggregated processor)
    assertChangedFiles(
      FileType.JAVA,
      genHiltApp,
      genAppInjector,
      genAppInjectorDeps,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    // * All aggregating processor gen sources are re-compiled
    assertChangedFiles(
      FileType.CLASS,
      classSrcApp, // re-compiles because superclass re-compiled
      classGenHiltApp,
      classGenAppInjector,
      classGenAppInjectorDeps,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  @Test
  fun deleteActivitySource() {
    runFullBuild()

    srcActivity2.delete()

    val result = runIncrementalBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * All related gen classes from activity 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    // * Root classes along with components are always re-generated (aggregated processor)
    assertDeletedFiles(
      genHiltActivity2,
      genActivityInjector2,
      genActivityInjectorDeps2
    )
    assertChangedFiles(
      FileType.JAVA,
      genHiltApp,
      genAppInjector,
      genAppInjectorDeps,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    // * All compiled classes from activity 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    assertDeletedFiles(
      classSrcActivity2,
      classGenHiltActivity2,
      classGenActivityInjector2,
      classGenActivityInjectorDeps2
    )
    assertChangedFiles(
      FileType.CLASS,
      classGenHiltApp,
      classGenAppInjector,
      classGenAppInjectorDeps,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  @Test
  fun deleteModuleSource() {
    runFullBuild()

    srcModule2.delete()

    val result = runIncrementalBuild()
    expect.that(result.task(COMPILE_TASK)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * All related gen classes from module 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    // * Root classes along with components are always re-generated (aggregated processor)
    assertDeletedFiles(
      genModuleDeps2
    )
    assertChangedFiles(
      FileType.JAVA,
      genHiltApp,
      genAppInjector,
      genAppInjectorDeps,
      genHiltComponents,
      genDaggerHiltApplicationComponent
    )

    // Check compilation outputs
    // * All compiled classes from module 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    assertDeletedFiles(
      classSrcModule2,
      classGenModuleDeps2
    )
    assertChangedFiles(
      FileType.CLASS,
      classGenHiltApp,
      classGenAppInjector,
      classGenAppInjectorDeps,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )
  }

  private fun runGradleTasks(vararg args: String): BuildResult {
    return GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(*args)
      .withPluginClasspath()
      .forwardOutput()
      .build()
  }

  private fun runFullBuild(): BuildResult {
    val result = runGradleTasks(CLEAN_TASK, COMPILE_TASK)
    recordTimestamps()
    return result
  }

  private fun runIncrementalBuild(): BuildResult {
    val result = runGradleTasks(COMPILE_TASK)
    recordFileChanges()
    return result
  }
  private fun recordTimestamps() {
    val files = listOf(
      genHiltApp,
      genHiltActivity1,
      genHiltActivity2,
      genAppInjector,
      genActivityInjector1,
      genActivityInjector2,
      genAppInjectorDeps,
      genActivityInjectorDeps1,
      genActivityInjectorDeps2,
      genModuleDeps1,
      genModuleDeps2,
      genHiltComponents,
      genDaggerHiltApplicationComponent,
      classSrcApp,
      classSrcActivity1,
      classSrcActivity2,
      classSrcModule1,
      classSrcModule2,
      classGenHiltApp,
      classGenHiltActivity1,
      classGenHiltActivity2,
      classGenAppInjector,
      classGenActivityInjector1,
      classGenActivityInjector2,
      classGenAppInjectorDeps,
      classGenActivityInjectorDeps1,
      classGenActivityInjectorDeps2,
      classGenModuleDeps1,
      classGenModuleDeps2,
      classGenHiltComponents,
      classGenDaggerHiltApplicationComponent
    )

    fileToTimestampMap = mutableMapOf<File, Long>().apply {
      for (file in files) {
        this[file] = file.lastModified()
      }
    }
  }

  private fun recordFileChanges() {
    changedFiles = fileToTimestampMap.filter { (file, previousTimestamp) ->
      file.exists() && file.lastModified() != previousTimestamp
    }.keys

    unchangedFiles = fileToTimestampMap.filter { (file, previousTimestamp) ->
      file.exists() && file.lastModified() == previousTimestamp
    }.keys

    deletedFiles = fileToTimestampMap.filter { (file, _) -> !file.exists() }.keys
  }

  private fun assertFilesExist(vararg files: File) {
    expect.withMessage("Existing files")
      .that(files.filter { it.exists() })
      .containsExactlyElementsIn(files)
  }

  private fun assertChangedFiles(type: FileType, vararg files: File) {
    expect.withMessage("Changed files")
      .that(changedFiles.filter { it.name.endsWith(type.extension) })
      .containsExactlyElementsIn(files)
  }

  private fun assertDeletedFiles(vararg files: File) {
    expect.withMessage("Deleted files").that(deletedFiles).containsAtLeastElementsIn(files)
  }

  private fun searchAndReplace(file: File, search: String, replace: String) {
    file.writeText(file.readText().replace(search, replace))
  }

  enum class FileType(val extension: String) {
    JAVA(".java"),
    CLASS(".class"),
  }

  companion object {
    private const val SRC_DIR = "src/main/java"
    private const val GEN_SRC_DIR = "build/generated/ap_generated_sources/debug/out/"
    private const val CLASS_DIR = "build/intermediates/javac/debug/classes"

    private const val CLEAN_TASK = ":clean"
    private const val COMPILE_TASK = ":compileDebugJavaWithJavac"
  }
}
