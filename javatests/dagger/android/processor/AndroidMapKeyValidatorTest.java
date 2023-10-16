/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.processor;

import androidx.room.compiler.processing.util.Source;
import com.google.common.base.Joiner;
import dagger.testing.compile.CompilerTests;
import dagger.testing.compile.CompilerTests.DaggerCompiler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidMapKeyValidatorTest {
  private static final Source FOO_ACTIVITY =
      CompilerTests.javaSource(
          "test.FooActivity",
          "package test;",
          "",
          "import android.app.Activity;",
          "import dagger.android.AndroidInjector;",
          "",
          "public class FooActivity extends Activity {",
          "  interface Factory extends AndroidInjector.Factory<FooActivity> {}",
          "  abstract static class Builder extends AndroidInjector.Builder<FooActivity> {}",
          "}");
  private static final Source BAR_ACTIVITY =
      CompilerTests.javaSource(
          "test.BarActivity",
          "package test;",
          "",
          "import android.app.Activity;",
          "",
          "public class BarActivity extends Activity {}");

  private static Source moduleWithMethod(String... lines) {
    return CompilerTests.javaSource(
        "test.AndroidModule",
        "package test;",
        "",
        "import android.app.Activity;",
        "import android.app.Fragment;",
        "import dagger.Module;",
        "import dagger.*;",
        "import dagger.android.*;",
        "import dagger.multibindings.*;",
        "import javax.inject.*;",
        "",
        "@Module",
        "abstract class AndroidModule {",
        "  " + Joiner.on("\n  ").join(lines),
        "}");
  }

  // TODO(dpb): Change these tests to use onLineContaining() instead of onLine().
  private static final int LINES_BEFORE_METHOD = 12;

  @Test
  public void rawFactoryType() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Factory bindRawFactory(FooActivity.Factory factory);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "should bind dagger.android.AndroidInjector.Factory<?>, "
                      + "not dagger.android.AndroidInjector.Factory");
            });
  }

  @Test
  public void rawBuilderType() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Builder bindRawBuilder(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "should bind dagger.android.AndroidInjector.Factory<?>, "
                      + "not dagger.android.AndroidInjector.Builder");
            });
  }

  @Test
  public void bindsToBuilderNotFactory() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<?> bindBuilder(",
            "    FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "should bind dagger.android.AndroidInjector.Factory<?>, not "
                      + "dagger.android.AndroidInjector.Builder<?>");
            });
  }

  @Test
  public void providesToBuilderNotFactory() {
    Source module =
        moduleWithMethod(
            "@Provides",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "static AndroidInjector.Builder<?> bindBuilder(FooActivity.Builder builder) {",
            "  return builder;",
            "}");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "should bind dagger.android.AndroidInjector.Factory<?>, not "
                      + "dagger.android.AndroidInjector.Builder<?>");
            });
  }

  @Test
  public void bindsToConcreteTypeInsteadOfWildcard() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<FooActivity> bindBuilder(",
            "    FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "should bind dagger.android.AndroidInjector.Factory<?>, not "
                      + "dagger.android.AndroidInjector.Builder<test.FooActivity>");
            });
  }

  @Test
  public void bindsToBaseTypeInsteadOfWildcard() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<Activity> bindBuilder(",
            "    FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining(
                  "@Binds methods' parameter type must be assignable to the return type");
            });
  }

  @Test
  public void bindsCorrectType() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Factory<?> bindCorrectType(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindsCorrectType_AndroidInjectionKey() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.FooActivity\")",
            "abstract AndroidInjector.Factory<?> bindCorrectType(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindsCorrectType_AndroidInjectionKey_unbounded() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.FooActivity\")",
            "abstract AndroidInjector.Factory<?> bindCorrectType(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindsWithScope() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "@Singleton",
            "abstract AndroidInjector.Factory<?> bindWithScope(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining("should not be scoped");
            });
  }

  @Test
  public void bindsWithScope_suppressWarnings() {
    Source module =
        moduleWithMethod(
            "@SuppressWarnings(\"dagger.android.ScopedInjectorFactory\")",
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "@Singleton",
            "abstract AndroidInjector.Factory<?> bindWithScope(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void mismatchedMapKey_bindsFactory() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(BarActivity.class)",
            "abstract AndroidInjector.Factory<?> mismatchedFactory(FooActivity.Factory factory);");
    compile(module, FOO_ACTIVITY, BAR_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining(
                      "test.FooActivity.Factory does not implement"
                          + " AndroidInjector<test.BarActivity>")
                  .onLine(LINES_BEFORE_METHOD + 3);
            });
  }

  @Test
  public void mismatchedMapKey_bindsBuilder() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(BarActivity.class)",
            "abstract AndroidInjector.Factory<?> mismatchedBuilder(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY, BAR_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining(
                      "test.FooActivity.Builder does not implement"
                          + " AndroidInjector<test.BarActivity>")
                  .onLine(LINES_BEFORE_METHOD + 3);
            });
  }

  @Test
  public void mismatchedMapKey_bindsBuilder_androidInjectionKey() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.BarActivity\")",
            "abstract AndroidInjector.Factory<?> mismatchedBuilder(FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY, BAR_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining(
                      "test.FooActivity.Builder does not implement"
                          + " AndroidInjector<test.BarActivity>")
                  .onLine(LINES_BEFORE_METHOD + 3);
            });
  }

  @Test
  public void mismatchedMapKey_providesBuilder() {
    Source module =
        moduleWithMethod(
            "@Provides",
            "@IntoMap",
            "@ClassKey(BarActivity.class)",
            "static AndroidInjector.Factory<?> mismatchedBuilder(FooActivity.Builder builder) {",
            "  return builder;",
            "}");
    compile(module, FOO_ACTIVITY, BAR_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindsQualifier_ignoresChecks() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "@Named(\"unused\")",
            // normally this should fail, since it is binding to a Builder not a Factory
            "abstract AndroidInjector.Builder<?> bindsBuilderWithQualifier(",
            "    FooActivity.Builder builder);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindToPrimitive() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.FooActivity\")",
            "abstract int bindInt(@Named(\"unused\") int otherInt);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void bindToNonFrameworkClass() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.FooActivity\")",
            "abstract Number bindInt(Integer integer);");
    compile(module, FOO_ACTIVITY)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasNoWarnings();
            });
  }

  @Test
  public void invalidBindsMethod() {
    Source module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ClassKey(FooActivity.class)",
            "abstract AndroidInjector.Factory<?> bindCorrectType(",
            "    FooActivity.Builder builder, FooActivity.Builder builder2);");
    compile(module, FOO_ACTIVITY).compile(subject -> subject.compilationDidFail());
  }

  private DaggerCompiler compile(Source... files) {
    return CompilerTests.daggerCompiler(files)
        .withAdditionalJavacProcessors(new AndroidProcessor())
        .withAdditionalKspProcessors(new KspAndroidProcessor.Provider());
  }
}
