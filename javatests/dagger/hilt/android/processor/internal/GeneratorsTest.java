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

package dagger.hilt.android.processor.internal;

import androidx.room.compiler.processing.XProcessingEnv.Backend;
import androidx.room.compiler.processing.util.Source;
import com.google.common.base.Joiner;
import com.google.common.truth.StringSubject;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GeneratorsTest {
  private static final Joiner JOINER = Joiner.on("\n");

  @Test
  public void copyConstructorParametersCopiesExternalNullables() {
    Source baseActivity =
        HiltCompilerTests.javaSource(
            "test.BaseActivity",
            "package test;",
            "",
            "import androidx.fragment.app.FragmentActivity;",
            "",
            "public abstract class BaseActivity extends FragmentActivity {",
            "  protected BaseActivity(",
            "      @androidx.annotation.Nullable String supportNullable,",
            "      @androidx.annotation.Nullable String androidxNullable,",
            "      @javax.annotation.Nullable String javaxNullable) { }",
            "}");
    Source myActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {",
            "  public MyActivity(",
            "      String supportNullable,",
            "      String androidxNullable,",
            "      String javaxNullable) {",
            "    super(supportNullable, androidxNullable, javaxNullable);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(baseActivity, myActivity)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyActivity.java");
              stringSubject.contains("package test;");
              stringSubject.contains("import androidx.annotation.Nullable;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator\")",
                      "abstract class Hilt_MyActivity extends BaseActivity implements "
                          + "GeneratedComponentManagerHolder {"));
              stringSubject.contains(
                  JOINER.join(
                      "  Hilt_MyActivity(@Nullable String supportNullable,"
                      + " @Nullable String androidxNullable,",
                      "      @javax.annotation.Nullable String javaxNullable) {",
                      "    super(supportNullable, androidxNullable, javaxNullable);",
                      "    _initHiltInternal();",
                      "  }"));
            });
  }

  @Test
  public void copyConstructorParametersConvertsAndroidInternalNullableToExternal() {
    // Relies on View(Context, AttributeSet), which has android-internal
    // @android.annotation.Nullable on AttributeSet.
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(View.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(Context context, AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(myView)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyView.java");
              stringSubject.contains("import androidx.annotation.Nullable;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ViewGenerator\")",
                      "abstract class Hilt_MyView extends View implements"
                          + " GeneratedComponentManagerHolder {"));
              // TODO(kuanyingchou): Remove the condition once
              //  https://github.com/google/ksp/issues/1459 is fixed
              if (HiltCompilerTests.backend(subject) == Backend.KSP) {
                stringSubject.contains(
                    JOINER.join(
                        "  Hilt_MyView(Context p0, @Nullable AttributeSet p1) {",
                        "    super(p0, p1);",
                        "    if(!isInEditMode()) {",
                        "      inject();",
                        "    }",
                        "  }"));
              } else {
                stringSubject.contains(
                    JOINER.join(
                        "  Hilt_MyView(Context context, @Nullable AttributeSet attrs) {",
                        "    super(context, attrs);",
                        "    if(!isInEditMode()) {",
                        "      inject();",
                        "    }",
                        "  }"));
              }
            });
  }

  // This is a regression test for b/382104423
  @Test
  public void typeUseNullableCopiedFromSuperConstructor() {
    Source baseView =
        HiltCompilerTests.javaSource(
            "test.BaseView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import org.jspecify.annotations.Nullable;",
            "",
            "public class BaseView extends View {",
            "  public BaseView(Context context, @Nullable AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "import org.jspecify.annotations.Nullable;",
            "",
            "@AndroidEntryPoint(BaseView.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(Context context, @Nullable AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(baseView, myView)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyView.java");
              stringSubject.contains("org.jspecify.annotations.Nullable");
            });
  }

  @Test
  public void hybridTypeUseAndDeclarationNullableNotDuplicated() {
    Source hybridNullable =
        HiltCompilerTests.javaSource(
            "test.Nullable",
            "package test;",
            "",
            "import static java.lang.annotation.ElementType.PARAMETER;",
            "import static java.lang.annotation.ElementType.TYPE_USE;",
            "",
            "import java.lang.annotation.Target;",
            "",
            "@Target({TYPE_USE, PARAMETER})",
            "public @interface Nullable {}");
    Source baseView =
        HiltCompilerTests.javaSource(
            "test.BaseView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "",
            "public class BaseView extends View {",
            "  public BaseView(Context context, @Nullable AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseView.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(Context context, @Nullable AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(hybridNullable, baseView, myView)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyView.java");
              stringSubject.contains("@Nullable");
            });
  }

  // This is a regression test for https://github.com/google/dagger/issues/3296
  @Test
  public void isRestrictedApiConstructorWithPrimitiveParameterTest() {
    Source baseView =
        HiltCompilerTests.javaSource(
            "test.BaseView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "",
            "public class BaseView extends View {",
            "  public BaseView(int i, int j, Context context, AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseView.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(int i, int j, Context context, AttributeSet attrs) {",
            "    super(i, j, context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(baseView, myView).compile(subject -> subject.hasErrorCount(0));
  }

  // This is a regression test for https://github.com/google/dagger/issues/3296
  @Test
  public void isRestrictedApiConstructorWithArrayParameterTest() {
    Source baseView =
        HiltCompilerTests.javaSource(
            "test.BaseView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "",
            "public class BaseView extends View {",
            "  public BaseView(String[] strs, int i, Context context, AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseView.class)",
            "public class MyView extends Hilt_MyView {",
            "  public MyView(String[] strs, int i, Context context, AttributeSet attrs) {",
            "    super(strs, i, context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(baseView, myView).compile(subject -> subject.hasErrorCount(0));
  }

  // This is a regression test for https://github.com/google/dagger/issues/3296
  @Test
  public void isRestrictedApiConstructorWithTypeParameterTest() {
    Source baseView =
        HiltCompilerTests.javaSource(
            "test.BaseView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "",
            "public class BaseView<T> extends View {",
            "  public BaseView(T t, int i, Context context, AttributeSet attrs) {",
            "    super(context, attrs);",
            "  }",
            "}");
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import android.view.View;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@AndroidEntryPoint(BaseView.class)",
            "public class MyView extends Hilt_MyView<String> {",
            "  public MyView(String str, int i, Context context, AttributeSet attrs) {",
            "    super(str, i, context, attrs);",
            "  }",
            "}");
    HiltCompilerTests.hiltCompiler(baseView, myView).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void copyTargetApiAnnotationActivity() {
    Source myActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.FragmentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(FragmentActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {}");
    HiltCompilerTests.hiltCompiler(myActivity)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyActivity.java");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyActivity extends FragmentActivity"
                          + " implements GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copyTargetApiAnnotationOverView() {
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.widget.LinearLayout;",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(LinearLayout.class)",
            "public class MyView extends Hilt_MyView {",
            " public MyView(Context context, AttributeSet attributeSet){",
            "   super(context, attributeSet);",
            " }",
            "}");
    HiltCompilerTests.hiltCompiler(myView)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyView.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ViewGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyView extends LinearLayout implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copyTargetApiAnnotationApplication() {
    Source myApplication =
        HiltCompilerTests.javaSource(
            "test.MyApplication",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@TargetApi(24)",
            "@HiltAndroidApp(Application.class)",
            "public class MyApplication extends Hilt_MyApplication {}");
    HiltCompilerTests.hiltCompiler(myApplication)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyApplication.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint."
                          + "ApplicationGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyApplication extends Application implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copyTargetApiAnnotationFragment() {
    Source myApplication =
        HiltCompilerTests.javaSource(
            "test.MyFragment",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.Fragment;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(Fragment.class)",
            "public class MyFragment extends Hilt_MyFragment {}");
    HiltCompilerTests.hiltCompiler(myApplication)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyFragment.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.FragmentGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyFragment extends Fragment implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copyTargetApiBroadcastRecieverGenerator() {
    Source myBroadcastReceiver =
        HiltCompilerTests.javaSource(
            "test.MyBroadcastReceiver",
            "package test;",
            "",
            "import android.content.BroadcastReceiver;",
            "import android.annotation.TargetApi;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(BroadcastReceiver.class)",
            "public class MyBroadcastReceiver extends Hilt_MyBroadcastReceiver {}");
    HiltCompilerTests.hiltCompiler(myBroadcastReceiver)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyBroadcastReceiver.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.BroadcastReceiverGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyBroadcastReceiver extends BroadcastReceiver {"));
            });
  }

  @Test
  public void copyTargetApiServiceGenerator() {
    Source myService =
        HiltCompilerTests.javaSource(
            "test.MyService",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.content.Intent;",
            "import android.app.Service;",
            "import android.os.IBinder;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@TargetApi(24)",
            "@AndroidEntryPoint(Service.class)",
            "public class MyService extends Hilt_MyService {",
            "   @Override",
            "   public IBinder onBind(Intent intent){",
            "     return null;",
            "   }",
            "}");
    HiltCompilerTests.hiltCompiler(myService)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyService.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ServiceGenerator\")",
                      "@TargetApi(24)",
                      "abstract class Hilt_MyService extends Service implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copySuppressWarningsAnnotationActivity_annotationCopied() {
    Source myActivity =
        HiltCompilerTests.javaSource(
            "test.MyActivity",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.FragmentActivity;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@SuppressWarnings(\"deprecation\")",
            "@AndroidEntryPoint(FragmentActivity.class)",
            "public class MyActivity extends Hilt_MyActivity {}");
    HiltCompilerTests.hiltCompiler(myActivity)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyActivity.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ActivityGenerator\")",
                      "@SuppressWarnings(\"deprecation\")",
                      "abstract class Hilt_MyActivity extends FragmentActivity "
                          + "implements GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copySuppressWarningsAnnotation_onView_annotationCopied() {
    Source myView =
        HiltCompilerTests.javaSource(
            "test.MyView",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.widget.LinearLayout;",
            "import android.content.Context;",
            "import android.util.AttributeSet;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@SuppressWarnings(\"deprecation\")",
            "@AndroidEntryPoint(LinearLayout.class)",
            "public class MyView extends Hilt_MyView {",
            " public MyView(Context context, AttributeSet attributeSet){",
            "   super(context, attributeSet);",
            " }",
            "}");
    HiltCompilerTests.hiltCompiler(myView)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyView.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ViewGenerator\")",
                      "@SuppressWarnings(\"deprecation\")",
                      "abstract class Hilt_MyView extends LinearLayout implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copySuppressWarningsAnnotation_onApplication_annotationCopied() {
    Source myApplication =
        HiltCompilerTests.javaSource(
            "test.MyApplication",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@SuppressWarnings(\"deprecation\")",
            "@HiltAndroidApp(Application.class)",
            "public class MyApplication extends Hilt_MyApplication {}");
    HiltCompilerTests.hiltCompiler(myApplication)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyApplication.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.ApplicationGenerator\")",
                      "@SuppressWarnings(\"deprecation\")",
                      "abstract class Hilt_MyApplication extends Application implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copySuppressWarningsAnnotation_onFragment_annotationCopied() {
    Source myApplication =
        HiltCompilerTests.javaSource(
            "test.MyFragment",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import androidx.fragment.app.Fragment;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@SuppressWarnings(\"rawtypes\")",
            "@AndroidEntryPoint(Fragment.class)",
            "public class MyFragment extends Hilt_MyFragment {}");
    HiltCompilerTests.hiltCompiler(myApplication)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyFragment.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint.FragmentGenerator\")",
                      "@SuppressWarnings(\"rawtypes\")",
                      "abstract class Hilt_MyFragment extends Fragment implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }

  @Test
  public void copySuppressWarnings_onBroadcastRecieverGenerator_annotationCopied() {
    Source myBroadcastReceiver =
        HiltCompilerTests.javaSource(
            "test.MyBroadcastReceiver",
            "package test;",
            "",
            "import android.content.BroadcastReceiver;",
            "import android.annotation.TargetApi;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@SuppressWarnings(\"deprecation\")",
            "@AndroidEntryPoint(BroadcastReceiver.class)",
            "public class MyBroadcastReceiver extends Hilt_MyBroadcastReceiver {}");
    HiltCompilerTests.hiltCompiler(myBroadcastReceiver)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyBroadcastReceiver.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint."
                          + "BroadcastReceiverGenerator\")",
                      "@SuppressWarnings(\"deprecation\")",
                      "abstract class Hilt_MyBroadcastReceiver extends BroadcastReceiver {"));
            });
  }

  @Test
  public void copySuppressWarnings_onServiceGenerator_annotationCopied() {
    Source myService =
        HiltCompilerTests.javaSource(
            "test.MyService",
            "package test;",
            "",
            "import android.annotation.TargetApi;",
            "import android.content.Intent;",
            "import android.app.Service;",
            "import android.os.IBinder;",
            "import dagger.hilt.android.AndroidEntryPoint;",
            "",
            "@SuppressWarnings(\"deprecation\")",
            "@AndroidEntryPoint(Service.class)",
            "public class MyService extends Hilt_MyService {",
            "   @Override",
            "   public IBinder onBind(Intent intent){",
            "     return null;",
            "   }",
            "}");
    HiltCompilerTests.hiltCompiler(myService)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              StringSubject stringSubject =
                  subject.generatedSourceFileWithPath("test/Hilt_MyService.java");
              stringSubject.contains("package test;");
              stringSubject.contains(
                  JOINER.join(
                      "@Generated(\"dagger.hilt.android.processor.internal.androidentrypoint."
                          + "ServiceGenerator\")",
                      "@SuppressWarnings(\"deprecation\")",
                      "abstract class Hilt_MyService extends Service implements"
                          + " GeneratedComponentManagerHolder {"));
            });
  }
}
