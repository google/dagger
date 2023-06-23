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

import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CustomTestApplicationProcessorTest {

  @Test
  public void validBaseClass_succeeds() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "",
                "@CustomTestApplication(Application.class)",
                "@HiltAndroidTest",
                "public class HiltTest {}"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void incorrectBaseType_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource("test.Foo", "package test;", "", "public class Foo {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(Foo.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication value should be an instance of android.app.Application. "
                      + "Found: test.Foo");
            });
  }

  @Test
  public void baseWithHiltAndroidApp_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.HiltAndroidApp;",
                "",
                "@HiltAndroidApp(Application.class)",
                "public class BaseApplication extends Hilt_BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication value cannot be annotated with @HiltAndroidApp. "
                      + "Found: test.BaseApplication");
            });
  }

  @Test
  public void superclassWithHiltAndroidApp_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import dagger.hilt.android.HiltAndroidApp;",
                "",
                "@HiltAndroidApp(Application.class)",
                "public class BaseApplication extends Hilt_BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication value cannot be annotated with @HiltAndroidApp. "
                      + "Found: test.BaseApplication");
            });
  }

  @Test
  public void withInjectField_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str;",
                "}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject fields. Found test.BaseApplication with @Inject fields"
                      + " [str]");
            });
  }

  @Test
  public void withSuperclassInjectField_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str;",
                "}"),
            HiltCompilerTests.javaSource(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject fields. Found test.BaseApplication with @Inject fields"
                      + " [str]");
            });
  }

  @Test
  public void withInjectMethod_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str() { return null; }",
                "}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject methods. Found test.BaseApplication with @Inject methods"
                      + " [str()]");
            });
  }

  @Test
  public void withSuperclassInjectMethod_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject String str() { return null; }",
                "}"),
            HiltCompilerTests.javaSource(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject methods. Found test.BaseApplication with @Inject methods"
                      + " [str()]");
            });
  }

  @Test
  public void withInjectConstructor_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject BaseApplication() {}",
                "}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(BaseApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject constructors. Found test.BaseApplication with @Inject"
                      + " constructors [BaseApplication()]");
            });
  }

  @Test
  public void withSuperclassInjectConstructor_fails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "test.BaseApplication",
                "package test;",
                "",
                "import android.app.Application;",
                "import javax.inject.Inject;",
                "",
                "public class BaseApplication extends Application {",
                "  @Inject BaseApplication() {}",
                "}"),
            HiltCompilerTests.javaSource(
                "test.ParentApplication",
                "package test;",
                "",
                "public class ParentApplication extends BaseApplication {}"),
            HiltCompilerTests.javaSource(
                "test.HiltTest",
                "package test;",
                "",
                "import dagger.hilt.android.testing.CustomTestApplication;",
                "",
                "@CustomTestApplication(ParentApplication.class)",
                "public class HiltTest {}"))
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "@CustomTestApplication does not support application classes (or super classes)"
                      + " with @Inject constructors. Found test.BaseApplication with @Inject"
                      + " constructors [BaseApplication()]");
            });
  }
}
