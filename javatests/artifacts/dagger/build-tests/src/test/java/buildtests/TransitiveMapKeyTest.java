/*
 * Copyright (C) 2021 The Dagger Authors.
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

// This is a regression test for https://github.com/google/dagger/issues/3133
@RunWith(JUnit4.class)
public class TransitiveMapKeyTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testTransitiveMapKey_WithImplementation() throws IOException {
    BuildResult result = setupRunnerWith("implementation").buildAndFail();
    assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
    assertThat(result.getOutput())
        .contains(
            "Missing map key annotation for method: library1.MyModule#provideString(). "
                + "That method was annotated with: ["
                + "@dagger.Provides, "
                + "@dagger.multibindings.IntoMap, "
                + "@library2.MyMapKey(\"some-key\")"
                + "]");
  }

  @Test
  public void testTransitiveMapKey_WithApi() throws IOException {
    // Test that if we use an "api" dependency for the custom map key things work properly.
    BuildResult result = setupRunnerWith("api").build();
    assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
  }

  private GradleRunner setupRunnerWith(String dependencyType) throws IOException {
    File projectDir = folder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile(
            "include 'app'",
            "include 'library1'",
            "include 'library2'")
        .addBuildFile(
            "buildscript {",
            "  ext {",
            String.format("dagger_version = \"%s\"", System.getProperty("dagger_version")),
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
            "import library1.MyModule;",
            "import java.util.Map;",
            "",
            "@Component(modules = MyModule.class)",
            "public interface MyComponent {",
            "  Map<String, String> multiMap();",
            "}");

    GradleModule.create(projectDir, "library1")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'java-library'",
            "}",
            "dependencies {",
            dependencyType + " project(':library2')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "MyModule.java",
            "package library1;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import library2.MyMapKey;",
            "",
            "@Module",
            "public interface MyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @MyMapKey(\"some-key\")",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "}");

    GradleModule.create(projectDir, "library2")
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
            "MyMapKey.java",
            "package library2;",
            "",
            "import dagger.MapKey;",
            "",
            "@MapKey",
            "public @interface MyMapKey {",
            "  String value();",
            "}");

    return GradleRunner.create()
        .withArguments("--stacktrace", "build")
        .withProjectDir(projectDir);
  }
}
