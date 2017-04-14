/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests validation of {@code @ForReleasableRefernces}. */
@RunWith(JUnit4.class)
public class ForReleasableReferencesValidatorTest {
  @Test
  public void notAScope() {
    JavaFileObject notAScope =
        JavaFileObjects.forSourceLines(
            "test.NotAScope", // force one-string-per-line format
            "package test;",
            "",
            "@interface NotAScope {}");
    JavaFileObject injects =
        JavaFileObjects.forSourceLines(
            "test.Injects",
            "package test;",
            "",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "interface Injects {",
            "  @ForReleasableReferences(NotAScope.class) ReleasableReferenceManager manager();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(notAScope, injects))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "The value of @ForReleasableReferences must be a reference-releasing scope. "
                + "Did you mean to annotate test.NotAScope with @javax.inject.Scope and "
                + "@dagger.releasablereferences.CanReleaseReferences?")
        .in(injects)
        .onLine(7)
        .atColumn(3);
  }

  @Test
  public void notAReferenceReleasingScope() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@Retention(RUNTIME)",
            "@Scope",
            "@interface TestScope {}");
    JavaFileObject injects =
        JavaFileObjects.forSourceLines(
            "test.Injects",
            "package test;",
            "",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "interface Injects {",
            "  @ForReleasableReferences(TestScope.class) ReleasableReferenceManager manager();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(testScope, injects))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "The value of @ForReleasableReferences must be a reference-releasing scope. "
                + "Did you mean to annotate test.TestScope with "
                + "@dagger.releasablereferences.CanReleaseReferences?")
        .in(injects)
        .onLine(7)
        .atColumn(3);
  }
}
