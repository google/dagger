/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that errors are reported correctly when a {@code @Binds} method's delegate (the type of its
 * parameter) is missing.
 */
@RunWith(JUnit4.class)
public class BindsMissingDelegateValidationTest {
  @Test
  public void bindsMissingDelegate() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Object object();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.C.NotBound cannot be provided")
        .inFile(component)
        .onLineContaining("interface C");
  }

  @Test
  public void bindsMissingDelegate_duplicateBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Object object();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "    @Provides static Object provideObject() { return new Object(); }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    // Some javacs report only the first error for each source line.
    // Assert that one of the expected errors is reported.
    assertThat(compilation)
        .hadErrorContainingMatch(
            "\\Qtest.C.NotBound cannot be provided\\E|"
                + message(
                    "\\Qjava.lang.Object is bound multiple times:",
                    "    @Binds Object test.C.TestModule.bindObject(test.C.NotBound)",
                    "    @Provides Object test.C.TestModule.provideObject()\\E"))
        .inFile(component)
        .onLineContaining("interface C");
  }

  @Test
  public void bindsMissingDelegate_setBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Set<Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoSet abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.C.NotBound cannot be provided")
        .inFile(component)
        .onLineContaining("interface C");
  }

  @Test
  public void bindsMissingDelegate_mapBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Map<String, Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoMap @StringKey(\"key\")",
            "    abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.C.NotBound cannot be provided")
        .inFile(component)
        .onLineContaining("interface C");
  }

  @Test
  public void bindsMissingDelegate_mapBinding_sameKey() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Map<String, Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoMap @StringKey(\"key\")",
            "    abstract Object bindObject(NotBound notBound);",
            "",
            "    @Provides @IntoMap @StringKey(\"key\")",
            "    static Object provideObject() { return new Object(); }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    // Some javacs report only the first error for each source line.
    assertThat(compilation)
        .hadErrorContainingMatch(
            "\\Qtest.C.NotBound cannot be provided\\E|"
                + "\\Qsame map key is bound more than once\\E")
        .inFile(component)
        .onLineContaining("interface C");
  }
}
