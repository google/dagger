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
public class TransitiveQualifierTest {
  @Parameters(name = "transitiveDependencyType = {0}, strictSuperficialValidationMode = {1}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          { "implementation", "ENABLED" },
          { "implementation", "DISABLED" },
          { "api", "ENABLED" },
          { "api", "DISABLED" }
        });
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final String transitiveDependencyType;
  private final String strictSuperficialValidationMode;

  public TransitiveQualifierTest(
      String transitiveDependencyType, String strictSuperficialValidationMode) {
    this.transitiveDependencyType = transitiveDependencyType;
    this.strictSuperficialValidationMode = strictSuperficialValidationMode;
  }

  @Test
  public void testQualifierOnInjectConstructorParameter() throws IOException {
    GradleRunner runner =
        setupRunnerWith(
            GradleFile.create(
                "QualifierUsage.java",
                "package library1;",
                "",
                "import javax.inject.Inject;",
                "import library2.MyQualifier;",
                "",
                "public class QualifierUsage {",
                "  @Inject QualifierUsage(@MyQualifier int i) {}",
                "}"));
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        switch (strictSuperficialValidationMode) {
          case "ENABLED":
            result = runner.buildAndFail();
            assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
            // TODO(bcorso): Give more context about what couldn't be resolved once we've fixed the
            // issue described in https://github.com/google/dagger/issues/2208.
            assertThat(result.getOutput())
                .contains(
                    "ComponentProcessingStep was unable to process 'app.MyComponent' because "
                        + "'library2.MyQualifier' could not be resolved."
                        + "\n  "
                        + "\n  Dependency trace:"
                        + "\n      => element (INTERFACE): library1.MyModule"
                        + "\n      => element (METHOD): provideInt()"
                        + "\n      => annotation: @library2.MyQualifier");
            break;
          case "DISABLED":
            // When strict mode is disabled we fall back to the old behavior where the qualifier is
            // missing and we do not throw an exception.
            result = runner.build();
            assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
            assertThat(result.getOutput()).contains("REQUEST: java.lang.Integer");
            break;
          default: throw new AssertionError("Unexpected mode: " + strictSuperficialValidationMode);
        }
        break;
      case "api":
        result = runner.build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        assertThat(result.getOutput()).contains("REQUEST: @library2.MyQualifier java.lang.Integer");
        break;
    }
  }

  @Test
  public void testQualifierOnInjectField() throws IOException {
    GradleRunner runner =
        setupRunnerWith(
            GradleFile.create(
                "QualifierUsage.java",
                "package library1;",
                "",
                "import javax.inject.Inject;",
                "import library2.MyQualifier;",
                "",
                "public class QualifierUsage {",
                "  @Inject @MyQualifier int i;",
                "",
                "  @Inject QualifierUsage() {}",
                "}"));
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        switch (strictSuperficialValidationMode) {
          case "ENABLED":
            result = runner.buildAndFail();
            assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
            // TODO(bcorso): Give more context about what couldn't be resolved once we've fixed the
            // issue described in https://github.com/google/dagger/issues/2208.
            assertThat(result.getOutput())
                .contains(
                    "ComponentProcessingStep was unable to process 'app.MyComponent' because "
                        + "'library2.MyQualifier' could not be resolved."
                        + "\n  "
                        + "\n  Dependency trace:"
                        + "\n      => element (INTERFACE): library1.MyModule"
                        + "\n      => element (METHOD): provideInt()"
                        + "\n      => annotation: @library2.MyQualifier");
            break;
          case "DISABLED":
            // When strict mode is disabled we fall back to the old behavior where the qualifier is
            // missing and we do not throw an exception.
            result = runner.build();
            assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
            assertThat(result.getOutput()).contains("REQUEST: java.lang.Integer");
            break;
          default: throw new AssertionError("Unexpected mode: " + strictSuperficialValidationMode);
        }
        break;
      case "api":
        result = runner.build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        assertThat(result.getOutput()).contains("REQUEST: @library2.MyQualifier java.lang.Integer");
        break;
    }
  }

  @Test
  public void testQualifierOnInjectMethodParameter() throws IOException {
    GradleRunner runner =
        setupRunnerWith(
            GradleFile.create(
                "QualifierUsage.java",
                "package library1;",
                "",
                "import javax.inject.Inject;",
                "import library2.MyQualifier;",
                "",
                "public class QualifierUsage {",
                "  @Inject QualifierUsage() {}",
                "",
                "  @Inject void injectMethod(@MyQualifier int i) {}",
                "}"));
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        switch (strictSuperficialValidationMode) {
          case "ENABLED":
            result = runner.buildAndFail();
            assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
            // TODO(bcorso): Give more context about what couldn't be resolved once we've fixed the
            // issue described in https://github.com/google/dagger/issues/2208.
            assertThat(result.getOutput())
                .contains(
                    "ComponentProcessingStep was unable to process 'app.MyComponent' because "
                        + "'library2.MyQualifier' could not be resolved."
                        + "\n  "
                        + "\n  Dependency trace:"
                        + "\n      => element (INTERFACE): library1.MyModule"
                        + "\n      => element (METHOD): provideInt()"
                        + "\n      => annotation: @library2.MyQualifier");
            break;
          case "DISABLED":
            // When strict mode is disabled we fall back to the old behavior where the qualifier is
            // missing and we do not throw an exception.
            result = runner.build();
            assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
            assertThat(result.getOutput()).contains("REQUEST: java.lang.Integer");
            break;
          default: throw new AssertionError("Unexpected mode: " + strictSuperficialValidationMode);
        }
        break;
      case "api":
        result = runner.build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        assertThat(result.getOutput()).contains("REQUEST: @library2.MyQualifier java.lang.Integer");
        break;
    }
  }

  private GradleRunner setupRunnerWith(GradleFile qualifierUsage) throws IOException {
    File projectDir = folder.getRoot();
    GradleModule.create(projectDir)
        .addSettingsFile(
            "include 'app'",
            "include 'library1'",
            "include 'library2'",
            "include 'spi-plugin'")
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
            String.format(
                "    options.compilerArgs += '-Adagger.strictSuperficialValidation=%s'",
                strictSuperficialValidationMode),
            "}",
            "dependencies {",
            "  implementation project(':library1')",
            "  annotationProcessor project(':spi-plugin')",
            "  implementation \"com.google.dagger:dagger:$dagger_version\"",
            "  annotationProcessor \"com.google.dagger:dagger-compiler:$dagger_version\"",
            "}")
        .addSrcFile(
            "MyComponent.java",
            "package app;",
            "",
            "import dagger.Component;",
            "import library1.MyModule;",
            "import library1.QualifierUsage;",
            "",
            "@Component(modules = MyModule.class)",
            "public interface MyComponent {",
            "  QualifierUsage qualifierUsage();",
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
            "MyModule.java",
            "package library1;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import library2.MyQualifier;",
            "",
            "@Module",
            "public interface MyModule {",
            "  @Provides",
            "  @MyQualifier",
            "  static int provideInt() {",
            "    return 0;",
            "  }",
            "}")
        .addSrcFile(qualifierUsage);

    GradleModule.create(projectDir, "library2")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "  id 'java-library'",
            "}",
            "dependencies {",
            "  implementation 'javax.inject:javax.inject:1'",
            "}")
        .addSrcFile(
            "MyQualifier.java",
            "package library2;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "public @interface MyQualifier {}");

    // This plugin is used to print output about bindings that we can assert on in tests.
    GradleModule.create(projectDir, "spi-plugin")
        .addBuildFile(
            "plugins {",
            "  id 'java'",
            "}",
            "dependencies {",
            "  implementation \"com.google.dagger:dagger-spi:$dagger_version\"",
            "  implementation 'com.google.auto.service:auto-service-annotations:1.0.1'",
            "  annotationProcessor 'com.google.auto.service:auto-service:1.0.1'",
            "}")
        .addSrcFile(
            "TestBindingGraphPlugin.java",
            "package spiplugin;",
            "",
            "import com.google.auto.service.AutoService;",
            "import dagger.model.BindingGraph;",
            "import dagger.model.BindingGraph.DependencyEdge;",
            "import dagger.model.DependencyRequest;",
            "import dagger.spi.BindingGraphPlugin;",
            "import dagger.spi.DiagnosticReporter;",
            "",
            "@AutoService(BindingGraphPlugin.class)",
            "public class TestBindingGraphPlugin implements BindingGraphPlugin {",
            "  @Override",
            "  public void visitGraph(",
            "      BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {",
            "    bindingGraph.dependencyEdges().stream()",
            "        .map(DependencyEdge::dependencyRequest)",
            "        .map(DependencyRequest::key)",
            "        .forEach(key -> System.out.println(\"REQUEST: \" + key));",
            "  }",
            "}");

    return GradleRunner.create()
        .withArguments("--stacktrace", "build")
        .withProjectDir(projectDir);
  }
}
