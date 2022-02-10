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
import java.util.Arrays;
import java.util.Collection;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for https://github.com/google/dagger/issues/3136
@RunWith(Parameterized.class)
public class TransitiveComponentDependenciesTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{"implementation"}, {"api"}});
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final String transitiveDependencyType;

  public TransitiveComponentDependenciesTest(String transitiveDependencyType) {
    this.transitiveDependencyType = transitiveDependencyType;
  }

  @Test
  public void testsComponentDependencies() throws IOException {
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        result = setupRunner().buildAndFail();
        assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
        String expectedErrorMsg =
            "error: ComponentProcessingStep was unable to process 'app.ComponentC' because"
                + " 'libraryA.ComponentA' could not be resolved."
                + "\n  "
                + "\n  Dependency trace:"
                + "\n      => element (CLASS): libraryB.ComponentB"
                + "\n      => annotation:"
                + " @dagger.Component(dependencies = libraryA.ComponentA.class)"
                + "\n      => annotation method: java.lang.Class<?>[] dependencies()"
                + "\n      => annotation value (ARRAY):"
                + " value 'libraryA.ComponentA.class' with expected type java.lang.Class<?>[]"
                + "\n      => annotation value (TYPE):"
                + " value 'libraryA.ComponentA' with expected type java.lang.Class<?>"
                + "\n      => type (ERROR annotation value type): libraryA.ComponentA";
        assertThat(result.getOutput()).contains(expectedErrorMsg);
        break;
      case "api":
        result = setupRunner().build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        break;
    }
  }

  private GradleRunner setupRunner() throws IOException {
    File projectDir = folder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile("include 'app'", "include 'libraryB'", "include 'libraryA'")
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
            "tasks.withType(JavaCompile) {",
            "    options.compilerArgs += '-Adagger.experimentalDaggerErrorMessages=ENABLED'",
            "}",
            "dependencies {",
            "  implementation project(':libraryB')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "ComponentC.java",
            "package app;",
            "",
            "import dagger.Component;",
            "import libraryB.ComponentB;",
            "",
            "@Component(dependencies = ComponentB.class)",
            "public interface ComponentC {",
            "  public abstract C getC();",
            "}")
        .addSrcFile(
            "C.java",
            "package app;",
            "",
            "import javax.inject.Inject;",
            "import libraryB.B;",
            "",
            "public class C {",
            "  @Inject C(B b) {}",
            "}");

    GradleModule.create(projectDir, "libraryB")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'java-library'",
            "}",
            "dependencies {",
            transitiveDependencyType + " project(':libraryA')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "ComponentB.java",
            "package libraryB;",
            "",
            "import dagger.Component;",
            "import libraryA.ComponentA;",
            "",
            "@Component(dependencies = ComponentA.class)",
            "public abstract class ComponentB {",
            "  public abstract B getB();",
            "}")
        .addSrcFile(
            "B.java",
            "package libraryB;",
            "",
            "import javax.inject.Inject;",
            "import libraryA.A;",
            "",
            "public class B {",
            "  @Inject B(A a) {}",
            "}");

    GradleModule.create(projectDir, "libraryA")
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
            "ComponentA.java",
            "package libraryA;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "public abstract class ComponentA {",
            "  public abstract A getA();",
            "}")
        .addSrcFile(
            "A.java",
            "package libraryA;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class A {",
            "  @Inject A() {}",
            "}")
        .addSrcFile(
            "AScope.java",
            "package libraryA;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "public @interface AScope {}");

    return GradleRunner.create().withArguments("--stacktrace", "build").withProjectDir(projectDir);
  }
}
