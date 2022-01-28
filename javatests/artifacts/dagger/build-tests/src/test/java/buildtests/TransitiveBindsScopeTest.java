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
public class TransitiveBindsScopeTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{ "implementation" }, { "api" }});
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final String transitiveDependencyType;

  public TransitiveBindsScopeTest(String transitiveDependencyType) {
    this.transitiveDependencyType = transitiveDependencyType;
  }

  @Test
  public void testScopeOnBindsMethod() throws IOException {
    BuildResult result;
    switch (transitiveDependencyType) {
      case "implementation":
        result = setupRunner().buildAndFail();
        assertThat(result.getOutput()).contains("Task :app:compileJava FAILED");
        assertThat(result.getOutput())
            .contains(
                "ComponentProcessingStep was unable to process 'app.MyComponent' because "
                    + "'library2.MyScope' could not be resolved."
                    + "\n  "
                    + "\n  Dependency trace:"
                    + "\n      => element (INTERFACE): library1.MyModule"
                    + "\n      => element (METHOD): bindObject(java.lang.String)"
                    + "\n      => annotation: @library2.MyScope");
        break;
      case "api":
        result = setupRunner().build();
        assertThat(result.task(":app:assemble").getOutcome()).isEqualTo(SUCCESS);
        assertThat(result.getOutput())
            .contains(
                "@Binds @library2.MyScope Object library1.MyModule.bindObject(String): SCOPED");
        break;
    }
  }

  private GradleRunner setupRunner() throws IOException {
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
            "    options.compilerArgs += '-Adagger.experimentalDaggerErrorMessages=ENABLED'",
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
            "import library1.MySubcomponent;",
            "",
            "@Component",
            "public interface MyComponent {",
            "  MySubcomponent subcomponent();",
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
        // Note: In order to repro the issue we place MyScope on a subcomponent so that it can be a
        // transitive dependency of the component. If MyScope was placed on directly on the
        // component, it would need to be a direct dependency of the component.
        .addSrcFile(
            "MySubcomponent.java",
            "package library1;",
            "",
            "import dagger.Subcomponent;",
            "import library2.MyScope;",
            "",
            "@MyScope",
            "@Subcomponent(modules = MyModule.class)",
            "public interface MySubcomponent {",
            "  Object object();",
            "}")
        .addSrcFile(
            "MyModule.java",
            "package library1;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import library2.MyScope;",
            "",
            "@Module",
            "public interface MyModule {",
            "  @MyScope",
            "  @Binds",
            "  Object bindObject(String string);",
            "",
            "  @Provides",
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
            "  implementation 'javax.inject:javax.inject:1'",
            "}")
        .addSrcFile(
            "MyScope.java",
            "package library2;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "public @interface MyScope {}");

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
            "    bindingGraph.bindings().stream()",
            "        .filter(binding -> binding.scope().isPresent())",
            "        .forEach(binding -> System.out.println(binding + \": SCOPED\"));",
            "    bindingGraph.bindings().stream()",
            "        .filter(binding -> !binding.scope().isPresent())",
            "        .forEach(binding -> System.out.println(binding + \": UNSCOPED\"));",
            "  }",
            "}");

    return GradleRunner.create()
        .withArguments("--stacktrace", "build")
        .withProjectDir(projectDir);
  }
}
