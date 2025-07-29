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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests to verify Gradle annotation processor incremental compilation.
 *
 * To run these tests first deploy artifacts to local maven via util/install-local-snapshot.sh.
 */
@RunWith(Parameterized::class)
class IncrementalProcessorTest(private val incapMode: String) {

  @get:Rule val testProjectDir = TemporaryFolder()

  @get:Rule val expect: Expect = Expect.create()

  // Original source files
  private lateinit var srcApp: File
  private lateinit var srcActivity1: File
  private lateinit var srcActivity2: File
  private lateinit var srcModule1: File
  private lateinit var srcModule2: File
  private lateinit var srcTest1: File
  private lateinit var srcTest2: File

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
  private lateinit var genComponentTreeDeps: File
  private lateinit var genHiltComponents: File
  private lateinit var genDaggerHiltApplicationComponent: File
  private lateinit var genTest1ComponentTreeDeps: File
  private lateinit var genTest2ComponentTreeDeps: File
  private lateinit var genTest1HiltComponents: File
  private lateinit var genTest2HiltComponents: File
  private lateinit var genTest1DaggerHiltApplicationComponent: File
  private lateinit var genTest2DaggerHiltApplicationComponent: File

  // Compiled classes
  private lateinit var classSrcApp: File
  private lateinit var classSrcActivity1: File
  private lateinit var classSrcActivity2: File
  private lateinit var classSrcModule1: File
  private lateinit var classSrcModule2: File
  private lateinit var classSrcTest1: File
  private lateinit var classSrcTest2: File
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
  private lateinit var classGenComponentTreeDeps: File
  private lateinit var classGenHiltComponents: File
  private lateinit var classGenDaggerHiltApplicationComponent: File
  private lateinit var classGenTest1ComponentTreeDeps: File
  private lateinit var classGenTest2ComponentTreeDeps: File
  private lateinit var classGenTest1HiltComponents: File
  private lateinit var classGenTest2HiltComponents: File
  private lateinit var classGenTest1DaggerHiltApplicationComponent: File
  private lateinit var classGenTest2DaggerHiltApplicationComponent: File

  // Timestamps of files
  private lateinit var fileToTimestampMap: Map<File, Long>

  // Sets of files that have changed/not changed/deleted
  private lateinit var changedFiles: Set<File>
  private lateinit var unchangedFiles: Set<File>
  private lateinit var deletedFiles: Set<File>

  private val compileTaskName =
    if (incapMode == ISOLATING_MODE) {
      ":hiltJavaCompileDebug"
    } else {
      ":compileDebugJavaWithJavac"
    }
  private val testCompileTaskName =
    if (incapMode == ISOLATING_MODE) {
      ":hiltJavaCompileDebugUnitTest"
    } else {
      ":compileDebugUnitTestJavaWithJavac"
    }
  private val aggregatingTaskName = ":hiltAggregateDepsDebug"
  private val testAggregatingTaskName = ":hiltAggregateDepsDebugUnitTest"

  @Before
  fun setup() {
    val projectRoot = testProjectDir.root
    // copy test project
    File("src/test/data/simple-project").copyRecursively(projectRoot)

    // set up build file
    File(projectRoot, "build.gradle")
      .writeText(
        """
      buildscript {
        repositories {
          google()
          mavenCentral()
        }
        dependencies {
          classpath 'com.android.tools.build:gradle:7.1.2'
        }
      }

      plugins {
        id 'com.android.application'
        id 'com.google.dagger.hilt.android'
      }

      android {
        compileSdkVersion 33
        buildToolsVersion "33.0.1"

        defaultConfig {
          applicationId "hilt.simple"
          minSdkVersion 21
          targetSdkVersion 33
          javaCompileOptions {
            annotationProcessorOptions {
                arguments += ["dagger.hilt.shareTestComponents" : "true"]
            }
          }
        }

        namespace = "simple"

        compileOptions {
            sourceCompatibility JavaVersion.VERSION_11
            targetCompatibility JavaVersion.VERSION_11
        }
      }

      repositories {
        mavenLocal()
        google()
        mavenCentral()
      }

      dependencies {
        implementation 'androidx.appcompat:appcompat:1.1.0'
        implementation 'com.google.dagger:dagger:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:dagger-compiler:LOCAL-SNAPSHOT'
        implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'
        annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'

        testImplementation 'junit:junit:4.12'
        testImplementation 'androidx.test.ext:junit:1.1.3'
        testImplementation 'androidx.test:runner:1.4.0'
        testImplementation 'org.robolectric:robolectric:4.11.1'
        testImplementation 'com.google.dagger:hilt-android-testing:LOCAL-SNAPSHOT'
        testAnnotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'
      }

      hilt {
        enableAggregatingTask = ${if (incapMode == ISOLATING_MODE) "true" else "false"}
      }
      """
          .trimIndent()
      )

    // Compute directory paths
    val defaultGenSrcDir = "build/generated/ap_generated_sources/debug/out/"
    fun getComponentTreeDepsGenSrcDir(variant: String) =
      if (incapMode == ISOLATING_MODE) {
        "build/generated/hilt/component_trees/$variant/"
      } else {
        "build/generated/ap_generated_sources/$variant/out/"
      }
    val componentTreeDepsGenSrcDir = getComponentTreeDepsGenSrcDir("debug")
    val testComponentTreeDepsGenSrcDir = getComponentTreeDepsGenSrcDir("debugUnitTest")
    fun getRootGenSrcDir(variant: String) =
      if (incapMode == ISOLATING_MODE) {
        "build/generated/hilt/component_sources/$variant/"
      } else {
        "build/generated/ap_generated_sources/$variant/out/"
      }
    val rootGenSrcDir = getRootGenSrcDir("debug")
    val testRootGenSrcDir = getRootGenSrcDir("debugUnitTest")
    val defaultClassesDir = "build/intermediates/javac/debug/classes"
    val testDefaultClassesDir = "build/intermediates/javac/debugUnitTest/classes"
    fun getRootClassesDir(variant: String) =
      if (incapMode == ISOLATING_MODE) {
        "build/intermediates/hilt/component_classes/$variant/"
      } else {
        "build/intermediates/javac/$variant/classes"
      }
    val rootClassesDir = getRootClassesDir("debug")
    val testRootClassesDir = getRootClassesDir("debugUnitTest")

    // Compute file paths
    srcApp = File(projectRoot, "$MAIN_SRC_DIR/simple/SimpleApp.java")
    srcActivity1 = File(projectRoot, "$MAIN_SRC_DIR/simple/Activity1.java")
    srcActivity2 = File(projectRoot, "$MAIN_SRC_DIR/simple/Activity2.java")
    srcModule1 = File(projectRoot, "$MAIN_SRC_DIR/simple/Module1.java")
    srcModule2 = File(projectRoot, "$MAIN_SRC_DIR/simple/Module2.java")
    srcTest1 = File(projectRoot, "$TEST_SRC_DIR/simple/Test1.java")
    srcTest2 = File(projectRoot, "$TEST_SRC_DIR/simple/Test2.java")

    genHiltApp = File(projectRoot, "$rootGenSrcDir/simple/Hilt_SimpleApp.java")
    genHiltActivity1 = File(projectRoot, "$defaultGenSrcDir/simple/Hilt_Activity1.java")
    genHiltActivity2 = File(projectRoot, "$defaultGenSrcDir/simple/Hilt_Activity2.java")
    genAppInjector = File(projectRoot, "$defaultGenSrcDir/simple/SimpleApp_GeneratedInjector.java")
    genActivityInjector1 =
      File(projectRoot, "$defaultGenSrcDir/simple/Activity1_GeneratedInjector.java")
    genActivityInjector2 =
      File(projectRoot, "$defaultGenSrcDir/simple/Activity2_GeneratedInjector.java")
    genAppInjectorDeps =
      File(
        projectRoot,
        "$defaultGenSrcDir/hilt_aggregated_deps/_simple_SimpleApp_GeneratedInjector.java",
      )
    genActivityInjectorDeps1 =
      File(
        projectRoot,
        "$defaultGenSrcDir/hilt_aggregated_deps/_simple_Activity1_GeneratedInjector.java",
      )
    genActivityInjectorDeps2 =
      File(
        projectRoot,
        "$defaultGenSrcDir/hilt_aggregated_deps/_simple_Activity2_GeneratedInjector.java",
      )
    genModuleDeps1 =
      File(projectRoot, "$defaultGenSrcDir/hilt_aggregated_deps/_simple_Module1.java")
    genModuleDeps2 =
      File(projectRoot, "$defaultGenSrcDir/hilt_aggregated_deps/_simple_Module2.java")
    genComponentTreeDeps =
      File(projectRoot, "$componentTreeDepsGenSrcDir/simple/SimpleApp_ComponentTreeDeps.java")
    genHiltComponents = File(projectRoot, "$rootGenSrcDir/simple/SimpleApp_HiltComponents.java")
    genDaggerHiltApplicationComponent =
      File(projectRoot, "$rootGenSrcDir/simple/DaggerSimpleApp_HiltComponents_SingletonC.java")
    genTest1ComponentTreeDeps =
      File(
        projectRoot,
        testComponentTreeDepsGenSrcDir +
          "/dagger/hilt/android/internal/testing/root/Test1_ComponentTreeDeps.java",
      )
    genTest2ComponentTreeDeps =
      File(
        projectRoot,
        testComponentTreeDepsGenSrcDir +
          "/dagger/hilt/android/internal/testing/root/Test2_ComponentTreeDeps.java",
      )
    genTest1HiltComponents =
      File(
        projectRoot,
        "$testRootGenSrcDir/dagger/hilt/android/internal/testing/root/Test1_HiltComponents.java",
      )
    genTest2HiltComponents =
      File(
        projectRoot,
        "$testRootGenSrcDir/dagger/hilt/android/internal/testing/root/Test2_HiltComponents.java",
      )
    genTest1DaggerHiltApplicationComponent =
      File(
        projectRoot,
        testRootGenSrcDir +
          "/dagger/hilt/android/internal/testing/root/DaggerTest1_HiltComponents_SingletonC.java",
      )
    genTest2DaggerHiltApplicationComponent =
      File(
        projectRoot,
        testRootGenSrcDir +
          "/dagger/hilt/android/internal/testing/root/DaggerTest2_HiltComponents_SingletonC.java",
      )

    classSrcApp = File(projectRoot, "$defaultClassesDir/simple/SimpleApp.class")
    classSrcActivity1 = File(projectRoot, "$defaultClassesDir/simple/Activity1.class")
    classSrcActivity2 = File(projectRoot, "$defaultClassesDir/simple/Activity2.class")
    classSrcModule1 = File(projectRoot, "$defaultClassesDir/simple/Module1.class")
    classSrcModule2 = File(projectRoot, "$defaultClassesDir/simple/Module2.class")
    classSrcTest1 = File(projectRoot, "$testDefaultClassesDir/simple/Test1.class")
    classSrcTest2 = File(projectRoot, "$testDefaultClassesDir/simple/Test2.class")
    classGenHiltApp = File(projectRoot, "$rootClassesDir/simple/Hilt_SimpleApp.class")
    classGenHiltActivity1 = File(projectRoot, "$defaultClassesDir/simple/Hilt_Activity1.class")
    classGenHiltActivity2 = File(projectRoot, "$defaultClassesDir/simple/Hilt_Activity2.class")
    classGenAppInjector =
      File(projectRoot, "$defaultClassesDir/simple/SimpleApp_GeneratedInjector.class")
    classGenActivityInjector1 =
      File(projectRoot, "$defaultClassesDir/simple/Activity1_GeneratedInjector.class")
    classGenActivityInjector2 =
      File(projectRoot, "$defaultClassesDir/simple/Activity2_GeneratedInjector.class")
    classGenAppInjectorDeps =
      File(
        projectRoot,
        "$defaultClassesDir/hilt_aggregated_deps/_simple_SimpleApp_GeneratedInjector.class",
      )
    classGenActivityInjectorDeps1 =
      File(
        projectRoot,
        "$defaultClassesDir/hilt_aggregated_deps/_simple_Activity1_GeneratedInjector.class",
      )
    classGenActivityInjectorDeps2 =
      File(
        projectRoot,
        "$defaultClassesDir/hilt_aggregated_deps/_simple_Activity2_GeneratedInjector.class",
      )
    classGenModuleDeps1 =
      File(projectRoot, "$defaultClassesDir/hilt_aggregated_deps/_simple_Module1.class")
    classGenModuleDeps2 =
      File(projectRoot, "$defaultClassesDir/hilt_aggregated_deps/_simple_Module2.class")
    classGenComponentTreeDeps =
      File(projectRoot, "$rootClassesDir/simple/SimpleApp_ComponentTreeDeps.class")
    classGenHiltComponents =
      File(projectRoot, "$rootClassesDir/simple/SimpleApp_HiltComponents.class")
    classGenDaggerHiltApplicationComponent =
      File(projectRoot, "$rootClassesDir/simple/DaggerSimpleApp_HiltComponents_SingletonC.class")
    classGenTest1ComponentTreeDeps =
      File(
        projectRoot,
        testRootClassesDir +
          "/dagger/hilt/android/internal/testing/root/Test1_ComponentTreeDeps.class",
      )
    classGenTest2ComponentTreeDeps =
      File(
        projectRoot,
        testRootClassesDir +
          "/dagger/hilt/android/internal/testing/root/Test2_ComponentTreeDeps.class",
      )
    classGenTest1HiltComponents =
      File(
        projectRoot,
        "$testRootClassesDir/dagger/hilt/android/internal/testing/root/Test1_HiltComponents.class",
      )
    classGenTest2HiltComponents =
      File(
        projectRoot,
        "$testRootClassesDir/dagger/hilt/android/internal/testing/root/Test2_HiltComponents.class",
      )
    classGenTest1DaggerHiltApplicationComponent =
      File(
        projectRoot,
        testRootClassesDir +
          "/dagger/hilt/android/internal/testing/root/DaggerTest1_HiltComponents_SingletonC.class",
      )
    classGenTest2DaggerHiltApplicationComponent =
      File(
        projectRoot,
        testRootClassesDir +
          "/dagger/hilt/android/internal/testing/root/DaggerTest2_HiltComponents_SingletonC.class",
      )
  }

  @Test
  fun firstFullBuild() {
    // This test verifies the results of the first full (non-incremental) build. The other tests
    // verify the results of the second incremental build based on different change scenarios.
    val result = runFullBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    assertFilesExist(
      listOf(
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
        genComponentTreeDeps,
        genHiltComponents,
        genDaggerHiltApplicationComponent,
      )
    )

    // Check compilation outputs
    assertFilesExist(
      listOf(
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
        classGenComponentTreeDeps,
        classGenHiltComponents,
        classGenDaggerHiltApplicationComponent,
      )
    )
  }

  @Test
  fun changeActivitySource_addPublicMethod() {
    runFullBuild()
    val componentTreeDepsFullBuild = genComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Activity 1 source
    searchAndReplace(
      srcActivity1,
      "// Insert-change",
      """
      @Override
      public void onResume() {
        super.onResume();
      }
      """
        .trimIndent(),
    )

    val result = runIncrementalBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * Only activity 1 sources are re-generated, isolation in modules and from other activities
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task did not run, no change in deps
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        // * Components are re-generated due to a recompilation of a dep
        listOf(
          genHiltApp, // Re-gen because components got re-gen
          genHiltActivity1,
          genActivityInjector1,
          genActivityInjectorDeps1,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      } else {
        // * Root classes along with components are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genHiltActivity1,
          genAppInjector,
          genActivityInjector1,
          genAppInjectorDeps,
          genActivityInjectorDeps1,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val componentTreeDepsIncrementalBuild = genComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(componentTreeDepsFullBuild)
      .isEqualTo(componentTreeDepsIncrementalBuild)

    // Check compilation outputs
    // * Gen sources from activity 1 are re-compiled
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classSrcActivity1,
          classGenHiltApp,
          classGenHiltActivity1,
          classGenActivityInjector1,
          classGenActivityInjectorDeps1,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      } else {
        // * All aggregating processor gen sources are re-compiled
        listOf(
          classSrcActivity1,
          classGenHiltApp,
          classGenHiltActivity1,
          classGenAppInjector,
          classGenActivityInjector1,
          classGenAppInjectorDeps,
          classGenActivityInjectorDeps1,
          classGenHiltComponents,
          classGenComponentTreeDeps,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun changeActivitySource_addPrivateMethod() {
    runFullBuild()
    val componentTreeDepsFullBuild = genComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Activity 1 source
    searchAndReplace(
      srcActivity1,
      "// Insert-change",
      """
      private void foo() { }
      """
        .trimIndent(),
    )

    val result = runIncrementalBuild()
    val expectedOutcome =
      if (incapMode == ISOLATING_MODE) {
        // In isolating mode, changes that do not affect ABI will not cause re-compilation.
        TaskOutcome.UP_TO_DATE
      } else {
        TaskOutcome.SUCCESS
      }
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(expectedOutcome)

    // Check annotation processing outputs
    // * Only activity 1 sources are re-generated, isolation in modules and from other activities
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task did not run, no change in deps
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        listOf(genHiltActivity1, genActivityInjector1, genActivityInjectorDeps1)
      } else {
        // * Root classes along with components are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genHiltActivity1,
          genAppInjector,
          genActivityInjector1,
          genAppInjectorDeps,
          genActivityInjectorDeps1,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val componentTreeDepsIncrementalBuild = genComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(componentTreeDepsFullBuild)
      .isEqualTo(componentTreeDepsIncrementalBuild)

    // Check compilation outputs
    // * Gen sources from activity 1 are re-compiled
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classSrcActivity1,
          classGenHiltActivity1,
          classGenActivityInjector1,
          classGenActivityInjectorDeps1,
        )
      } else {
        // * All aggregating processor gen sources are re-compiled
        listOf(
          classSrcActivity1,
          classGenHiltApp,
          classGenHiltActivity1,
          classGenAppInjector,
          classGenActivityInjector1,
          classGenAppInjectorDeps,
          classGenActivityInjectorDeps1,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun changeModuleSource() {
    runFullBuild()
    val componentTreeDepsFullBuild = genComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Module 1 source
    searchAndReplace(
      srcModule1,
      "// Insert-change",
      """
      @Provides
      static double provideDouble() {
        return 10.10;
      }
      """
        .trimIndent(),
    )

    val result = runIncrementalBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * Only module 1 sources are re-generated, isolation from other modules
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task did not run, no change in deps
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        // * Components are re-generated due to a recompilation of a dep
        listOf(
          genHiltApp, // Re-generated because components got re-generated
          genModuleDeps1,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      } else {
        // * Root classes along with components are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genAppInjector,
          genAppInjectorDeps,
          genModuleDeps1,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val componentTreeDepsIncrementalBuild = genComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(componentTreeDepsFullBuild)
      .isEqualTo(componentTreeDepsIncrementalBuild)

    // Check compilation outputs
    // * Gen sources from module 1 are re-compiled
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classSrcModule1,
          classGenHiltApp,
          classGenModuleDeps1,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      } else {
        // * All aggregating processor gen sources are re-compiled
        listOf(
          classSrcModule1,
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenModuleDeps1,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun changeAppSource() {
    runFullBuild()
    val componentTreeDepsFullBuild = genComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Application source
    searchAndReplace(
      srcApp,
      "// Insert-change",
      """
      @Override
      public void onCreate() {
        super.onCreate();
      }
      """
        .trimIndent(),
    )

    val result = runIncrementalBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * No modules or activities (or any other non-root) classes should be generated
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task did not run, no change in deps
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        // * Components are re-generated due to a recompilation of a dep
        listOf(
          genHiltApp, // Re-generated because components got re-generated
          genAppInjector,
          genAppInjectorDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      } else {
        // * Root classes along with components are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genAppInjector,
          genAppInjectorDeps,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val componentTreeDepsIncrementalBuild = genComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(componentTreeDepsFullBuild)
      .isEqualTo(componentTreeDepsIncrementalBuild)

    // Check compilation outputs
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classSrcApp,
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      } else {
        // * All aggregating processor gen sources are re-compiled
        listOf(
          classSrcApp,
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun deleteActivitySource() {
    runFullBuild()

    srcActivity2.delete()

    val result = runIncrementalBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * All related gen classes from activity 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    // * Root classes along with components are always re-generated (aggregated processor)
    assertDeletedFiles(listOf(genHiltActivity2, genActivityInjector2, genActivityInjectorDeps2))
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task ran due to a change in dep
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        // * Components are re-generated since there was a change in dep
        listOf(
          genHiltApp, // Re-generated because components got re-generated
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      } else {
        listOf(
          genHiltApp,
          genAppInjector,
          genAppInjectorDeps,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    // Check compilation outputs
    // * All compiled classes from activity 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    assertDeletedFiles(
      listOf(
        classSrcActivity2,
        classGenHiltActivity2,
        classGenActivityInjector2,
        classGenActivityInjectorDeps2,
      )
    )
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classGenHiltApp,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      } else {
        listOf(
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun deleteModuleSource() {
    runFullBuild()

    srcModule2.delete()

    val result = runIncrementalBuild()
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * All related gen classes from module 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged

    assertDeletedFiles(listOf(genModuleDeps2))
    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task ran due to a change in dep
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        // * Components are re-generated since there was a change in dep
        listOf(
          genHiltApp, // Re-generated because components got re-generated
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      } else {
        // * Root classes along with components are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genAppInjector,
          genAppInjectorDeps,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    // Check compilation outputs
    // * All compiled classes from module 2 should be deleted
    // * Unrelated activities and modules are in isolation and should be unchanged
    assertDeletedFiles(listOf(classSrcModule2, classGenModuleDeps2))
    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classGenHiltApp,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      } else {
        listOf(
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun addNewSource() {
    runFullBuild()

    val newSource = File(testProjectDir.root, "$MAIN_SRC_DIR/simple/Foo.java")
    newSource.writeText(
      """
        package simple;

        public class Foo { }
      """
        .trimIndent()
    )

    val result = runIncrementalBuild()
    val expectedOutcome =
      if (incapMode == ISOLATING_MODE) {
        // In isolating mode, component compile task does not re-compile.
        TaskOutcome.UP_TO_DATE
      } else {
        TaskOutcome.SUCCESS
      }
    expect.that(result.task(compileTaskName)!!.outcome).isEqualTo(expectedOutcome)

    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        // * Aggregating task did not run, no change in deps
        expect.that(result.task(aggregatingTaskName)!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        // * Non-DI related source causes no files to be generated
        emptyList()
      } else {
        // * Root classes are always re-generated (aggregated processor)
        listOf(
          genHiltApp,
          genAppInjector,
          genAppInjectorDeps,
          genComponentTreeDeps,
          genHiltComponents,
          genDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        emptyList()
      } else {
        listOf(
          classGenHiltApp,
          classGenAppInjector,
          classGenAppInjectorDeps,
          classGenComponentTreeDeps,
          classGenHiltComponents,
          classGenDaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun firstTestFullBuild() {
    val result = runFullTestBuild()
    expect.that(result.task(testCompileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    assertFilesExist(
      listOf(
        genTest1ComponentTreeDeps,
        genTest2ComponentTreeDeps,
        genTest1HiltComponents,
        genTest2HiltComponents,
        genTest1DaggerHiltApplicationComponent,
        genTest2DaggerHiltApplicationComponent,
      )
    )

    assertFilesExist(
      listOf(
        classSrcTest1,
        classSrcTest2,
        classGenTest1ComponentTreeDeps,
        classGenTest2ComponentTreeDeps,
        classGenTest1HiltComponents,
        classGenTest2HiltComponents,
        classGenTest1DaggerHiltApplicationComponent,
        classGenTest2DaggerHiltApplicationComponent,
      )
    )
  }

  @Test
  fun changeTestSource_addPublicMethod() {
    runFullTestBuild()
    val test1ComponentTreeDepsFullBuild = genTest1ComponentTreeDeps.readText(Charsets.UTF_8)
    val test2ComponentTreeDepsFullBuild = genTest2ComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Test 1 source
    searchAndReplace(
      srcTest1,
      "// Insert-change",
      """
      @Test
      public void newTest() { }
      """
        .trimIndent(),
    )

    val result = runIncrementalTestBuild()
    expect.that(result.task(testCompileTaskName)!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // Check annotation processing outputs
    // * Unrelated test components should be unchanged

    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(genTest1HiltComponents, genTest1DaggerHiltApplicationComponent)
      } else {
        listOf(
          genTest1ComponentTreeDeps,
          genTest2ComponentTreeDeps,
          genTest1HiltComponents,
          genTest2HiltComponents,
          genTest1DaggerHiltApplicationComponent,
          genTest2DaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val test1ComponentTreeDepsIncrementalBuild = genTest1ComponentTreeDeps.readText(Charsets.UTF_8)
    val test2ComponentTreeDepsIncrementalBuild = genTest2ComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(test1ComponentTreeDepsFullBuild)
      .isEqualTo(test1ComponentTreeDepsIncrementalBuild)
    expect
      .withMessage("Full build")
      .that(test2ComponentTreeDepsFullBuild)
      .isEqualTo(test2ComponentTreeDepsIncrementalBuild)

    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(
          classSrcTest1,
          classGenTest1HiltComponents,
          classGenTest1DaggerHiltApplicationComponent,
        )
      } else {
        listOf(
          classSrcTest1,
          classGenTest1ComponentTreeDeps,
          classGenTest2ComponentTreeDeps,
          classGenTest1HiltComponents,
          classGenTest2HiltComponents,
          classGenTest1DaggerHiltApplicationComponent,
          classGenTest2DaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
  }

  @Test
  fun changeTestSource_addPrivateMethod() {
    runFullTestBuild()
    val test1ComponentTreeDepsFullBuild = genTest1ComponentTreeDeps.readText(Charsets.UTF_8)
    val test2ComponentTreeDepsFullBuild = genTest2ComponentTreeDeps.readText(Charsets.UTF_8)

    // Change Test 1 source
    searchAndReplace(
      srcTest1,
      "// Insert-change",
      """
      private void newMethod() { }
      """
        .trimIndent(),
    )

    val result = runIncrementalTestBuild()
    val expectedOutcome =
      if (incapMode == ISOLATING_MODE) {
        // In isolating mode, changes that do not affect ABI will not cause re-compilation.
        TaskOutcome.UP_TO_DATE
      } else {
        TaskOutcome.SUCCESS
      }
    expect.that(result.task(testCompileTaskName)!!.outcome).isEqualTo(expectedOutcome)

    // Check annotation processing outputs
    // * Unrelated test components should be unchanged

    val regeneratedSourceFiles =
      if (incapMode == ISOLATING_MODE) {
        emptyList()
      } else {
        listOf(
          genTest1ComponentTreeDeps,
          genTest2ComponentTreeDeps,
          genTest1HiltComponents,
          genTest2HiltComponents,
          genTest1DaggerHiltApplicationComponent,
          genTest2DaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.JAVA, regeneratedSourceFiles)

    val test1ComponentTreeDepsIncrementalBuild = genTest1ComponentTreeDeps.readText(Charsets.UTF_8)
    val test2ComponentTreeDepsIncrementalBuild = genTest2ComponentTreeDeps.readText(Charsets.UTF_8)
    expect
      .withMessage("Full build")
      .that(test1ComponentTreeDepsFullBuild)
      .isEqualTo(test1ComponentTreeDepsIncrementalBuild)
    expect
      .withMessage("Full build")
      .that(test2ComponentTreeDepsFullBuild)
      .isEqualTo(test2ComponentTreeDepsIncrementalBuild)

    val recompiledClassFiles =
      if (incapMode == ISOLATING_MODE) {
        listOf(classSrcTest1)
      } else {
        listOf(
          classSrcTest1,
          classGenTest1ComponentTreeDeps,
          classGenTest2ComponentTreeDeps,
          classGenTest1HiltComponents,
          classGenTest2HiltComponents,
          classGenTest1DaggerHiltApplicationComponent,
          classGenTest2DaggerHiltApplicationComponent,
        )
      }
    assertChangedFiles(FileType.CLASS, recompiledClassFiles)
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
    val result = runGradleTasks(CLEAN_TASK, compileTaskName)
    recordTimestamps()
    return result
  }

  private fun runFullTestBuild(): BuildResult {
    runFullBuild()
    val result = runIncrementalTestBuild()
    recordTimestamps()
    return result
  }

  private fun runIncrementalBuild(): BuildResult {
    val result = runGradleTasks(compileTaskName)
    recordFileChanges()
    return result
  }

  private fun runIncrementalTestBuild(): BuildResult {
    val result = runGradleTasks(testCompileTaskName)
    recordFileChanges()
    return result
  }

  private fun recordTimestamps() {
    val files =
      listOf(
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
        genComponentTreeDeps,
        genHiltComponents,
        genDaggerHiltApplicationComponent,
        genTest1ComponentTreeDeps,
        genTest2ComponentTreeDeps,
        genTest1HiltComponents,
        genTest2HiltComponents,
        genTest1DaggerHiltApplicationComponent,
        genTest2DaggerHiltApplicationComponent,
        classSrcApp,
        classSrcActivity1,
        classSrcActivity2,
        classSrcModule1,
        classSrcModule2,
        classSrcTest1,
        classSrcTest2,
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
        classGenComponentTreeDeps,
        classGenHiltComponents,
        classGenDaggerHiltApplicationComponent,
        classGenTest1ComponentTreeDeps,
        classGenTest2ComponentTreeDeps,
        classGenTest1HiltComponents,
        classGenTest2HiltComponents,
        classGenTest1DaggerHiltApplicationComponent,
        classGenTest2DaggerHiltApplicationComponent,
      )

    fileToTimestampMap =
      mutableMapOf<File, Long>().apply {
        for (file in files) {
          this[file] = file.lastModified()
        }
      }
  }

  private fun recordFileChanges() {
    changedFiles =
      fileToTimestampMap
        .filter { (file, previousTimestamp) ->
          file.exists() && file.lastModified() != previousTimestamp
        }
        .keys

    unchangedFiles =
      fileToTimestampMap
        .filter { (file, previousTimestamp) ->
          file.exists() && file.lastModified() == previousTimestamp
        }
        .keys

    deletedFiles = fileToTimestampMap.filter { (file, _) -> !file.exists() }.keys
  }

  private fun assertFilesExist(files: Collection<File>) {
    expect
      .withMessage("Existing files")
      .that(files.filter { it.exists() })
      .containsExactlyElementsIn(files)
  }

  private fun assertChangedFiles(type: FileType, files: Collection<File>) {
    expect
      .withMessage("Changed files")
      .that(changedFiles.filter { it.name.endsWith(type.extension) })
      .containsExactlyElementsIn(files)
  }

  private fun assertDeletedFiles(files: Collection<File>) {
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

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters() = listOf(ISOLATING_MODE, AGGREGATING_MODE)

    private const val ISOLATING_MODE = "isolating"
    private const val AGGREGATING_MODE = "aggregating"

    private const val MAIN_SRC_DIR = "src/main/java"
    private const val TEST_SRC_DIR = "src/test/java"
    private const val CLEAN_TASK = ":clean"
  }
}
