/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.hilt.processor.internal.bindvalue;

import static com.google.testing.compile.CompilationSubject.assertThat;

import androidx.room.compiler.processing.util.Source;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.hilt.android.processor.internal.bindvalue.BindValueProcessor;
import dagger.hilt.android.processor.internal.bindvalue.KspBindValueProcessor;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BindValueErrorsTest {

  @Test
  public void testNoTestRootFails() {
    Source source =
        HiltCompilerTests.javaSource(
            "foo.BarTest",
            "package foo;",
            "",
            "import dagger.hilt.android.testing.BindValue;",
            "",
            "public final class BarTest {",
            "  @BindValue",
            "  int bindInt = 5;",
            "}");
    HiltCompilerTests.hiltCompiler(source)
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue can only be used within a class annotated with "
                      + "@HiltAndroidTest. Found: foo.BarTest");
            });
  }

  // TODO(kuanyingchou): Migrate to hiltCompiler() after b/288893275 is fixed.
  @Test
  public void testNotUsedWithFieldFails() {
    Compilation compilation =
        CompilerTests.compiler()
            .withOptions("-Xlint:-processing") // Suppresses unclaimed annotation warning
            .withProcessors(new BindValueProcessor())
            .compile(
                JavaFileObjects.forSourceLines(
                    "foo.BarTest",
                    "package foo;",
                    "",
                    "import dagger.hilt.android.testing.BindValue;",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "",
                    "@HiltAndroidTest",
                    "public final class BarTest {",
                    "  @BindValue class Baz {}",
                    "}"));

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(
        "annotation (type|interface) not applicable to this kind of declaration");
  }

  @Test
  public void testBindValueWithPrivateModifierFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @BindValue",
                "  private int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@BindValue fields cannot be private. Found: bindInt");
            });
  }

  @Test
  public void testBindValueWithInjectAnnotationFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Inject;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Inject",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue fields cannot be used with @Inject annotation. Found bindInt");
            });
  }

  @Test
  public void testBindValueWithMultipleQualifiersFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @Qualifier",
                "  @interface MyOtherQualifier{}",
                "",
                "  @MyQualifier",
                "  @MyOtherQualifier",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue fields cannot have more than one qualifier. "
                      + "Found [@foo.BarTest.MyQualifier, "
                      + "@foo.BarTest.MyOtherQualifier]");
            });
  }

  @Test
  public void testMultipleTypesOfBindValueAnnotationsFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.BindValueIntoSet;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @MyQualifier",
                "  @BindValueIntoSet",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Fields can be annotated with only one of @BindValue, @BindValueIntoMap, "
                      + "@BindElementsIntoSet, @BindValueIntoSet. Found: "
                      + "[@BindValueIntoSet, @BindValue]");
            });
  }

  @Test
  public void testBindValueIntoMapWithNoKeysFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValueIntoMap;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @MyQualifier",
                "  @BindValueIntoMap",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValueIntoMap fields must have exactly one @MapKey. Found []");
            });
  }

  @Test
  public void testBindValueIntoMapWithMultipleKeysFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValueIntoMap;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import dagger.MapKey;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @MyQualifier",
                "  @BindValueIntoMap",
                "  @MyMapKey(\"K1\")",
                "  @MyMapKey2(\"K2\")",
                "  int bindInt = 5;",
                "",
                "  @MapKey",
                "  @interface MyMapKey { String value(); }",
                "",
                "  @MapKey",
                "  @interface MyMapKey2 { String value(); }",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValueIntoMap fields must have exactly one @MapKey. "
                      + "Found [@foo.BarTest.MyMapKey(\"K1\"), @foo.BarTest.MyMapKey2(\"K2\")]");
            });
  }

  @Test
  public void testBindValueIntoSetWithMapKeyFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValueIntoSet;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import dagger.MapKey;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @MyQualifier",
                "  @BindValueIntoSet",
                "  @MyMapKey(\"K1\")",
                "  int bindInt = 5;",
                "",
                "  @MapKey",
                "  @interface MyMapKey { String value(); }",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@MapKey can only be used on @BindValueIntoMap fields, not "
                      + "@BindValueIntoSet fields");
            });
  }

  @Test
  public void testBindValueWithScopeFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Singleton;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Singleton",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue fields cannot be scoped. Found [@javax.inject.Singleton]");
            });
  }

  @Test
  public void testBindValueWithJakartaScopeFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import jakarta.inject.Singleton;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Singleton",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue fields cannot be scoped. Found [@jakarta.inject.Singleton]");
            });
  }

  @Test
  public void testBindValueWithMultipleJakartaJavaxQualifiersFails() {
    HiltCompilerTests.hiltCompiler(
            HiltCompilerTests.javaSource(
                "foo.BarTest",
                "package foo;",
                "",
                "import dagger.hilt.android.testing.BindValue;",
                "import dagger.hilt.android.testing.HiltAndroidTest;",
                "import javax.inject.Qualifier;",
                "",
                "@HiltAndroidTest",
                "public final class BarTest {",
                "  @Qualifier",
                "  @interface MyQualifier{}",
                "",
                "  @jakarta.inject.Qualifier",
                "  @interface MyOtherQualifier{}",
                "",
                "  @MyQualifier",
                "  @MyOtherQualifier",
                "  @BindValue",
                "  int bindInt = 5;",
                "}"))
        .withJavacArguments("-Xlint:-processing") // Suppresses unclaimed annotation warning
        .withAdditionalJavacProcessors(new BindValueProcessor())
        .withAdditionalKspProcessors(new KspBindValueProcessor.Provider())
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindValue fields cannot have more than one qualifier. "
                      + "Found [@foo.BarTest.MyQualifier, "
                      + "@foo.BarTest.MyOtherQualifier]");
            });
  }
}
