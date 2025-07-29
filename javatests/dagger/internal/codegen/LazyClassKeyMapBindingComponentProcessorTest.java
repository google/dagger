/*
 * Copyright (C) 2024 The Dagger Authors.
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
public class LazyClassKeyMapBindingComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public LazyClassKeyMapBindingComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapBindingsWithInaccessibleKeys() throws Exception {
    Source mapKeys =
        CompilerTests.javaSource(
            "mapkeys.MapKeys",
            "package mapkeys;",
            "",
            "import dagger.MapKey;",
            "import dagger.multibindings.LazyClassKey;",
            "",
            "public class MapKeys {",
            "  @MapKey(unwrapValue = false)",
            "  public @interface ComplexKey {",
            "    Class<?>[] manyClasses();",
            "    Class<?> oneClass();",
            "    LazyClassKey annotation();",
            "  }",
            "",
            "  interface Inaccessible {}",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "mapkeys.MapModule",
            "package mapkeys;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "public interface MapModule {",
            "  @Provides @IntoMap @LazyClassKey(MapKeys.Inaccessible.class)",
            "  static int classKey() { return 1; }",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {java.lang.Object.class, java.lang.String.class},",
            "    oneClass = MapKeys.Inaccessible.class,",
            "    annotation = @LazyClassKey(java.lang.Object.class)",
            "  )",
            "  static int complexKeyWithInaccessibleValue() { return 1; }",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {MapKeys.Inaccessible.class, java.lang.String.class},",
            "    oneClass = java.lang.String.class,",
            "    annotation = @LazyClassKey(java.lang.Object.class)",
            "  )",
            "  static int complexKeyWithInaccessibleArrayValue() { return 1; }",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {java.lang.String.class},",
            "    oneClass = java.lang.String.class,",
            "    annotation = @LazyClassKey(MapKeys.Inaccessible.class)",
            "  )",
            "  static int complexKeyWithInaccessibleAnnotationValue() { return 1; }",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "import mapkeys.MapKeys;",
            "import mapkeys.MapModule;",
            "",
            "@Component(modules = MapModule.class)",
            "interface TestComponent {",
            "  Map<Class<?>, Integer> classKey();",
            "  Provider<Map<Class<?>, Integer>> classKeyProvider();",
            "",
            "  Map<MapKeys.ComplexKey, Integer> complexKey();",
            "  Provider<Map<MapKeys.ComplexKey, Integer>> complexKeyProvider();",
            "}");
    CompilerTests.daggerCompiler(mapKeys, moduleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .withAdditionalJavacProcessors(new AutoAnnotationProcessor())
        .compile(
          subject -> {
              // TODO(b/264464791): There is no AutoAnnotationProcessor for KSP.
              assume().that(CompilerTests.backend(subject)).isNotEqualTo(Backend.KSP);
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "mapkeys.MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("mapkeys.MapModule_ClassKeyMapKey"));
            }
        );
  }

  @Test
  public void lazyClassKeySimilarQualifiedName_doesNotConflict() throws Exception {
    Source fooBar =
        CompilerTests.javaSource("test.Foo_Bar", "package test;", "", "interface Foo_Bar {}");
    Source fooBar2 =
        CompilerTests.javaSource(
            "test.Foo", "package test;", "", "interface Foo { interface Bar {} }");
    Source mapKeyBindingsModule =
        CompilerTests.javaSource(
            "test.MapKeyBindingsModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface MapKeyBindingsModule {",
            " @Provides @IntoMap @LazyClassKey(test.Foo_Bar.class)",
            " static int classKey() { return 1; }",
            "",
            " @Provides @IntoMap @LazyClassKey(test.Foo.Bar.class)",
            " static int classKey2() { return 1; }",
            "}");

    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "",
            "@Component(modules = MapKeyBindingsModule.class)",
            "interface TestComponent {",
            "  Map<Class<?>, Integer> classKey();",
            "}");
    CompilerTests.daggerCompiler(fooBar, fooBar2, mapKeyBindingsModule, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void lazyClassKeyProvider_compilesSuccessfully() throws Exception {
    Source fooBar =
        CompilerTests.javaSource("test.Foo_Bar", "package test;", "", "interface Foo_Bar {}");
    Source fooBar2 =
        CompilerTests.javaSource(
            "test.Foo", "package test;", "", "interface Foo { interface Bar {} }");
    Source mapKeyBindingsModule =
        CompilerTests.javaSource(
            "test.MapKeyBindingsModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface MapKeyBindingsModule {",
            " @Provides @IntoMap @LazyClassKey(test.Foo_Bar.class)",
            " static int classKey() { return 1; }",
            "}");

    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import java.util.Map;",
            "",
            "@Component(modules = MapKeyBindingsModule.class)",
            "interface TestComponent {",
            "  Map<Class<?>, Provider<Integer>> classKey();",
            "}");
    CompilerTests.daggerCompiler(fooBar, fooBar2, mapKeyBindingsModule, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void scopedLazyClassKeyProvider_compilesSuccessfully() throws Exception {
    Source fooBar =
        CompilerTests.javaSource("test.Foo_Bar", "package test;", "", "interface Foo_Bar {}");
    Source fooBar2 =
        CompilerTests.javaSource(
            "test.Foo", "package test;", "", "interface Foo { interface Bar {} }");
    Source mapKeyBindingsModule =
        CompilerTests.javaSource(
            "test.MapKeyBindingsModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface MapKeyBindingsModule {",
            " @Provides @IntoMap @LazyClassKey(test.Foo_Bar.class)",
            " static int classKey() { return 1; }",
            "}");

    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import javax.inject.Provider;",
            "import java.util.Map;",
            "",
            "@Component(modules = MapKeyBindingsModule.class)",
            "@Singleton",
            "interface TestComponent {",
            "  Provider<Map<Class<?>, Provider<Integer>>> classKey();",
            "}");
    CompilerTests.daggerCompiler(fooBar, fooBar2, mapKeyBindingsModule, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProguardFile() throws Exception {
    Source fooKey =
        CompilerTests.javaSource(
            "test.FooKey",
            "package test;",
            "",
            "interface FooKey {}");
    Source fooKeyModule =
        CompilerTests.javaSource(
            "test.FooKeyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface FooKeyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @LazyClassKey(FooKey.class)",
            "  static String provideString() { return \"\"; }",
            "}");
    CompilerTests.daggerCompiler(fooKey, fooKeyModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedTextResourceFileWithPath(
                      "META-INF/proguard/test_FooKeyModule_LazyClassKeys.pro")
                  .isEqualTo("-keep,allowobfuscation,allowshrinking class test.FooKey");
            });
  }

  @Test
  public void testProguardFile_nestedModule() throws Exception {
    Source fooKey =
        CompilerTests.javaSource(
            "test.FooKey",
            "package test;",
            "",
            "interface FooKey {}");
    Source outerClass =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "public interface OuterClass {",
            "  @Module",
            "  public interface FooKeyModule {",
            "    @Provides",
            "    @IntoMap",
            "    @LazyClassKey(FooKey.class)",
            "    static String provideString() { return \"\"; }",
            "  }",
            "}");
    CompilerTests.daggerCompiler(fooKey, outerClass)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedTextResourceFileWithPath(
                      "META-INF/proguard/test_OuterClass_FooKeyModule_LazyClassKeys.pro")
                  .isEqualTo("-keep,allowobfuscation,allowshrinking class test.FooKey");
            });
  }

  @Test
  public void testProguardFile_multipleModules() throws Exception {
    Source fooKey =
        CompilerTests.javaSource(
            "test.FooKey",
            "package test;",
            "",
            "interface FooKey {}");
    Source barKey =
        CompilerTests.javaSource(
            "test.BarKey",
            "package test;",
            "",
            "interface BarKey {}");
    Source fooKeyModule =
        CompilerTests.javaSource(
            "test.FooKeyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface FooKeyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @LazyClassKey(FooKey.class)",
            "  static String provideString() { return \"\"; }",
            "}");
    Source barKeyModule =
        CompilerTests.javaSource(
            "test.BarKeyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface BarKeyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @LazyClassKey(BarKey.class)",
            "  static String provideString() { return \"\"; }",
            "}");
    CompilerTests.daggerCompiler(fooKey, fooKeyModule, barKey, barKeyModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedTextResourceFileWithPath(
                      "META-INF/proguard/test_FooKeyModule_LazyClassKeys.pro")
                  .isEqualTo("-keep,allowobfuscation,allowshrinking class test.FooKey");
              subject.generatedTextResourceFileWithPath(
                      "META-INF/proguard/test_BarKeyModule_LazyClassKeys.pro")
                  .isEqualTo("-keep,allowobfuscation,allowshrinking class test.BarKey");
            });
  }

  @Test
  public void testProguardFile_multipleKeys() throws Exception {
    Source fooKey =
        CompilerTests.javaSource(
            "test.FooKey",
            "package test;",
            "",
            "interface FooKey {}");
    Source barKey =
        CompilerTests.javaSource(
            "test.BarKey",
            "package test;",
            "",
            "interface BarKey {}");
    Source fooKeyAndBarKeyModule =
        CompilerTests.javaSource(
            "test.FooKeyAndBarKeyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.LazyClassKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "public interface FooKeyAndBarKeyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @LazyClassKey(FooKey.class)",
            "  static String provideFooKeyString() { return \"\"; }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @LazyClassKey(BarKey.class)",
            "  static String provideBarKeyString() { return \"\"; }",
            "}");
    CompilerTests.daggerCompiler(fooKey, barKey, fooKeyAndBarKeyModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedTextResourceFileWithPath(
                      "META-INF/proguard/test_FooKeyAndBarKeyModule_LazyClassKeys.pro")
                  .isEqualTo(
                      "-keep,allowobfuscation,allowshrinking class test.FooKey\n"
                          + "-keep,allowobfuscation,allowshrinking class test.BarKey");
            });
  }
}
