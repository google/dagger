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

package buildtests;

import static com.google.common.truth.Truth.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

import java.io.File;
import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for https://github.com/google/dagger/issues/3401
// This issues occurs specifically when the subcomponent factory method is defined in a separate
// kotlin library from the component that implements the subcomponent factory method.
@RunWith(JUnit4.class)
public class TransitiveSubcomponentTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testBuild() throws IOException {
    BuildResult result = setupRunner().build();
    assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
  }

  private GradleRunner setupRunner() throws IOException {
    File projectDir = folder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile(
            "include 'app'",
            "include 'library1'")
        .addBuildFile(
            "buildscript {",
            "  ext {",
            String.format("dagger_version = \"%s\"", System.getProperty("dagger_version")),
            String.format("kotlin_version = \"%s\"", System.getProperty("kotlin_version")),
            "  }",
            "}",
            "",
            "allprojects {",
            "  repositories {",
            "    mavenCentral()",
            "    mavenLocal()",
            "  }",
            "}");

    GradleModule.create(projectDir, "app")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'application'",
            "}",
            "dependencies {",
            "  implementation project(':library1')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "MyComponent.java",
            "package app;",
            "",
            "import dagger.Component;",
            "import library1.MySubcomponent1;",
            "",
            "@Component",
            "interface MyComponent {",
            "  MySubcomponent1 subcomponent1();",
            "}");

    GradleModule.create(projectDir, "library1")
        .addBuildFile(
            "plugins {",
            "  id 'org.jetbrains.kotlin.jvm' version \"$kotlin_version\"",
            "  id 'org.jetbrains.kotlin.kapt' version \"$kotlin_version\"",
            "}",
            "dependencies {",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "MyModule.kt",
            "package library1",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "public class MyModule(private val int: Int) {",
            "  @Provides public fun provideInt(): Int = int",
            "}")
        .addSrcFile(
            "MySubcomponent1.kt",
            "package library1",
            "",
            "import dagger.Subcomponent",
            "",
            "@Subcomponent",
            "public interface MySubcomponent1 {",
            "  public fun subcomponent2(myModule: MyModule): MySubcomponent2",
            "}")
        .addSrcFile(
            "MySubcomponent2.kt",
            "package library1",
            "",
            "import dagger.Subcomponent",
            "",
            "@Subcomponent(modules = [MyModule::class])",
            "public interface MySubcomponent2 {",
            "  public fun integer(): Int",
            "}");

    return GradleRunner.create()
        .withArguments("--stacktrace", "build")
        .withProjectDir(projectDir);
  }
}
