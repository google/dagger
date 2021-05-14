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
public class ActivityGeneratorTest {

  @Test
  public void generate_componentActivity() {
    JavaFileObject myActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {",
            "}");
    Compilation compilation = compiler().compile(myActivity);
    assertThat(compilation).succeeded();
  }

  @Test
  public void generate_baseHiltComponentActivity() {
    JavaFileObject baseActivity =
        JavaFileObjects.forSourceLines(
            "test.BaseActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class BaseActivity extends Hilt_BaseActivity {",
            "}");
    JavaFileObject myActivity =
        JavaFileObjects.forSourceLines(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {",
            "}");
    Compilation compilation = compiler().compile(baseActivity, myActivity);
    assertThat(compilation).succeeded();
  }
}
