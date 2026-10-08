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

import androidx.room3.compiler.processing.XProcessingEnv.Backend;
import androidx.room3.compiler.processing.util.CompilationResultSubject;
import androidx.room3.compiler.processing.util.Source;
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

  private void assumeMapKeySupported(Backend backend) {
    // TODO(b/264464791): There is no AutoAnnotationProcessor for KSP when generating Java.
    assume().that(backend != Backend.KSP).isTrue();
  }

  private void assertSourceMatchesGolden(CompilationResultSubject subject, String goldenName) {
    Source source = goldenFileRule.goldenSource(goldenName);
    subject.generatedSource(source);
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
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/PathKeyCreator");
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
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/Container_PathKeyCreator");
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
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
            });
  }

  @Test
  public void complexLiteralsKey() {
    Source complexKey =
        CompilerTests.kotlinSource(
            "ComplexLiteralsKey.kt",
            "package test",
            "",
            "import dagger.MapKey",
            "import kotlin.reflect.KClass",
            "",
            "@MapKey(unwrapValue = false)",
            "annotation class ComplexLiteralsKey(",
            "  val clazz: KClass<*>,",
            "  val clazzArray: Array<KClass<*>>,",
            "  val intArray: IntArray,",
            "  val byteVal: Byte,",
            "  val shortVal: Short",
            ")");
    Source module =
        CompilerTests.kotlinSource(
            "FooModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoMap",
            "",
            "@Module",
            "class FooModule {",
            "  @IntoMap",
            "  @ComplexLiteralsKey(",
            "    clazz = String::class,",
            "    clazzArray = [String::class, Any::class],",
            "    intArray = [1, 2],",
            "    byteVal = (-3).toByte(),",
            "    shortVal = (-4).toShort()",
            "  )",
            "  @Provides",
            "  fun provideString(): String = \"hello\"",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [FooModule::class])",
            "interface MyComponent {",
            "  fun getFoo(): Map<ComplexLiteralsKey, String>",
            "}");
    CompilerTests.daggerCompiler(complexKey, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .compile(
            subject -> {
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/ComplexLiteralsKeyCreator");
            });
  }

  @Test
  public void nestedAnnotations() {
    Source outerKey =
        CompilerTests.kotlinSource(
            "OuterKey.kt",
            "package test",
            "",
            "import dagger.MapKey",
            "",
            "@MapKey(unwrapValue = false)",
            "annotation class OuterKey(val nested: NestedKey)",
            "",
            "annotation class NestedKey(val value: String)");
    CompilerTests.daggerCompiler(outerKey)
        .withProcessingOptions(compilerMode.processorOptions())
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .compile(
            subject -> {
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/OuterKeyCreator");
            });
  }

  @Test
  public void unwrappedMapKey_withNestedAnnotation() {
    Source unwrappedKey =
        CompilerTests.kotlinSource(
            "UnwrappedOuterKey.kt",
            "package test",
            "",
            "import dagger.MapKey",
            "",
            "@MapKey(unwrapValue = true)",
            "annotation class UnwrappedOuterKey(val nested: NestedKey)",
            "",
            "annotation class NestedKey(val value: String)");
    CompilerTests.daggerCompiler(unwrappedKey)
        .withProcessingOptions(compilerMode.processorOptions())
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .compile(
            subject -> {
              assumeMapKeySupported(CompilerTests.backend(subject));
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/UnwrappedOuterKeyCreator");
            });
  }
}
