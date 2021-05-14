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

package dagger.hilt.android.processor.internal.androidentrypoint;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidEntryPointProcessorTest {

  @Test
  public void missingBaseClass() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint",
            "public class MyActivity extends ComponentActivity { }");
    Compilation compilation = compiler().compile(testActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Expected @AndroidEntryPoint to have a value.")
        ;
  }

  @Test
  public void incorrectSuperclass() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class MyActivity extends ComponentActivity { }");
    Compilation compilation = compiler().compile(testActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@AndroidEntryPoint class expected to extend Hilt_MyActivity. "
                + "Found: ComponentActivity")
        ;
  }

  @Test
  public void disableSuperclassValidation_activity() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint",
            "public class MyActivity extends ComponentActivity { }");
    Compilation compilation =
        compiler()
            .withOptions("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
            .compile(testActivity);
    assertThat(compilation).succeeded();
  }

  @Test
  public void disableSuperclassValidation_application() {
    JavaFileObject testApplication =
        JavaFileObjects.forSourceLines(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp",
            "public class MyApp extends Application { }");
    Compilation compilation =
        compiler()
            .withOptions("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
            .compile(testApplication);
    assertThat(compilation).succeeded();
  }

  @Test
  public void checkBaseActivityExtendsComponentActivity() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(Activity.class)",
            "public class MyActivity extends Hilt_MyActivity { }");
    Compilation compilation = compiler().compile(testActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Activities annotated with @AndroidEntryPoint must be a subclass of "
            + "androidx.activity.ComponentActivity. (e.g. FragmentActivity, AppCompatActivity, "
            + "etc.)");
  }

  @Test
  public void checkBaseActivityWithTypeParameters() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.BaseActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class BaseActivity<T> extends Hilt_BaseActivity {}");
    Compilation compilation = compiler().compile(testActivity);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation).hadErrorContaining(
        "cannot find symbol\n  symbol: class Hilt_BaseActivity");
    assertThat(compilation).hadErrorContaining(
        "@AndroidEntryPoint-annotated classes cannot have type parameters.");
  }

  @Test
  public void checkAndroidEntryPointOnApplicationRecommendsHiltAndroidApp() {
    JavaFileObject testActivity =
        JavaFileObjects.forSourceLines(
            "test.MyApplication",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(Application.class)",
            "public class MyApplication extends Hilt_MyApplication { }");
    Compilation compilation = compiler().compile(testActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@AndroidEntryPoint cannot be used on an Application. "
            + "Use @HiltAndroidApp instead.");
  }
}
