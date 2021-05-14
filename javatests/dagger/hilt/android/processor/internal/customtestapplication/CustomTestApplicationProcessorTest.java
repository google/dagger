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

package dagger.hilt.android.processor.internal.customtestapplication;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CustomTestApplicationProcessorTest {

  @Test
  public void validBaseClass_succeeds() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "",
                "@CustomTestApplication(Application.class)",
                "@HiltAndroidTest",
                "public class HiltTest {}"));

    assertThat(compilation).succeeded();
  }

  @Test
  public void incorrectBaseType_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.Foo",
                "package test;",
                "",
                "public class Foo {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(Foo.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication value should be an instance of android.app.Application. "
                + "Found: test.Foo");
  }

  @Test
  public void baseWithHiltAndroidApp_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.HiltAndroidApp;",
                "",
                "@HiltAndroidApp(Application.class)",
                "public class BaseApplication extends Hilt_BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication value cannot be annotated with @HiltAndroidApp. "
                + "Found: test.BaseApplication");
  }

  @Test
  public void superclassWithHiltAndroidApp_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.HiltAndroidApp;",
                "",
                "@HiltAndroidApp(Application.class)",
                "public class BaseApplication extends Hilt_BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication value cannot be annotated with @HiltAndroidApp. "
                + "Found: test.BaseApplication");
  }

  @Test
  public void withInjectField_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str;",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject fields. Found test.BaseApplication with @Inject fields [str]");
  }

  @Test
  public void withSuperclassInjectField_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str;",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject fields. Found test.BaseApplication with @Inject fields [str]");
  }

  @Test
  public void withInjectMethod_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str() { return null; }",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject methods. Found test.BaseApplication with @Inject methods [str()]");
  }

  @Test
  public void withSuperclassInjectMethod_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str() { return null; }",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject methods. Found test.BaseApplication with @Inject methods [str()]");
  }

  @Test
  public void withInjectConstructor_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject BaseApplication() {}",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject constructors. Found test.BaseApplication with @Inject constructors "
                + "[BaseApplication()]");
  }

  @Test
  public void withSuperclassInjectConstructor_fails() {
    Compilation compilation =
        compiler().compile(
            JavaFileObjects.forSourceLines(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject BaseApplication() {}",
                "}"),
            JavaFileObjects.forSourceLines(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            JavaFileObjects.forSourceLines(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@CustomTestApplication does not support application classes (or super classes) with "
                + "@Inject constructors. Found test.BaseApplication with @Inject constructors "
                + "[BaseApplication()]");
  }
}
