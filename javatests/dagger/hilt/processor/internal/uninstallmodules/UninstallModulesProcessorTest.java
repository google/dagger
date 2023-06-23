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

import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UninstallModulesProcessorTest {

  @Test
  public void testInvalidModuleNoInstallIn_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.MyTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import dagger.hilt.android.testing.UninstallModules;",
                "",
                "@UninstallModules(InvalidModule.class)",
                "@HiltAndroidTest",
                "public class MyTest {}"),
            HiltCompilerTests.javaSource(
                "test.InvalidModule",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.hilt.migration.DisableInstallInCheck;",
                "",
                "@DisableInstallInCheck",
                "@Module",
                "public class InvalidModule {}"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@UninstallModules should only include modules annotated with both @Module and "
                      + "@InstallIn, but found: [test.InvalidModule].");
            });
  }

  @Test
  public void testInvalidModuleNoModule_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.MyTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import dagger.hilt.android.testing.UninstallModules;",
                "",
                "@UninstallModules(InvalidModule.class)",
                "@HiltAndroidTest",
                "public class MyTest {}"),
            HiltCompilerTests.javaSource(
                "test.InvalidModule", "package test;", "", "public class InvalidModule {", "}"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@UninstallModules should only include modules annotated with both @Module and "
                      + "@InstallIn, but found: [test.InvalidModule].");
            });
  }

  @Test
  public void testInvalidTest_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.InvalidTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.UninstallModules;",
                "",
                "@UninstallModules(ValidModule.class)",
                "public class InvalidTest {}"),
            HiltCompilerTests.javaSource(
                "test.ValidModule",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.hilt.InstallIn;",
                "import dagger.hilt.components.SingletonComponent;",
                "",
                "@Module",
                "@InstallIn(SingletonComponent.class)",
                "public class ValidModule {}"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@UninstallModules should only be used on test classes annotated with"
                      + " @HiltAndroidTest, but found: test.InvalidTest");
            });
  }

  @Test
  public void testInvalidTestModule_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
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
                "}"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(bcorso): Consider unwrapping pkg-private modules before reporting the error.
              subject.hasErrorContaining(
                  "@UninstallModules should not contain test modules, but found: "
                      + "[test.MyTest.PkgPrivateInvalidModule, test.MyTest.PublicInvalidModule]");
            });
  }
}
