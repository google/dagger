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
import static dagger.internal.codegen.Compilers.CLASS_PATH_WITHOUT_GUAVA_OPTION;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.CodeBlock;
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

  public static final CodeBlock NPE_FROM_PROVIDES =
      CodeBlocks.stringLiteral(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);

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
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.MapBuilder;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
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
            "    return Collections.<Integer, Provider<Integer>>singletonMap(",
            "        0, MapModule_ProvideIntFactory.create());",
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
            "    return MapBuilder.<Long, Provider<Long>>newMapBuilder(3)",
            "        .put(0L, MapModule_ProvideLong0Factory.create())",
            "        .put(1L, MapModule_ProvideLong1Factory.create())",
            "        .put(2L, MapModule_ProvideLong2Factory.create())",
            "        .build();",
            "  }",
            "}");
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
            "public final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newUsesInaccessible(",
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
            "public final class DaggerParent implements Parent {",
            "  private ParentModule parentModule;",
            "",
            "  private final class ChildImpl implements Child {",
            "    @Override",
            "    public Map<String, Object> objectMap() {",
            "      return Collections.<String, Object>singletonMap(",
            "          \"parent key\",",
            "          ParentModule_ParentKeyObjectFactory.proxyParentKeyObject(",
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
