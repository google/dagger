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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultibindingTest {

  @Test
  public void providesWithTwoMultibindingAnnotations_failsToCompile() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
            "  @Provides @IntoSet @IntoMap Integer provideInt() { ",
            "    return 1;",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Provides methods cannot have more than one multibinding annotation")
        .inFile(module)
        .onLine(10);
  }

  @Test
  public void appliedOnInvalidMethods_failsToCompile() {
    JavaFileObject someType =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(someType);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods")
        .inFile(someType)
        .onLineContaining("ints();");
    assertThat(compilation)
        .hadErrorContaining(
            "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods")
        .inFile(someType)
        .onLineContaining("doubles();");
    assertThat(compilation)
        .hadErrorContaining(
            "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods")
        .inFile(someType)
        .onLineContaining("map();");
  }

  @Test
  public void concreteBindingForMultibindingAlias() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.util.Map<java.lang.String,java.lang.String> "
                + "cannot be provided without an @Provides-annotated method")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void produceConcreteSet_andRequestSetOfProduced() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.util.Set<dagger.producers.Produced<java.lang.String>> "
                + "cannot be provided without an @Provides- or @Produces-annotated method")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void provideExplicitSetInParent_AndMultibindingContributionInChild() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("incompatible bindings or declarations")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }
}
