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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IncrementalProcessingTest {
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  // This is a regression test for https://github.com/google/dagger/issues/4054.
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

  // This is a regression test for https://github.com/google/dagger/issues/4878
  // (filed upstream in KSP as https://github.com/google/ksp/issues/2536).
  @Test
  public void testIncrementalProcessingWithGeneratedModule() throws IOException {
    File projectDir = tmpFolder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile("include 'app', 'processor'")
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

    GradleModule.create(projectDir, "processor")
        .addBuildFile(
            "plugins {",
            "  id 'org.jetbrains.kotlin.jvm' version \"$kotlin_version\"",
            "}",
            "dependencies {",
            "  implementation \"com.google.devtools.ksp:symbol-processing-api:$ksp_version\"",
            "}")
        .addFile(
            "src/main/resources/META-INF/services/"
                + "com.google.devtools.ksp.processing.SymbolProcessorProvider",
            "processor.ModuleGeneratingProcessorProvider")
        .addSrcFile(
            "processor/ModuleGeneratingProcessor.kt",
            "package processor",
            "",
            "import com.google.devtools.ksp.processing.Dependencies",
            "import com.google.devtools.ksp.processing.Resolver",
            "import com.google.devtools.ksp.processing.SymbolProcessor",
            "import com.google.devtools.ksp.processing.SymbolProcessorEnvironment",
            "import com.google.devtools.ksp.processing.SymbolProcessorProvider",
            "import com.google.devtools.ksp.symbol.KSAnnotated",
            "import com.google.devtools.ksp.symbol.KSClassDeclaration",
            "",
            "class ModuleGeneratingProcessorProvider : SymbolProcessorProvider {",
            "  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =",
            "      ModuleGeneratingProcessor(environment)",
            "}",
            "",
            "private class ModuleGeneratingProcessor(",
            "  private val environment: SymbolProcessorEnvironment,",
            ") : SymbolProcessor {",
            "  override fun process(resolver: Resolver): List<KSAnnotated> {",
            "    resolver",
            "        .getSymbolsWithAnnotation(\"app.TargetAnnotation\")",
            "        .filterIsInstance<KSClassDeclaration>()",
            "        .forEach { declaration ->",
            "          val generatedName = \"Generated_${declaration.simpleName.asString()}Module\"",
            "          environment.codeGenerator",
            "              .createNewFile(",
            "                  dependencies =",
            "                      Dependencies(aggregating = false, declaration.containingFile!!),",
            "                  packageName = \"app.generated\",",
            "                  fileName = generatedName,",
            "              )",
            "              .writer()",
            "              .use {",
            "                it.write(",
            "                    \"\"\"",
            "                    package app.generated",
            "",
            "                    @dagger.Module",
            "                    class $generatedName {",
            "                      @dagger.Provides",
            "                      fun provide(): app.ClassProvidedByGeneratedModule =",
            "                          app.ClassProvidedByGeneratedModule()",
            "                    }",
            "                    \"\"\".trimIndent()",
            "                )",
            "              }",
            "        }",
            "    return emptyList()",
            "  }",
            "}");

    String otherClassContent =
        String.join(
            "\n",
            "package app",
            "import javax.inject.Inject",
            "class OtherClass @Inject constructor() {",
            "  fun doWork() {}",
            "}");

    GradleModule appModule =
        GradleModule.create(projectDir, "app")
            .addBuildFile(
                "plugins {",
                "  id 'org.jetbrains.kotlin.jvm' version \"$kotlin_version\"",
                "  id 'com.google.devtools.ksp' version \"$ksp_version\"",
                "}",
                "dependencies {",
                "  implementation \"com.google.dagger:dagger:$dagger_version\"",
                "  ksp project(':processor')",
                "  ksp \"com.google.dagger:dagger-compiler:$dagger_version\"",
                "}")
            .addSrcFile(
                "AnnotatedClass.kt",
                "package app",
                "@Target(AnnotationTarget.CLASS)",
                "@Retention(AnnotationRetention.BINARY)",
                "annotation class TargetAnnotation",
                "class ClassProvidedByGeneratedModule",
                "@TargetAnnotation",
                "class AnnotatedClass")
            .addSrcFile("OtherClass.kt", otherClassContent)
            .addSrcFile(
                "MyComponent.kt",
                "package app",
                "import dagger.Component",
                "@Component(modules = [app.generated.Generated_AnnotatedClassModule::class])",
                "interface MyComponent {",
                "  fun classProvidedByGeneratedModule(): ClassProvidedByGeneratedModule",
                "  fun otherClass(): OtherClass",
                "}");

    BuildResult initialBuild = build(projectDir);
    assertThat(initialBuild.task(":app:kspKotlin").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Set<String> initialGeneratedFiles = getAllKspGeneratedFileNames(appModule.getDir());
    assertThat(initialGeneratedFiles)
        .containsExactly(
            "Generated_AnnotatedClassModule.kt",
            "Generated_AnnotatedClassModule_ProvideFactory.java",
            "OtherClass_Factory.java",
            "DaggerMyComponent.java");

    // Modify OtherClass.kt while leaving AnnotatedClass.kt untouched so the generated module's
    // originating source is not dirtied.
    // TODO(user): Update to build() once fixed in KSP
    // (https://github.com/google/ksp/issues/2536).
    appModule.addSrcFile("OtherClass.kt", otherClassContent.replace("doWork()", "doWork2()"));
    BuildResult incrementalBuild = buildAndFail(projectDir);
    assertThat(incrementalBuild.task(":app:kspKotlin").getOutcome()).isEqualTo(TaskOutcome.FAILED);
    assertThat(incrementalBuild.getOutput())
        .contains(
            "ComponentProcessingStep was unable to process 'app.MyComponent' because "
                + "'generated.Generated_AnnotatedClassModule' could not be resolved.");
  }

  // This is a regression test for https://github.com/google/dagger/issues/5363.
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
    return gradleRunner(projectDir).build();
  }

  private static BuildResult buildAndFail(File projectDir) {
    return gradleRunner(projectDir).buildAndFail();
  }

  private static GradleRunner gradleRunner(File projectDir) {
    return GradleRunner.create()
        .withArguments("--stacktrace", "--no-build-cache", "-Pksp.incremental=true", "build")
        .withProjectDir(projectDir);
  }

  private static Set<String> getAllKspGeneratedFileNames(Path moduleDir) throws IOException {
    Path javaDir = moduleDir.resolve("build/generated/ksp/main/java/");
    Path kotlinDir = moduleDir.resolve("build/generated/ksp/main/kotlin/");
    if (!Files.isDirectory(javaDir) && !Files.isDirectory(kotlinDir)) {
      throw new IllegalArgumentException(
          "Expected KSP generated directory under: " + moduleDir.resolve("build/generated/ksp/"));
    }
    Set<String> files = new LinkedHashSet<>();
    if (Files.isDirectory(javaDir)) {
      files.addAll(getAllFileNames(javaDir));
    }
    if (Files.isDirectory(kotlinDir)) {
      files.addAll(getAllFileNames(kotlinDir));
    }
    return files;
  }

  private static Set<String> getAllFileNames(Path dir) throws IOException {
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .map(file -> file.getFileName().toString())
          .collect(Collectors.toSet());
    }
  }
}
