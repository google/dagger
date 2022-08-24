/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen;

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComponentRequirementFieldTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentRequirementFieldTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void bindsInstance() throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import java.util.List;",
            "",
            "@Component",
            "interface TestComponent {",
            "  int i();",
            "  List<String> list();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder i(int i);",
            "    @BindsInstance Builder list(List<String> list);",
            "    TestComponent build();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void instanceModuleMethod() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides int i() { return 0; }",
            "}");
    Source otherPackageModule =
        CompilerTests.javaSource(
            "other.OtherPackageModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public class OtherPackageModule {",
            "  @Provides long l() { return 0L; }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.OtherPackageModule;",
            "",
            "@Component(modules = {ParentModule.class, OtherPackageModule.class})",
            "interface TestComponent {",
            "  int i();",
            "  long l();",
            "}");
    CompilerTests.daggerCompiler(module, otherPackageModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void componentInstances() throws Exception {
    Source dependency =
        CompilerTests.javaSource(
            "test.Dep",
            "package test;",
            "",
            "interface Dep {",
            "  String string();",
            "  Object object();",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = Dep.class)",
            "interface TestComponent {",
            "  TestComponent self();",
            "  TestSubcomponent subcomponent();",
            "",
            "  Dep dep();",
            "  String methodOnDep();",
            "  Object otherMethodOnDep();",
            "}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface TestSubcomponent {",
            "  TestComponent parent();",
            "  Dep depFromSubcomponent();",
            "}");

    CompilerTests.daggerCompiler(dependency, component, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void componentRequirementNeededInFactoryCreationOfSubcomponent() throws Exception {
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            // intentionally non-static. this needs to require the module when the subcompnent
            // adds to the Set binding
            "  Object reliesOnMultibinding(Set<Object> set) { return set; }",
            "",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface TestComponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "  TestSubcomponent subcomponent();",
            "}");

    Source subcomponent =
        CompilerTests.javaSource(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface TestSubcomponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "}");

    CompilerTests.daggerCompiler(parentModule, childModule, component, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
