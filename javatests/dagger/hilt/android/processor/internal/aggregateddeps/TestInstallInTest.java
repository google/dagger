/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.aggregateddeps;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestInstallInTest {

  @Test
  public void testMissingValues() {
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@Module",
            "@TestInstallIn",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.hilt.testing.TestInstallIn is missing default values for elements "
                + "components,replaces")
        .inFile(testInstallInModule)
        .onLine(7);
  }

  @Test
  public void testEmptyComponentValues() {
    JavaFileObject installInModule =
        JavaFileObjects.forSourceLines(
            "test.InstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@Module",
            "@InstallIn(SingletonComponent.class)",
            "interface InstallInModule {}");
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@Module",
            "@TestInstallIn(components = {}, replaces = InstallInModule.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(installInModule, testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    // TODO(bcorso): Add inFile().onLine() whenever we've fixed Processors.getAnnotationClassValues
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn, 'components' class is invalid or missing: "
                + "@dagger.hilt.testing.TestInstallIn("
                + "components={}, replaces={test.InstallInModule.class})");
  }

  @Test
  public void testEmptyReplacesValues() {
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.testing.TestInstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@Module",
            "@TestInstallIn(components = SingletonComponent.class, replaces = {})",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    // TODO(bcorso): Add inFile().onLine() whenever we've fixed Processors.getAnnotationClassValues
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn, 'replaces' class is invalid or missing: "
                + "@dagger.hilt.testing.TestInstallIn("
                + "components={dagger.hilt.components.SingletonComponent.class}, replaces={})");
  }

  @Test
  public void testMissingModuleAnnotation() {
    JavaFileObject installInModule =
        JavaFileObjects.forSourceLines(
            "test.InstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@Module",
            "@InstallIn(SingletonComponent.class)",
            "interface InstallInModule {}");
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            "    replaces = InstallInModule.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(installInModule, testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn-annotated classes must also be annotated with @Module or @EntryPoint: "
                + "test.TestInstallInModule")
        .inFile(testInstallInModule)
        .onLine(10);
  }

  @Test
  public void testInvalidUsageOnEntryPoint() {
    JavaFileObject installInModule =
        JavaFileObjects.forSourceLines(
            "test.InstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@Module",
            "@InstallIn(SingletonComponent.class)",
            "interface InstallInModule {}");
    JavaFileObject testInstallInEntryPoint =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInEntryPoint",
            "package test;",
            "",
            "import dagger.hilt.EntryPoint;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@EntryPoint",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            "    replaces = InstallInModule.class)",
            "interface TestInstallInEntryPoint {}");
    Compilation compilation = compiler().compile(installInModule, testInstallInEntryPoint);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("@TestInstallIn can only be used with modules")
        .inFile(testInstallInEntryPoint)
        .onLine(11);
  }

  @Test
  public void testInvalidReplaceModules() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines("test.Foo", "package test;", "", "class Foo {}");
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@Module",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            "    replaces = Foo.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(foo, testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn#replaces() can only contain @InstallIn modules, but found: [test.Foo]")
        .inFile(testInstallInModule)
        .onLine(11);
  }

  @Test
  public void testInternalDaggerReplaceModules() {
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@Module",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            "    replaces = dagger.hilt.android.internal.modules.ApplicationContextModule.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn#replaces() cannot contain internal Hilt modules, but found: "
                + "[dagger.hilt.android.internal.modules.ApplicationContextModule]")
        .inFile(testInstallInModule)
        .onLine(11);
  }

  @Test
  public void testHiltWrapperDaggerReplaceModules() {
    JavaFileObject testInstallInModule =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "import"
                + " dagger.hilt.android.processor.internal.aggregateddeps.HiltWrapper_InstallInModule;",
            "",
            "@Module",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            // Note: this module is built in a separate library since AggregatedDepsProcessor can't
            // handle modules generated in the same round.
            "    replaces = HiltWrapper_InstallInModule.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(testInstallInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn#replaces() cannot contain Hilt generated public wrapper modules, "
                + "but found: [dagger.hilt.android.processor.internal.aggregateddeps."
                + "HiltWrapper_InstallInModule]")
        .inFile(testInstallInModule)
        .onLine(12);
  }

  @Test
  public void testCannotReplaceLocalInstallInModule() {
    JavaFileObject test =
        JavaFileObjects.forSourceLines(
            "test.MyTest",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "",
            "@HiltAndroidTest",
            "public class MyTest {",
            "  @Module",
            "  @InstallIn(SingletonComponent.class)",
            "  interface LocalInstallInModule {}",
            "}");
    JavaFileObject testInstallIn =
        JavaFileObjects.forSourceLines(
            "test.TestInstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "",
            "@Module",
            "@TestInstallIn(",
            "    components = SingletonComponent.class,",
            "    replaces = MyTest.LocalInstallInModule.class)",
            "interface TestInstallInModule {}");
    Compilation compilation = compiler().compile(test, testInstallIn);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "TestInstallIn#replaces() cannot replace test specific @InstallIn modules, but found: "
                + "[test.MyTest.LocalInstallInModule].")
        .inFile(testInstallIn)
        .onLine(11);
  }

  @Test
  public void testThatTestInstallInCannotOriginateFromTest() {
    JavaFileObject installInModule =
        JavaFileObjects.forSourceLines(
            "test.InstallInModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@Module",
            "@InstallIn(SingletonComponent.class)",
            "interface InstallInModule {}");
    JavaFileObject test =
        JavaFileObjects.forSourceLines(
            "test.MyTest",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.testing.TestInstallIn;",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "",
            "@HiltAndroidTest",
            "public class MyTest {",
            "  @Module",
            "  @TestInstallIn(",
            "      components = SingletonComponent.class,",
            "      replaces = InstallInModule.class)",
            "  interface TestInstallInModule {}",
            "}");
    Compilation compilation = compiler().compile(test, installInModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@TestInstallIn modules cannot be nested in (or originate from) a "
                + "@HiltAndroidTest-annotated class:  test.MyTest")
        .inFile(test)
        .onLine(14);
  }
}
