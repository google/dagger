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

import java.io.File;
import java.io.IOException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for https://github.com/google/dagger/issues/3522
@RunWith(JUnit4.class)
public class InjectClassNonDaggerMethodTest {
  private static final String JAVAC_ERROR_MESSAGE =
      "Foo.java:8: error: cannot find symbol";

  private static final String DAGGER_ERROR_MESSAGE =
      "InjectProcessingStep was unable to process 'Foo()' because 'MissingClass' could not be "
          + "resolved";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testInjectMethod() throws IOException {
    BuildResult result = setupRunner(/* useInject= */ true).buildAndFail();

    // Assert that the inject method has both a javac error and a dagger error.
    assertThat(result.getOutput()).contains("Task :library1:compileJava FAILED");
    assertThat(result.getOutput()).contains(JAVAC_ERROR_MESSAGE);
    assertThat(result.getOutput()).contains(DAGGER_ERROR_MESSAGE);
  }

  @Test
  public void testNonInjectMethod() throws IOException {
    BuildResult result = setupRunner(/* useInject= */ false).buildAndFail();

    // Assert that the non-inject method has a javac error but not a dagger error.
    assertThat(result.getOutput()).contains("Task :library1:compileJava FAILED");
    assertThat(result.getOutput()).contains(JAVAC_ERROR_MESSAGE);
    assertThat(result.getOutput()).doesNotContain(DAGGER_ERROR_MESSAGE);
  }

  private GradleRunner setupRunner(boolean useInject) throws IOException {
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
            "import library1.Foo;",
            "",
            "@Component",
            "interface MyComponent {",
            "  Foo foo();",
            "}");

    GradleModule.create(projectDir, "library1")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'java-library'",
            "}",
            "dependencies {",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "Foo.java",
            "package library1;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "",
            useInject
                ? "  @Inject void method(MissingClass missingClass) {}"
                : "  void method(MissingClass missingClass) {}",
            "}");

    return GradleRunner.create()
        .withArguments("--stacktrace", "build")
        .withProjectDir(projectDir);
  }
}
