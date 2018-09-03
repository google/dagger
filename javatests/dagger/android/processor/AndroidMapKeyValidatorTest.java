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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.common.base.Joiner;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.ComponentProcessor;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidMapKeyValidatorTest {
  private static final JavaFileObject FOO_ACTIVITY =
      JavaFileObjects.forSourceLines(
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
  private static final JavaFileObject BAR_ACTIVITY =
      JavaFileObjects.forSourceLines(
          "test.BarActivity",
          "package test;",
          "",
          "import android.app.Activity;",
          "",
          "public class BarActivity extends Activity {}");
  private static final JavaFileObject BAZ_FRAGMENT =
      JavaFileObjects.forSourceLines(
          "test.BazFragment",
          "package test;",
          "",
          "import android.app.Fragment;",
          "",
          "public class BazFragment extends Fragment {}");

  private static JavaFileObject moduleWithMethod(String... lines) {
    return JavaFileObjects.forSourceLines(
        "test.AndroidModule",
        "package test;",
        "",
        "import android.app.Activity;",
        "import android.app.Fragment;",
        "import dagger.Module;",
        "import dagger.*;",
        "import dagger.android.*;",
        "import dagger.multibindings.IntoMap;",
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
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Factory bindRawFactory(FooActivity.Factory factory);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.ActivityKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>, not "
                + "dagger.android.AndroidInjector.Factory");
  }

  @Test
  public void rawBuilderType() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Builder bindRawBuilder(FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.ActivityKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>, not "
                + "dagger.android.AndroidInjector.Builder");
  }

  @Test
  public void bindsToBuilderNotFactory() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<? extends Activity> bindBuilder(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.ActivityKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>, not "
                + "dagger.android.AndroidInjector.Builder<? extends android.app.Activity>");
  }

  @Test
  public void providesToBuilderNotFactory() {
    JavaFileObject module =
        moduleWithMethod(
            "@Provides",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "static AndroidInjector.Builder<? extends Activity> bindBuilder(",
            "    FooActivity.Builder builder) {",
            "  return builder;",
            "}");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.ActivityKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>, not "
                + "dagger.android.AndroidInjector.Builder<? extends android.app.Activity>");
  }

  @Test
  public void mapKeyDoesntMatchCoreType() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@FragmentKey(BazFragment.class)",
            "abstract AndroidInjector.Factory<? extends Activity> bindWrongFrameworkType(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAZ_FRAGMENT);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.FragmentKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Fragment>, not "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>");
    assertThat(compilation)
        .hadErrorContaining(
            "test.FooActivity.Builder does not implement AndroidInjector<test.BazFragment>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void mapKeyDoesntMatchCoreType_AndroidInjectionKey() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.BazFragment\")",
            "abstract AndroidInjector.Factory<? extends Activity> bindWrongFrameworkType(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAZ_FRAGMENT);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "bindWrongFrameworkType(test.FooActivity.Builder) should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Fragment>, not "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>");
    assertThat(compilation)
        .hadErrorContaining(
            "test.FooActivity.Builder does not implement AndroidInjector<test.BazFragment>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void mapKeyDoesntMatchCoreType_supportFragments() {
    JavaFileObject supportFragment =
        JavaFileObjects.forSourceLines(
            "test.SupportFragment",
            "package test;",
            "",
            "import android.support.v4.app.Fragment;",
            "import dagger.android.AndroidInjector;",
            "",
            "public class SupportFragment extends Fragment {",
            "  interface Factory extends AndroidInjector.Factory<SupportFragment> {}",
            "}");

    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@dagger.android.FragmentKey(BazFragment.class)",
            "abstract AndroidInjector.Factory<? extends android.support.v4.app.Fragment> ",
            "    bindWrongFrameworkType(SupportFragment.Factory factory);");
    Compilation compilation = compile(module, BAZ_FRAGMENT, supportFragment);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.FragmentKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Fragment>, not "
                + "dagger.android.AndroidInjector.Factory<? extends "
                + "android.support.v4.app.Fragment>");
    assertThat(compilation)
        .hadErrorContaining(
            "test.SupportFragment.Factory does not implement AndroidInjector<test.BazFragment>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void bindsToConcreteTypeInsteadOfWildcard() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<FooActivity> bindBuilder(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@dagger.android.ActivityKey methods should bind "
                + "dagger.android.AndroidInjector.Factory<? extends android.app.Activity>, not "
                + "dagger.android.AndroidInjector.Builder<test.FooActivity>");
  }

  @Test
  public void bindsToBaseTypeInsteadOfWildcard() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Builder<Activity> bindBuilder(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Binds methods' parameter type must be assignable to the return type");
  }

  @Test
  public void bindsCorrectType() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Factory<? extends Activity> bindCorrectType(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void bindsCorrectType_AndroidInjectionKey() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.FooActivity\")",
            "abstract AndroidInjector.Factory<? extends Activity> bindCorrectType(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void bindsWithScope() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "@Singleton",
            "abstract AndroidInjector.Factory<? extends Activity> bindWithScope(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("should not be scoped");
  }

  @Test
  public void bindsWithScope_suppressWarnings() {
    JavaFileObject module =
        moduleWithMethod(
            "@SuppressWarnings(\"dagger.android.ScopedInjectorFactory\")",
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "@Singleton",
            "abstract AndroidInjector.Factory<? extends Activity> bindWithScope(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void mismatchedMapKey_bindsFactory() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(BarActivity.class)",
            "abstract AndroidInjector.Factory<?> mismatchedFactory(",
            "    FooActivity.Factory factory);");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAR_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.FooActivity.Factory does not implement AndroidInjector<test.BarActivity>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void mismatchedMapKey_bindsBuilder() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(BarActivity.class)",
            "abstract AndroidInjector.Factory<? extends Activity> mismatchedBuilder(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAR_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.FooActivity.Builder does not implement AndroidInjector<test.BarActivity>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void mismatchedMapKey_bindsBuilder_androidInjectionKey() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@AndroidInjectionKey(\"test.BarActivity\")",
            "abstract AndroidInjector.Factory<? extends Activity> mismatchedBuilder(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAR_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.FooActivity.Builder does not implement AndroidInjector<test.BarActivity>")
        .inFile(module)
        .onLine(LINES_BEFORE_METHOD + 3);
  }

  @Test
  public void mismatchedMapKey_providesBuilder() {
    JavaFileObject module =
        moduleWithMethod(
            "@Provides",
            "@IntoMap",
            "@ActivityKey(BarActivity.class)",
            "static AndroidInjector.Factory<? extends Activity> mismatchedBuilder(",
            "    FooActivity.Builder builder) {",
            "  return builder;",
            "}");
    Compilation compilation = compile(module, FOO_ACTIVITY, BAR_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void bindsQualifier_ignoresChecks() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "@Named(\"unused\")",
            // normally this should fail, since it is binding to a Builder not a Factory
            "abstract AndroidInjector.Builder<?> bindsBuilderWithQualifier(",
            "    FooActivity.Builder builder);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void bindToPrimitive() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract int bindInt(@Named(\"unused\") int otherInt);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void bindToNonFrameworkClass() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract Number bindInt(Integer integer);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void invalidBindsMethod() {
    JavaFileObject module =
        moduleWithMethod(
            "@Binds",
            "@IntoMap",
            "@ActivityKey(FooActivity.class)",
            "abstract AndroidInjector.Factory<?> bindCorrectType(",
            "    FooActivity.Builder builder, FooActivity.Builder builder2);");
    Compilation compilation = compile(module, FOO_ACTIVITY);
    assertThat(compilation).failed();
  }

  private Compilation compile(JavaFileObject... files) {
    return javac().withProcessors(new ComponentProcessor(), new AndroidProcessor()).compile(files);
  }
}
