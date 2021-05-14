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

package dagger.hilt.processor.internal.uninstallmodules;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UninstallModulesProcessorTest {

  @Test
  public void testInvalidModuleNoInstallIn_fails() {
    Compilation compilation =
        compiler()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.MyTest",
                    "package test;",
                    "",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "import dagger.hilt.android.testing.UninstallModules;",
                    "",
                    "@UninstallModules(InvalidModule.class)",
                    "@HiltAndroidTest",
                    "public class MyTest {}"),
                JavaFileObjects.forSourceLines(
                    "test.InvalidModule",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.hilt.migration.DisableInstallInCheck;",
                    "",
                    "@DisableInstallInCheck",
                    "@Module",
                    "public class InvalidModule {}"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@UninstallModules should only include modules annotated with both @Module and "
                + "@InstallIn, but found: [test.InvalidModule].");
  }

  @Test
  public void testInvalidModuleNoModule_fails() {
    Compilation compilation =
        compiler()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.MyTest",
                    "package test;",
                    "",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "import dagger.hilt.android.testing.UninstallModules;",
                    "",
                    "@UninstallModules(InvalidModule.class)",
                    "@HiltAndroidTest",
                    "public class MyTest {}"),
                JavaFileObjects.forSourceLines(
                    "test.InvalidModule",
                    "package test;",
                    "",
                    "public class InvalidModule {",
                    "}"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@UninstallModules should only include modules annotated with both @Module and "
                + "@InstallIn, but found: [test.InvalidModule].");
  }

  @Test
  public void testInvalidTest_fails() {
    Compilation compilation =
        compiler()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.InvalidTest",
                    "package test;",
                    "",
                    "import dagger.hilt.android.testing.UninstallModules;",
                    "",
                    "@UninstallModules(ValidModule.class)",
                    "public class InvalidTest {}"),
                JavaFileObjects.forSourceLines(
                    "test.ValidModule",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.hilt.InstallIn;",
                    "import dagger.hilt.components.SingletonComponent;",
                    "",
                    "@Module",
                    "@InstallIn(SingletonComponent.class)",
                    "public class ValidModule {}"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@UninstallModules should only be used on test classes annotated with @HiltAndroidTest,"
                + " but found: test.InvalidTest");
  }

  @Test
  public void testInvalidTestModule_fails() {
    Compilation compilation =
        compiler()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.MyTest",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.hilt.InstallIn;",
                    "import dagger.hilt.components.SingletonComponent;",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "import dagger.hilt.android.testing.UninstallModules;",
                    "",
                    "@UninstallModules({",
                    "    MyTest.PkgPrivateInvalidModule.class,",
                    "    MyTest.PublicInvalidModule.class,",
                    "})",
                    "@HiltAndroidTest",
                    "public class MyTest {",
                    "  @Module",
                    "  @InstallIn(SingletonComponent.class)",
                    "  interface PkgPrivateInvalidModule {}",
                    "",
                    "  @Module",
                    "  @InstallIn(SingletonComponent.class)",
                    "  public interface PublicInvalidModule {}",
                    "}"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    // TODO(bcorso): Consider unwrapping pkg-private modules before reporting the error.
    assertThat(compilation)
        .hadErrorContaining(
            "@UninstallModules should not contain test modules, but found: "
                + "[test.MyTest.PkgPrivateInvalidModule, test.MyTest.PublicInvalidModule]");
  }
}
