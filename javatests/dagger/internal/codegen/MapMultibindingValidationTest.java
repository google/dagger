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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapMultibindingValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MapMultibindingValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void duplicateMapKeys_UnwrappedMapKey() {
    Source module =
        CompilerTests.javaSource(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.StringKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "final class MapModule {",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKey() {",
            "    return \"one\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKeyAgain() {",
            "    return \"one again\";",
            "  }",
            "}");

    // If they're all there, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            component(
                "Map<String, Object> objects();",
                "Map<String, Provider<Object>> objectProviders();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Object>");
              subject.hasErrorContaining("provideObjectForAKey()");
              subject.hasErrorContaining("provideObjectForAKeyAgain()");
            });

    CompilerTests.daggerCompiler(module)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "The same map key is bound more than once for Map<String,Provider<Object>>")
                  .onSource(module)
                  .onLineContaining("class MapModule");
              subject.hasErrorContaining("provideObjectForAKey()");
              subject.hasErrorContaining("provideObjectForAKeyAgain()");
            });

    // If there's Map<K, V> and Map<K, Provider<V>>, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            component(
                "Map<String, Object> objects();",
                "Map<String, Provider<Object>> objectProviders();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Object>");
            });

    // If there's Map<K, V> and Map<K, Producer<V>>, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            component(
                "Map<String, Object> objects();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Object>");
            });

    // If there's Map<K, Provider<V>> and Map<K, Producer<V>>, report only Map<K, Provider<V>>.
    CompilerTests.daggerCompiler(
            module,
            component(
                "Map<String, Provider<Object>> objectProviders();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Provider<Object>>");
            });

    CompilerTests.daggerCompiler(
            module,
            component("Map<String, Object> objects();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Object>");
            });

    CompilerTests.daggerCompiler(
            module,
            component("Map<String, Provider<Object>> objectProviders();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Provider<Object>>");
            });

    CompilerTests.daggerCompiler(
            module,
            component("Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The same map key is bound more than once for Map<String,Producer<Object>>");
            });
  }

  @Test
  public void duplicateMapKeys_WrappedMapKey() {
    Source module =
        CompilerTests.javaSource(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.MapKey;",
            "",
            "@Module",
            "abstract class MapModule {",
            "",
            "  @MapKey(unwrapValue = false)",
            "  @interface WrappedMapKey {",
            "    String value();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @WrappedMapKey(\"foo\")",
            "  static String stringMapEntry1() { return \"\"; }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @WrappedMapKey(\"foo\")",
            "  static String stringMapEntry2() { return \"\"; }",
            "}");

    Source component = component("Map<test.MapModule.WrappedMapKey, String> objects();");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "\033[1;31m[Dagger/MapKeys]\033[0m The same map key is bound more than once for "
                    + "Map<MapModule.WrappedMapKey,String>");
              subject.hasErrorContaining(
                  "    @Provides @IntoMap @MapModule.WrappedMapKey(\"foo\") String "
                    + "MapModule.stringMapEntry1()");
              subject.hasErrorContaining(
                  "    @Provides @IntoMap @MapModule.WrappedMapKey(\"foo\") String "
                    + "MapModule.stringMapEntry2()")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void inconsistentMapKeyAnnotations() {
    Source module =
        CompilerTests.javaSource(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.StringKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "final class MapModule {",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKey() {",
            "    return \"one\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKeyTwo(\"BKey\") Object provideObjectForBKey() {",
            "    return \"two\";",
            "  }",
            "}");
    Source stringKeyTwoFile =
        CompilerTests.javaSource(
            "test.StringKeyTwo",
            "package test;",
            "",
            "import dagger.MapKey;",
            "",
            "@MapKey(unwrapValue = true)",
            "public @interface StringKeyTwo {",
            "  String value();",
            "}");

    // If they're all there, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component(
                "Map<String, Object> objects();",
                "Map<String, Provider<Object>> objectProviders();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Object> uses more than one @MapKey annotation type");
              subject.hasErrorContaining("provideObjectForAKey()");
              subject.hasErrorContaining("provideObjectForBKey()");
            });

    CompilerTests.daggerCompiler(module, stringKeyTwoFile)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Map<String,Provider<Object>> uses more than one @MapKey annotation type")
                  .onSource(module)
                  .onLineContaining("class MapModule");
              subject.hasErrorContaining("provideObjectForAKey()");
              subject.hasErrorContaining("provideObjectForBKey()");
            });

    // If there's Map<K, V> and Map<K, Provider<V>>, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component(
                "Map<String, Object> objects();",
                "Map<String, Provider<Object>> objectProviders();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Object> uses more than one @MapKey annotation type");
            });

    // If there's Map<K, V> and Map<K, Producer<V>>, report only Map<K, V>.
    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component(
                "Map<String, Object> objects();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Object> uses more than one @MapKey annotation type");
            });

    // If there's Map<K, Provider<V>> and Map<K, Producer<V>>, report only Map<K, Provider<V>>.
    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component(
                "Map<String, Provider<Object>> objectProviders();",
                "Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Provider<Object>> uses more than one @MapKey annotation type");
            });

    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component("Map<String, Object> objects();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Object> uses more than one @MapKey annotation type");
            });

    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component("Map<String, Provider<Object>> objectProviders();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Provider<Object>> uses more than one @MapKey annotation type");
            });

    CompilerTests.daggerCompiler(
            module,
            stringKeyTwoFile,
            component("Producer<Map<String, Producer<Object>>> objectProducers();"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,Producer<Object>> uses more than one @MapKey annotation type");
            });
  }

  private static Source component(String... entryPoints) {
    return CompilerTests.javaSource(
        "test.TestComponent",
        ImmutableList.<String>builder()
            .add(
                "package test;",
                "",
                "import dagger.Component;",
                "import dagger.producers.Producer;",
                "import java.util.Map;",
                "import javax.inject.Provider;",
                "",
                "@Component(modules = {MapModule.class})",
                "interface TestComponent {")
            .add(entryPoints)
            .add("}")
            .build());
  }
}
