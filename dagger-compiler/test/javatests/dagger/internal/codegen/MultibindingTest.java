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

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultibindingTest {
  @Test
  public void multibindingContributedWithKotlinProperty_compilesSucessfully() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Set<String> getStrs();",
            "}");
    Source moduleSrc =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoSet",
            "",
            "@Module",
            "object TestModule {",
            "@get:IntoSet",
            "@get:Provides",
            "val helloString: String",
            "  get() = \"hello\"",
            "}");

    CompilerTests.daggerCompiler(component, moduleSrc).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void providesWithTwoMultibindingAnnotations_failsToCompile() {
    Source module =
        CompilerTests.javaSource(
            "test.MultibindingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "class MultibindingModule {",
            "  @Provides",
            "  @IntoSet",
            "  @IntoMap",
            "  Integer provideInt() { ",
            "    return 1;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(module)
        .compile(
            subject -> {
              subject.hasErrorCount(3);
              subject.hasErrorContaining(
                      "@Provides methods cannot have more than one multibinding annotation")
                  .onSource(module)
                  .onLine(11);
              subject.hasErrorContaining(
                      "@Provides methods cannot have more than one multibinding annotation")
                  .onSource(module)
                  .onLine(12);
              subject.hasErrorContaining("@Provides methods of type map must declare a map key")
                  .onSource(module)
                  .onLine(13);
            });
  }

  @Test
  public void appliedOnInvalidMethods_failsToCompile() {
    Source someType =
        CompilerTests.javaSource(
            "test.SomeType",
            "package test;",
            "",
            "import java.util.Set;",
            "import java.util.Map;",
            "import dagger.Component;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.multibindings.IntoMap;",
            "",
            "interface SomeType {",
            "  @IntoSet Set<Integer> ints();",
            "  @ElementsIntoSet Set<Double> doubles();",
            "  @IntoMap Map<Integer, Double> map();",
            "}");

    CompilerTests.daggerCompiler(someType)
        .compile(
            subject -> {
              subject.hasErrorCount(3);
              String error =
                  "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods";
              subject.hasErrorContaining(error).onSource(someType).onLineContaining("ints();");
              subject.hasErrorContaining(error).onSource(someType).onLineContaining("doubles();");
              subject.hasErrorContaining(error).onSource(someType).onLineContaining("map();");
            });
  }

  @Test
  public void concreteBindingForMultibindingAlias() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Collections;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides",
            "  Map<String, Provider<String>> mapOfStringToProviderOfString() {",
            "    return Collections.emptyMap();",
            "  }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Map<String, String> mapOfStringToString();",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Map<String,String> cannot be provided without an @Provides-annotated method")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void produceConcreteSet_andRequestSetOfProduced() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "",
            "@ProducerModule",
            "class TestModule {",
            "  @Produces",
            "  Set<String> setOfString() {",
            "    return Collections.emptySet();",
            "  }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.BindsInstance;",
            "import dagger.producers.Produced;",
            "import dagger.producers.Production;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
            "import java.util.Set;",
            "",
            "@ProductionComponent(modules = TestModule.class)",
            "interface TestComponent {",
            "  ListenableFuture<Set<Produced<String>>> setOfProduced();",
            "",
            "  @ProductionComponent.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder executor(@Production Executor executor);",
            "    TestComponent build();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Set<Produced<String>> cannot be provided without an @Provides- or "
                          + "@Produces-annotated method")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void provideExplicitSetInParent_AndMultibindingContributionInChild() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Set<String> set();",
            "  Child child();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  Set<String> set() {",
            "    return new HashSet();",
            "  }",
            "}");

    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Set<String> set();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides",
            "  @IntoSet",
            "  String setContribution() {",
            "    return new String();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parent, parentModule, child, childModule)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("incompatible bindings or declarations")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingMapWithKotlinSource() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Usage @Inject constructor(map: Map<String, MyInterface>)");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoMap",
            "import dagger.multibindings.StringKey",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"key\")",
            "  fun provideMyInterface(): MyInterface = TODO()",
            "}");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myInterface, usage)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Map<String,? extends MyInterface> cannot be provided");
            });
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingMapWithOutVarianceKotlinSource_succeeds() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Usage @Inject constructor(",
            "  map: Map<String, @JvmSuppressWildcards MyGenericInterface<out MyInterface>>",
            ")");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoMap",
            "import dagger.multibindings.StringKey",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"key\")",
            "  fun provideMyInterface(): MyGenericInterface<out MyInterface> = TODO()",
            "}");
    Source myGenericInterface =
        CompilerTests.kotlinSource(
            "test.MyGenericInterface.kt",
            "package test",
            "",
            "interface MyGenericInterface<T>");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myGenericInterface, myInterface, usage)
        .compile(subject -> subject.hasErrorCount(0));
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingMapWithJvmWildcardsKotlinSource_succeeds() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Usage @Inject constructor(",
            "  map: Map<String,@JvmSuppressWildcards MyGenericInterface<@JvmWildcard MyInterface>>",
            ")");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoMap",
            "import dagger.multibindings.StringKey",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"key\")",
            "  fun provideMyInterface(): MyGenericInterface<@JvmWildcard MyInterface> = TODO()",
            "}");
    Source myGenericInterface =
        CompilerTests.kotlinSource(
            "test.MyGenericInterface.kt",
            "package test",
            "",
            "interface MyGenericInterface<out T>");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myGenericInterface, myInterface, usage)
        .compile(subject -> subject.hasErrorCount(0));
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingMapProviderWithKotlinSource() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "import javax.inject.Provider",
            "",
            "class Usage @Inject constructor(map: Map<String, Provider<MyInterface>>)");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoMap",
            "import dagger.multibindings.StringKey",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"key\")",
            "  fun provideMyInterface(): MyInterface = TODO()",
            "}");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myInterface, usage)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Map<String,? extends Provider<MyInterface>> cannot be provided");
            });
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingSetWithKotlinSource() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Usage @Inject constructor(set: Set<MyInterface>)");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoSet",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoSet",
            "  fun provideMyInterface(): MyInterface = TODO()",
            "}");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myInterface, usage)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Set<? extends MyInterface> cannot be provided");
            });
  }

  // Regression test for b/352142595.
  @Test
  public void testMultibindingSetProviderWithKotlinSource() {
    Source parent =
        CompilerTests.kotlinSource(
            "test.Parent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ParentModule::class])",
            "interface Parent {",
            "  fun usage(): Usage",
            "}");
    Source usage =
        CompilerTests.kotlinSource(
            "test.Usage.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "import javax.inject.Provider",
            "",
            "class Usage @Inject constructor(set: Set<Provider<MyInterface>>)");
    Source parentModule =
        CompilerTests.kotlinSource(
            "test.ParentModule.kt",
            "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")", // Required to use TODO()
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.IntoSet",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  @IntoSet",
            "  fun provideMyInterface(): MyInterface = TODO()",
            "}");
    Source myInterface =
        CompilerTests.kotlinSource(
            "test.MyInterface.kt",
            "package test",
            "",
            "interface MyInterface");

    CompilerTests.daggerCompiler(parent, parentModule, myInterface, usage)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Set<? extends Provider<MyInterface>> cannot be provided");
            });
  }
}
