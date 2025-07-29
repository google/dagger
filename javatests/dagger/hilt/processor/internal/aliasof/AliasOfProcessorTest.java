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

import androidx.room.compiler.processing.util.Source;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for failure on alias scope used on DefineComponent. */
@RunWith(JUnit4.class)
public final class AliasOfProcessorTest {
  @Test
  public void fails_componentScopedWithAliasScope() {
    Source scope =
        HiltCompilerTests.javaSource(
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

    Source root =
        HiltCompilerTests.javaSource(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public final class MyApp extends Hilt_MyApp {}");

    Source defineComponent =
        HiltCompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@DefineComponent(parent = SingletonComponent.class)",
            "@AliasScope",
            "public interface ChildComponent {}");

    HiltCompilerTests.hiltCompiler(root, defineComponent, scope)
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .compile(
            subject ->
                // TODO(user): TAP result inconsistent with local build.
                // if (HiltCompilerTests.backend(subject) == Backend.JAVAC) {
                //   subject.hasErrorCount(2);
                // } else {
                //   subject.hasErrorCount(1);
                // }
                subject.hasErrorContaining(
                    "@DefineComponent test.ChildComponent, references invalid scope(s) annotated"
                    + " with @AliasOf. @DefineComponent scopes cannot be aliases of other scopes:"
                    + " [@test.AliasScope]"));
  }

  @Test
  public void fails_aliasOfOnNonScope() {
    Source scope =
        HiltCompilerTests.javaSource(
            "test.AliasScope",
            "package test;",
            "",
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
  public void succeeds_aliasOfJakartaScope() {
    Source scope =
        HiltCompilerTests.javaSource(
            "test.AliasScope",
            "package test;",
            "",
            "import jakarta.inject.Scope;",
            "import javax.inject.Singleton;",
            "import dagger.hilt.migration.AliasOf;",
            "",
            "@Scope",
            "@AliasOf(Singleton.class)",
            "public @interface AliasScope{}");

    HiltCompilerTests.hiltCompiler(scope).compile(subject -> subject.hasErrorCount(0));
  }

  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();

  @Test
  public void fails_conflictingAliasScope() {
    Source scope =
        HiltCompilerTests.javaSource(
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

    Source root =
        HiltCompilerTests.javaSource(
            "test.MyApp",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public final class MyApp extends Hilt_MyApp {}");

    HiltCompilerTests.hiltCompiler(root, scope)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("has conflicting scopes");
            });
  }
}
