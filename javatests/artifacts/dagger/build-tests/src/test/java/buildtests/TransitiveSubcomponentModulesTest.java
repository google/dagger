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
public class TransitiveSubcomponentModulesTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{"implementation"}, {"api"}});
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final String transitiveDependencyType;

  public TransitiveSubcomponentModulesTest(String transitiveDependencyType) {
    this.transitiveDependencyType = transitiveDependencyType;
  }

  @Test
  public void testSubcomponentAnnotationWithTransitiveModule() throws IOException {
    GradleRunner runner =
        setupRunner(
            GradleFile.create(
                "MySubcomponent.java",
                "package library1;",
                "",
                "import dagger.Subcomponent;",
                "import library2.TransitiveModule;",
                "",
                "@Subcomponent(modules = TransitiveModule.class)",
                "public abstract class MySubcomponent {",
                "  public abstract int getInt();",
                "}"));
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        result = runner.buildAndFail();
        assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
        String expectedErrorMsg =
            "error: ComponentProcessingStep was unable to process 'app.MyComponent' because"
                + " 'library2.TransitiveModule' could not be resolved."
                + "\n  "
                + "\n  Dependency trace:"
                + "\n      => element (CLASS): library1.MySubcomponent"
                + "\n      => annotation:"
                + " @dagger.Subcomponent(modules = library2.TransitiveModule.class)"
                + "\n      => annotation method: java.lang.Class<?>[] modules()"
                + "\n      => annotation value (ARRAY):"
                + " value 'library2.TransitiveModule.class' with expected type java.lang.Class<?>[]"
                + "\n      => annotation value (TYPE):"
                + " value 'library2.TransitiveModule' with expected type java.lang.Class<?>"
                + "\n      => type (ERROR annotation value type): library2.TransitiveModule";
        assertThat(result.getOutput()).contains(expectedErrorMsg);
        break;
      case "api":
        result = runner.build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        break;
    }
  }

  @Test
  public void testSubcomponentAnnotationWithModuleIncludesTransitiveModuleDependencies()
      throws IOException {
    GradleRunner runner =
        setupRunner(
            GradleFile.create(
                "MySubcomponent.java",
                "package library1;",
                "",
                "import dagger.Subcomponent;",
                "",
                "@Subcomponent(modules = IncludesTransitiveModule.class)",
                "public abstract class MySubcomponent {",
                "  public abstract int getInt();",
                "}"));
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        result = runner.buildAndFail();
        assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
        String expectedErrorMsg =
            "error: ComponentProcessingStep was unable to process 'app.MyComponent' because"
                + " 'library2.TransitiveModule' could not be resolved."
                + "\n  "
                + "\n  Dependency trace:"
                + "\n      => element (INTERFACE): library1.IncludesTransitiveModule"
                + "\n      => annotation:"
                + " @dagger.Module(includes = library2.TransitiveModule.class)"
                + "\n      => annotation method: java.lang.Class<?>[] includes()"
                + "\n      => annotation value (ARRAY):"
                + " value 'library2.TransitiveModule.class' with expected type java.lang.Class<?>[]"
                + "\n      => annotation value (TYPE):"
                + " value 'library2.TransitiveModule' with expected type java.lang.Class<?>";
        assertThat(result.getOutput()).contains(expectedErrorMsg);
        break;
      case "api":
        result = runner.build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        break;
    }
  }

  private GradleRunner setupRunner(GradleFile subcomponent) throws IOException {
    File projectDir = folder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile("include 'app'", "include 'library1'", "include 'library2'")
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
            "  implementation project(':library1')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "MyComponent.java",
            "package app;",
            "",
            "import dagger.Component;",
            "import library1.MySubcomponent;",
            "",
            "@Component",
            "public interface MyComponent {",
            "  MySubcomponent mySubcomponent();",
            "}");

    GradleModule.create(projectDir, "library1")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'java-library'",
            "}",
            "dependencies {",
            transitiveDependencyType + " project(':library2')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "IncludesTransitiveModule.java",
            "package library1;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import library2.TransitiveModule;",
            "",
            "@Module(includes = TransitiveModule.class)",
            "public interface IncludesTransitiveModule {}")
        .addSrcFile(subcomponent);

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
            "TransitiveModule.java",
            "package library2;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public interface TransitiveModule {",
            "  @Provides",
            "  static int provideInt() {",
            "    return 0;",
            "  }",
            "}");

    return GradleRunner.create().withArguments("--stacktrace", "build").withProjectDir(projectDir);
  }
}
