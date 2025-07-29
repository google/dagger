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

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingEnv.Backend;
import androidx.room.compiler.processing.util.CompilationResultSubject;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import dagger.hilt.processor.internal.BaseProcessingStep;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidEntryPointProcessorTest {
  @Test
  public void testAndroidEntryPoint() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {}");

    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            (CompilationResultSubject subject) -> {
              subject.hasErrorCount(0);
              subject.generatedSourceFileWithPath("test/Hilt_MyActivity.java");
            });
  }

  @Test
  public void missingBaseClass() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint",
            "public class MyActivity extends ComponentActivity { }");
    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            (CompilationResultSubject subject) -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining("Expected @AndroidEntryPoint to have a value.")
                  ;
            });
  }

  @Test
  public void generatedSuperclass() {
    Source annotation =
        HiltCompilerTests.javaSource(
            "test.GenerateAndroidActivity",
            "package test;",
            "",
            "@interface GenerateAndroidActivity { }");
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@GenerateAndroidActivity",
            "@AndroidEntryPoint(GeneratedActivity.class)",
            "public class MyActivity extends Hilt_MyActivity { }");
    HiltCompilerTests.hiltCompiler(annotation, testActivity)
        .withProcessingSteps(
            env ->
                new BaseProcessingStep(env) {
                  @Override
                  public void processEach(ClassName annotation, XElement element) {
                    TypeSpec typeSpec =
                        TypeSpec.classBuilder("GeneratedActivity")
                            .superclass(ClassName.get("androidx.activity", "ComponentActivity"))
                            .build();
                    processingEnv()
                        .getFiler()
                        .write(
                            JavaFile.builder(/* packageName= */ "test", typeSpec).build(),
                            Mode.Isolating);
                  }

                  @Override
                  protected ImmutableSet<ClassName> annotationClassNames() {
                    return ImmutableSet.of(ClassName.get("test", "GenerateAndroidActivity"));
                  }
                })
        .compile((CompilationResultSubject subject) -> subject.hasErrorCount(0));
  }

  @Test
  public void errorTypeSuperclass() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(NonExistingActivity.class)",
            "public class MyActivity extends Hilt_MyActivity { }");
    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            (CompilationResultSubject subject) -> {
              if (HiltCompilerTests.backend(subject) == XProcessingEnv.Backend.KSP) {
                subject
                    .hasErrorCount(1)
                    .hasErrorContaining(
                        "@AndroidEntryPoint, 'value' class is invalid or missing:"
                            + " @dagger.hilt.android.AndroidEntryPoint(NonExistingActivity)");
              } else {
                subject
                    .hasErrorCount(3)
                    .hasErrorContaining(
                        "@AndroidEntryPoint, 'value' class is invalid or missing:"
                            + " @dagger.hilt.android.AndroidEntryPoint(<error>)");
              }
            });
  }

  @Test
  public void incorrectSuperclass() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class MyActivity extends ComponentActivity { }");
    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            (CompilationResultSubject subject) -> {
              // TODO(b/288210593): Add this check back to KSP once this bug is fixed.
              if (HiltCompilerTests.backend(subject) == XProcessingEnv.Backend.KSP) {
                subject.hasErrorCount(0);
              } else {
                subject.hasErrorCount(1);
                subject
                    .hasErrorContaining(
                        "@AndroidEntryPoint class expected to extend Hilt_MyActivity. "
                            + "Found: ComponentActivity")
                    ;
              }
            });
  }

  @Test
  public void disableSuperclassValidation_activity() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint",
            "public class MyActivity extends ComponentActivity { }");
    HiltCompilerTests.hiltCompiler(testActivity)
        .withProcessorOptions(
            ImmutableMap.of(
                "dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void disableSuperclassValidation_application() {
    Source testApplication =
        HiltCompilerTests.javaSource(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp",
            "public class MyApp extends Application { }");
    HiltCompilerTests.hiltCompiler(testApplication)
        .withProcessorOptions(
            ImmutableMap.of(
                "dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void checkBaseActivityExtendsComponentActivity() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(Activity.class)",
            "public class MyActivity extends Hilt_MyActivity { }");
    HiltCompilerTests.hiltCompiler(testActivity).compile(subject -> {
        subject.compilationDidFail();
        subject.hasErrorContaining(
            "Activities annotated with @AndroidEntryPoint must be a subclass of "
                + "androidx.activity.ComponentActivity. (e.g. FragmentActivity, AppCompatActivity, "
                + "etc.)"); });
  }

  @Test
  public void checkBaseActivityWithTypeParameters() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.BaseActivity",
            "package test;",
            "",
            "import androidx.activity.ComponentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(ComponentActivity.class)",
            "public class BaseActivity<T> extends Hilt_BaseActivity {}");
    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            subject -> {
              subject.compilationDidFail();
              if (HiltCompilerTests.backend(subject) == Backend.JAVAC) {
                subject.hasErrorCount(2);
              } else {
                subject.hasErrorCount(1);
              }
              subject.hasErrorContaining(
                  "@AndroidEntryPoint-annotated classes cannot have type parameters.");
            });
  }

  @Test
  public void checkAndroidEntryPointOnApplicationRecommendsHiltAndroidApp() {
    Source testActivity =
        HiltCompilerTests.javaSource(
            "test.MyApplication",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(Application.class)",
            "public class MyApplication extends Hilt_MyApplication { }");
    HiltCompilerTests.hiltCompiler(testActivity)
        .compile(
            (CompilationResultSubject subject) -> {
              if (HiltCompilerTests.backend(subject) == XProcessingEnv.Backend.KSP) {
                subject.hasErrorCount(1);
              } else {
                // Javac has an extra error due to the missing symbol.
                subject.hasErrorCount(2);
                subject.hasErrorContaining(
                    "cannot find symbol\n      symbol: class Hilt_MyApplication");
              }
              subject.hasErrorContaining(
                  "@AndroidEntryPoint cannot be used on an Application. "
                      + "Use @HiltAndroidApp instead.");
            });
  }
}
