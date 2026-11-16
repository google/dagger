/*
 * Copyright (C) 2023 The Dagger Authors.
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

package buildtests;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for https://github.com/google/dagger/issues/4054
@RunWith(JUnit4.class)
public class IncrementalProcessingTest {
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Test
  public void testIncrementalProcessing() throws IOException {
    File projectDir = tmpFolder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile("include 'app'")
        .addBuildFile(
            "buildscript {",
            "  ext {",
            String.format("dagger_version = \"%s\"", System.getProperty("dagger_version")),
            String.format("kotlin_version = \"%s\"", System.getProperty("kotlin_version")),
            String.format("ksp_version = \"%s\"", System.getProperty("ksp_version")),
            "  }",
            "}",
            "",
            "allprojects {",
            "  repositories {",
            "    mavenCentral()",
            "    mavenLocal()",
            "  }",
            "}");

    GradleModule appModule =
        GradleModule.create(projectDir, "app")
            .addBuildFile(
                "plugins {",
                "  id 'application'",
                "  id 'org.jetbrains.kotlin.jvm' version \"$kotlin_version\"",
                "  id 'com.google.devtools.ksp' version \"$ksp_version\"",
                "}",
                "dependencies {",
                "  implementation \"org.jetbrains.kotlin:kotlin-stdlib\"",
                "  implementation \"com.google.dagger:dagger:$dagger_version\"",
                "  ksp \"com.google.dagger:dagger-compiler:$dagger_version\"",
                "}")
            // Note: both A and AFactory need to be in the same source file for this to test the
            // regression in https://github.com/google/dagger/issues/4054.
            .addSrcFile(
                "A.kt",
                "package app",
                "",
                "import dagger.assisted.AssistedFactory",
                "import dagger.assisted.AssistedInject",
                "",
                "class A @AssistedInject constructor()",
                "",
                "@AssistedFactory",
                "interface AFactory {",
                "    fun create(): A",
                "}");

    // We'll be changing the contents of MyComponent between builds, so store it in a variable.
    String myComponentContent =
        String.join(
            "\n",
            "package app",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface MyComponent {",
            "  fun factory(): AFactory",
            "}");
    appModule.addSrcFile("MyComponent.kt",  myComponentContent);

    // Build #1
    build(projectDir);
    assertThat(getAllKspGeneratedFileNames(appModule.getDir()))
        .containsExactly(
            "A_Factory.java",
            "AFactory_Impl.java",
            "DaggerMyComponent.java");

    // Change method name in MyComponent.kt to trigger incremental processing of only MyComponent.
    appModule.addSrcFile("MyComponent.kt",  myComponentContent.replace("factory()", "factory2()"));

    // Build #2
    build(projectDir);
    assertThat(getAllKspGeneratedFileNames(appModule.getDir()))
        .containsExactly(
            "A_Factory.java",
            "AFactory_Impl.java",
            "DaggerMyComponent.java");
  }

  // Regression test for https://github.com/google/dagger/issues/4878.
  @Test
  public void testIncrementalProcessingWithGeneratedAndroidInjectorModules() throws IOException {
    File projectDir = tmpFolder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile("include 'app'")
        .addBuildFile(
            "buildscript {",
            "  ext {",
            String.format("dagger_version = \"%s\"", System.getProperty("dagger_version")),
            String.format("kotlin_version = \"%s\"", System.getProperty("kotlin_version")),
            String.format("ksp_version = \"%s\"", System.getProperty("ksp_version")),
            "  }",
            "}",
            "allprojects {",
            "  repositories {",
            "    mavenCentral()",
            "    mavenLocal()",
            "  }",
            "}");

    GradleModule appModule =
        GradleModule.create(projectDir, "app")
            .addBuildFile(
                "plugins {",
                "  id 'org.jetbrains.kotlin.jvm' version \"$kotlin_version\"",
                "  id 'com.google.devtools.ksp' version \"$ksp_version\"",
                "}",
                // This JVM test only needs the classes in dagger-android's AAR.
                "configurations { daggerAndroid }",
                "def extractDaggerAndroid = tasks.register('extractDaggerAndroid', Copy) {",
                "  from { zipTree(configurations.daggerAndroid.singleFile) }",
                "  include 'classes.jar'",
                "  into layout.buildDirectory.dir('dagger-android')",
                "}",
                "dependencies {",
                "  implementation \"com.google.dagger:dagger:$dagger_version\"",
                "  daggerAndroid \"com.google.dagger:dagger-android:$dagger_version@aar\"",
                "  implementation files(layout.buildDirectory.file('dagger-android/classes.jar'))",
                "      .builtBy(extractDaggerAndroid)",
                "  ksp \"com.google.dagger:dagger-compiler:$dagger_version\"",
                "  ksp \"com.google.dagger:dagger-android-processor:$dagger_version\"",
                "}")
            .addSrcFile(
                "MainActivity.kt",
                "package app",
                "import javax.inject.Inject",
                "class MainActivity { @Inject lateinit var dependency: Dependency }",
                "class Dependency @Inject constructor()")
            .addSrcFile(
                "ActivityModule.kt",
                "package app",
                "import dagger.Module",
                "import dagger.android.ContributesAndroidInjector",
                "@Module",
                "abstract class ActivityModule {",
                "  @ContributesAndroidInjector abstract fun activity(): MainActivity",
                "}")
            // Two generated modules are required: keeping only the last origin loses one of them.
            .addSrcFile(
                "ServiceModule.kt",
                "package app",
                "import dagger.Module",
                "import dagger.android.ContributesAndroidInjector",
                "class Service",
                "@Module",
                "abstract class ServiceModule {",
                "  @ContributesAndroidInjector abstract fun service(): Service",
                "}");

    String componentContent =
        String.join(
            "\n",
            "package app",
            "import dagger.Component",
            "import dagger.Module",
            "@Module(includes = [ActivityModule::class, ServiceModule::class])",
            "class AppModule",
            "@Component(modules = [AppModule::class])",
            "interface MyComponent { fun dependency(): Dependency }");
    appModule.addSrcFile("MyComponent.kt", componentContent);

    BuildResult initialBuild = build(projectDir);
    assertThat(initialBuild.task(":app:kspKotlin").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Set<String> initialGeneratedFiles = getAllKspGeneratedFileNames(appModule.getDir());
    assertThat(initialGeneratedFiles)
        .containsAtLeast(
            "ActivityModule_Activity.java", "ServiceModule_Service.java", "DaggerMyComponent.java");

    // Leave both injector modules unchanged and regenerate only the component's source file.
    appModule.addSrcFile(
        "MyComponent.kt", componentContent.replace("dependency()", "dependency2()"));
    BuildResult incrementalBuild = build(projectDir);
    assertThat(incrementalBuild.task(":app:kspKotlin").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(getAllKspGeneratedFileNames(appModule.getDir()))
        .containsExactlyElementsIn(initialGeneratedFiles);
  }

  private static BuildResult build(File projectDir) {
    return GradleRunner.create()
        .withArguments("--stacktrace", "--no-build-cache", "-Pksp.incremental=true", "build")
        .withProjectDir(projectDir)
        .build();
  }

  private static Set<String> getAllKspGeneratedFileNames(Path moduleDir) throws IOException {
    return getAllFileNames(moduleDir.resolve("build/generated/ksp/main/java/"));
  }

  private static Set<String> getAllFileNames(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      throw new IllegalArgumentException("Expected directory: " + dir);
    }
    return Files.walk(dir)
        .filter(Files::isRegularFile)
        .map(file -> file.getFileName().toString())
        .collect(Collectors.toSet());
  }
}
