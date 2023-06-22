/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.aliasof;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import androidx.room.compiler.processing.util.Source;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for failure on alias scope used on DefineComponent. */
@RunWith(JUnit4.class)
public final class AliasOfProcessorTest {
  @Test
  public void fails_componentScopedWithAliasScope() {
    JavaFileObject scope =
        JavaFileObjects.forSourceLines(
            "test.AliasScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "import javax.inject.Singleton;",
            "import dagger.hilt.migration.AliasOf;",
            "",
            "@Scope",
            "@AliasOf(Singleton.class)",
            "public @interface AliasScope{}");

    JavaFileObject root =
        JavaFileObjects.forSourceLines(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public final class MyApp extends Hilt_MyApp {}");

    JavaFileObject defineComponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@DefineComponent(parent = SingletonComponent.class)",
            "@AliasScope",
            "public interface ChildComponent {}");

    Compilation compilation =
        compiler()
            .withOptions("-Xlint:-processing") // Suppresses unclaimed annotation warning
            .compile(root, defineComponent, scope);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent test.ChildComponent, references invalid scope(s) annotated with"
                + " @AliasOf. @DefineComponent scopes cannot be aliases of other scopes:"
                + " [@test.AliasScope]");
  }

  @Test
  public void fails_alisOfOnNonScope() {
    Source scope =
        HiltCompilerTests.javaSource(
            "test.AliasScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "import javax.inject.Singleton;",
            "import dagger.hilt.migration.AliasOf;",
            "",
            "@AliasOf(Singleton.class)",
            "public @interface AliasScope{}");

    HiltCompilerTests.hiltCompiler(scope)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "AliasOf should only be used on scopes. However, it was found "
                      + "annotating test.AliasScope");
            });
  }

  @Test
  public void fails_conflictingAliasScope() {
    JavaFileObject scope =
        JavaFileObjects.forSourceLines(
            "test.AliasScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "import javax.inject.Singleton;",
            "import dagger.hilt.android.scopes.ActivityScoped;",
            "import dagger.hilt.migration.AliasOf;",
            "",
            "@Scope",
            "@AliasOf({Singleton.class, ActivityScoped.class})",
            "public @interface AliasScope{}");

    JavaFileObject root =
        JavaFileObjects.forSourceLines(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public final class MyApp extends Hilt_MyApp {}");

    Compilation compilation =
        compiler()
            .withOptions("-Xlint:-processing") // Suppresses unclaimed annotation warning
            .compile(root, scope);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("has conflicting scopes");
  }
}
