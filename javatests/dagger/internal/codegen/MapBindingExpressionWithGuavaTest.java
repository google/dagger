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
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_PROVIDES_METHOD;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapBindingExpressionWithGuavaTest {
  @Test
  public void mapBindings() {
    JavaFileObject mapModuleFile =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
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
    JavaFileObject subcomponentModuleFile =
        JavaFileObjects.forSourceLines(
            "test.SubcomponentMapModule",
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
            "interface SubcomponentMapModule {",
            "  @Provides @IntoMap @LongKey(3) static long provideLong3() { return 3; }",
            "  @Provides @IntoMap @LongKey(4) static long provideLong4() { return 4; }",
            "  @Provides @IntoMap @LongKey(5) static long provideLong5() { return 5; }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
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
            "",
            "  Sub sub();",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = SubcomponentMapModule.class)",
            "interface Sub {",
            "  Map<Long, Long> longs();",
            "  Map<Long, Provider<Long>> providerLongs();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Map<String, String> strings() {",
            "    return ImmutableMap.<String, String>of();",
            "  }",
            "",
            "  @Override",
            "  public Map<String, Provider<String>> providerStrings() {",
            "    return ImmutableMap.<String, Provider<String>>of();",
            "  }",
            "",
            "  @Override",
            "  public Map<Integer, Integer> ints() {",
            "    return ImmutableMap.<Integer, Integer>of(0, MapModule.provideInt());",
            "  }",
            "",
            "  @Override",
            "  public Map<Integer, Provider<Integer>> providerInts() {",
            "    return ImmutableMap.<Integer, Provider<Integer>>of(",
            "        0, MapModule_ProvideIntFactory.create());",
            "  }",
            "",
            "  @Override",
            "  public Map<Long, Long> longs() {",
            "    return ImmutableMap.<Long, Long>of(",
            "      0L, MapModule.provideLong0(),",
            "      1L, MapModule.provideLong1(),",
            "      2L, MapModule.provideLong2());",
            "  }",
            "",
            "  @Override",
            "  public Map<Long, Provider<Long>> providerLongs() {",
            "    return ImmutableMap.<Long, Provider<Long>>of(",
            "      0L, MapModule_ProvideLong0Factory.create(),",
            "      1L, MapModule_ProvideLong1Factory.create(),",
            "      2L, MapModule_ProvideLong2Factory.create());",
            "  }",
            "",
            "  @Override",
            "  public Sub sub() {",
            "    return new SubImpl();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "  private final class SubImpl implements Sub {",
            "    private SubImpl() {}",
            "",
            "    @Override",
            "    public Map<Long, Long> longs() {",
            "      return ImmutableMap.<Long, Long>builder()",
            "          .put(0L, MapModule.provideLong0())",
            "          .put(1L, MapModule.provideLong1())",
            "          .put(2L, MapModule.provideLong2())",
            "          .put(3L, SubcomponentMapModule.provideLong3())",
            "          .put(4L, SubcomponentMapModule.provideLong4())",
            "          .put(5L, SubcomponentMapModule.provideLong5())",
            "          .build();",
            "    }",
            "",
            "    @Override",
            "    public Map<Long, Provider<Long>> providerLongs() {",
            "      return ImmutableMap.<Long, Provider<Long>>builder()",
            "          .put(0L, MapModule_ProvideLong0Factory.create())",
            "          .put(1L, MapModule_ProvideLong1Factory.create())",
            "          .put(2L, MapModule_ProvideLong2Factory.create())",
            "          .put(3L, SubcomponentMapModule_ProvideLong3Factory.create())",
            "          .put(4L, SubcomponentMapModule_ProvideLong4Factory.create())",
            "          .put(5L, SubcomponentMapModule_ProvideLong5Factory.create())",
            "          .build();",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(mapModuleFile, componentFile, subcomponentModuleFile, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void inaccessible() {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible", "package other;", "", "class Inaccessible {}");
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
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newUsesInaccessible((Map) ImmutableMap.of());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(module, inaccessible, usesInaccessible, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
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
            "",
            "@Subcomponent",
            "interface Child {",
            "  Map<String, Object> objectMap();",
            "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerParent implements Parent {",
            "  private ParentModule parentModule;",
            "",
            "  private DaggerParent(Builder builder) {",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Parent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.parentModule = builder.parentModule;",
            "  }",
            "",
            "  @Override",
            "  public Child child() {",
            "    return new ChildImpl();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private ParentModule parentModule;",
            "",
            "    private Builder() {}",
            "",
            "    public Parent build() {",
            "      if (parentModule == null) {",
            "        this.parentModule = new ParentModule();",
            "      }",
            "      return new DaggerParent(this);",
            "    }",
            "",
            "    public Builder parentModule(ParentModule parentModule) {",
            "      this.parentModule = Preconditions.checkNotNull(parentModule);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  private final class ChildImpl implements Child {",
            "    private ChildImpl() {}",
            "",
            "    @Override",
            "    public Map<String, Object> objectMap() {",
            "      return ImmutableMap.<String, Object>of(",
            "          \"parent key\",",
            "          Preconditions.checkNotNull(",
            "              DaggerParent.this.parentModule.parentKeyObject(),",
            "              " + NPE_FROM_PROVIDES_METHOD + ");",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(parent, parentModule, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(expected);
  }

  @Test
  public void productionComponents() {
    JavaFileObject mapModuleFile =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Map;",
            "",
            "@Module",
            "interface MapModule {",
            "  @Multibinds Map<String, String> stringMap();",
            "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "import java.util.Map;",
        "",
        "@ProductionComponent(modules = MapModule.class)",
        "interface TestComponent {",
        "  ListenableFuture<Map<String, String>> stringMap();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Map<String, String>> stringMap() {",
            "    return Futures.<Map<String, String>>immediateFuture(",
            "        ImmutableMap.<String, String>of());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder testComponent_ProductionExecutorModule(",
            "        TestComponent_ProductionExecutorModule",
            "            testComponent_ProductionExecutorModule) {",
            "      Preconditions.checkNotNull(testComponent_ProductionExecutorModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(mapModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }
}
