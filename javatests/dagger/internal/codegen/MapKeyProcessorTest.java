/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.truth.TruthJUnit.assume;

import androidx.room.compiler.processing.XProcessingEnv.Backend;
import androidx.room.compiler.processing.util.Source;
import com.google.auto.value.processor.AutoAnnotationProcessor;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapKeyProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public MapKeyProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapKeyCreatorFile() {
    Source enumKeyFile =
        CompilerTests.javaSource("test.PathKey",
          "package test;",
          "import dagger.MapKey;",
          "import java.lang.annotation.Retention;",
          "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
          "",
          "@MapKey(unwrapValue = false)",
          "@Retention(RUNTIME)",
          "public @interface PathKey {",
          "  PathEnum value();",
          "  String relativePath() default \"Defaultpath\";",
          "}");
    Source pathEnumFile =
        CompilerTests.javaSource("test.PathEnum",
          "package test;",
          "",
          "public enum PathEnum {",
          "    ADMIN,",
          "    LOGIN;",
          "}");
    CompilerTests.daggerCompiler(enumKeyFile, pathEnumFile)
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(b/264464791): There is no AutoAnnotationProcessor for KSP.
              assume().that(CompilerTests.backend(subject)).isNotEqualTo(Backend.KSP);
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/PathKeyCreator"));
            });
  }

  @Test
  public void nestedMapKeyCreatorFile() {
    Source enumKeyFile = CompilerTests.javaSource("test.Container",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "public interface Container {",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}",
        "}");
    Source pathEnumFile = CompilerTests.javaSource("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    CompilerTests.daggerCompiler(enumKeyFile, pathEnumFile)
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(b/264464791): There is no AutoAnnotationProcessor for KSP.
              assume().that(CompilerTests.backend(subject)).isNotEqualTo(Backend.KSP);
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/Container_PathKeyCreator"));
            });
  }

  @Test
  public void nestedComplexMapKey_buildSuccessfully() {
    Source outerKey =
        CompilerTests.javaSource(
            "test.OuterKey",
            "package test;",
            "import dagger.MapKey;",
            "import java.lang.annotation.Retention;",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "@MapKey(unwrapValue = false)",
            "public @interface OuterKey {",
            "  String value() default \"hello\";",
            "  NestedKey[] nestedKeys() default {};",
            "}");
    Source nestedKey =
        CompilerTests.javaSource(
            "test.NestedKey",
            "package test;",
            "import dagger.MapKey;",
            "import java.lang.annotation.Retention;",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "@MapKey(unwrapValue = false)",
            "public @interface NestedKey {",
            " String value() default \"hello\";",
            " String otherValue() default \"world\";",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.multibindings.IntoMap;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public final class FooModule {",
            "  @IntoMap",
            "  @OuterKey(nestedKeys = @NestedKey)",
            "  @Provides",
            "  String provideString() { return \"hello\";}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "",
            "@Component(modules = FooModule.class)",
            "public interface MyComponent {",
            "  Map<OuterKey, String> getFoo();",
            "}");
    CompilerTests.daggerCompiler(outerKey, nestedKey, foo, component)
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(b/264464791): There is no AutoAnnotationProcessor for KSP.
              assume().that(CompilerTests.backend(subject)).isNotEqualTo(Backend.KSP);
              subject.hasErrorCount(0);
            });
  }
}
