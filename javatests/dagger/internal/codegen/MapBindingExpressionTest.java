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

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.CLASS_PATH_WITHOUT_GUAVA_OPTION;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapBindingExpressionTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MapBindingExpressionTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapBindings() {
    JavaFileObject mapModuleFile = JavaFileObjects.forSourceLines("test.MapModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntKey;",
        "import dagger.multibindings.IntoMap;",
        "import dagger.multibindings.LongKey;",
        "import dagger.multibindings.Multibinds;",
        "import java.util.Map;",
        "",
        "@Module",
        "interface MapModule {",
        "  @Multibinds Map<String, String> stringMap();",
        "  @Provides @IntoMap @IntKey(0) static int provideInt() { return 0; }",
        "  @Provides @IntoMap @LongKey(0) static long provideLong0() { return 0; }",
        "  @Provides @IntoMap @LongKey(1) static long provideLong1() { return 1; }",
        "  @Provides @IntoMap @LongKey(2) static long provideLong2() { return 2; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = MapModule.class)",
        "interface TestComponent {",
        "  Map<String, String> strings();",
        "  Map<String, Provider<String>> providerStrings();",
        "",
        "  Map<Integer, Integer> ints();",
        "  Map<Integer, Provider<Integer>> providerInts();",
        "  Map<Long, Long> longs();",
        "  Map<Long, Provider<Long>> providerLongs();",
        "}");
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.internal.MapBuilder;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private volatile Provider<Integer> provideIntProvider;",
                "  private volatile Provider<Long> provideLong0Provider;",
                "  private volatile Provider<Long> provideLong1Provider;",
                "  private volatile Provider<Long> provideLong2Provider;",
                "",
                "  private Provider<Integer> getProvideIntProvider() {",
                "    Object local = provideIntProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      provideIntProvider = (Provider<Integer>) local;",
                "    }",
                "    return (Provider<Integer>) local;",
                "  }",
                "",
                "  private Provider<Long> getProvideLong0Provider() {",
                "    Object local = provideLong0Provider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(1);",
                "      provideLong0Provider = (Provider<Long>) local;",
                "    }",
                "    return (Provider<Long>) local;",
                "  }",
                "",
                "  private Provider<Long> getProvideLong1Provider() {",
                "    Object local = provideLong1Provider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(2);",
                "      provideLong1Provider = (Provider<Long>) local;",
                "    }",
                "    return (Provider<Long>) local;",
                "  }",
                "",
                "  private Provider<Long> getProvideLong2Provider() {",
                "    Object local = provideLong2Provider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(3);",
                "      provideLong2Provider = (Provider<Long>) local;",
                "    }",
                "    return (Provider<Long>) local;",
                "  }")
            .addLines(
                "  @Override",
                "  public Map<String, String> strings() {",
                "    return Collections.<String, String>emptyMap();",
                "  }",
                "",
                "  @Override",
                "  public Map<String, Provider<String>> providerStrings() {",
                "    return Collections.<String, Provider<String>>emptyMap();",
                "  }",
                "",
                "  @Override",
                "  public Map<Integer, Integer> ints() {",
                "    return Collections.<Integer, Integer>singletonMap(0, MapModule.provideInt());",
                "  }",
                "",
                "  @Override",
                "  public Map<Integer, Provider<Integer>> providerInts() {",
                "    return Collections.<Integer, Provider<Integer>>singletonMap(")
            .addLinesIn(
                DEFAULT_MODE, //
                "        0, MapModule_ProvideIntFactory.create());")
            .addLinesIn(
                FAST_INIT_MODE,
                "        0, getProvideIntProvider());")
            .addLines(
                "  }",
                "",
                "  @Override",
                "  public Map<Long, Long> longs() {",
                "    return MapBuilder.<Long, Long>newMapBuilder(3)",
                "        .put(0L, MapModule.provideLong0())",
                "        .put(1L, MapModule.provideLong1())",
                "        .put(2L, MapModule.provideLong2())",
                "        .build();",
                "  }",
                "",
                "  @Override",
                "  public Map<Long, Provider<Long>> providerLongs() {",
                "    return MapBuilder.<Long, Provider<Long>>newMapBuilder(3)")
            .addLinesIn(
                DEFAULT_MODE,
                "        .put(0L, MapModule_ProvideLong0Factory.create())",
                "        .put(1L, MapModule_ProvideLong1Factory.create())",
                "        .put(2L, MapModule_ProvideLong2Factory.create())")
            .addLinesIn(
                FAST_INIT_MODE,
                "        .put(0L, getProvideLong0Provider())",
                "        .put(1L, getProvideLong1Provider())",
                "        .put(2L, getProvideLong2Provider())")
            .addLines( //
                "        .build();", "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    private final int id;",
                "",
                "    SwitchingProvider(int id) {",
                "      this.id = id;",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: return (T) (Integer) MapModule.provideInt();",
                "        case 1: return (T) (Long) MapModule.provideLong0();",
                "        case 2: return (T) (Long) MapModule.provideLong1();",
                "        case 3: return (T) (Long) MapModule.provideLong2();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}")
            .build();
    Compilation compilation = daggerCompilerWithoutGuava().compile(mapModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void inaccessible() {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import java.util.Map;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Map<Integer, Inaccessible> map) {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Map;",
            "",
            "@Module",
            "public abstract class TestModule {",
            "  @Multibinds abstract Map<Integer, Inaccessible> ints();",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "import other.TestModule;",
            "import other.UsesInaccessible;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  UsesInaccessible usesInaccessible();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newInstance(",
            "        (Map) Collections.emptyMap());",
            "  }",
            "}");
    Compilation compilation =
        daggerCompilerWithoutGuava().compile(module, inaccessible, usesInaccessible, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void subcomponentOmitsInheritedBindings() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides @IntoMap @StringKey(\"parent key\") Object parentKeyObject() {",
            "    return \"parent value\";",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import java.util.Map;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Map<String, Object> objectMap();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "  private final ParentModule parentModule;",
            "",
            "  private final class ChildImpl implements Child {",
            "    @Override",
            "    public Map<String, Object> objectMap() {",
            "      return Collections.<String, Object>singletonMap(",
            "          \"parent key\",",
            "          ParentModule_ParentKeyObjectFactory.parentKeyObject(",
            "              DaggerParent.this.parentModule));",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompilerWithoutGuava().compile(parent, parentModule, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsElementsIn(generatedComponent);
  }

  private Compiler daggerCompilerWithoutGuava() {
    return daggerCompiler()
        .withOptions(compilerMode.javacopts().append(CLASS_PATH_WITHOUT_GUAVA_OPTION));
  }
}
