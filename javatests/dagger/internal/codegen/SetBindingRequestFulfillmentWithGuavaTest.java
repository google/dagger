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
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetBindingRequestFulfillmentWithGuavaTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public SetBindingRequestFulfillmentWithGuavaTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void setBindings() {
    JavaFileObject emptySetModuleFile = JavaFileObjects.forSourceLines("test.EmptySetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.multibindings.Multibinds;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "",
        "@Module",
        "abstract class EmptySetModule {",
        "  @Multibinds abstract Set<Object> objects();",
        "",
        "  @Provides @ElementsIntoSet",
        "  static Set<String> emptySet() { ",
        "    return Collections.emptySet();",
        "  }",
        "  @Provides @ElementsIntoSet",
        "  static Set<Integer> onlyContributionIsElementsIntoSet() { ",
        "    return Collections.emptySet();",
        "  }",
        "}");
    JavaFileObject setModuleFile = JavaFileObjects.forSourceLines("test.SetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "",
        "@Module",
        "final class SetModule {",
        "  @Provides @IntoSet static String string() { return \"\"; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {EmptySetModule.class, SetModule.class})",
        "interface TestComponent {",
        "  Set<String> strings();",
        "  Set<Object> objects();",
        "  Set<Integer> onlyContributionIsElementsIntoSet();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Set<String> strings() {",
            "    return ImmutableSet.<String>builderWithExpectedSize(2)",
            "        .addAll(EmptySetModule_EmptySetFactory.emptySet())",
            "        .add(SetModule_StringFactory.string())",
            "        .build();",
            "  }",
            "",
            "  @Override",
            "  public Set<Object> objects() {",
            "    return ImmutableSet.<Object>of();",
            "  }",
            "",
            "  @Override",
            "  public Set<Integer> onlyContributionIsElementsIntoSet() {",
            "    return ImmutableSet.<Integer>copyOf(",
            "        EmptySetModule_OnlyContributionIsElementsIntoSetFactory",
            "            .onlyContributionIsElementsIntoSet());",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(emptySetModuleFile, setModuleFile, componentFile);
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
    JavaFileObject inaccessible2 =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible2",
            "package other;",
            "",
            "class Inaccessible2 {}");
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Set<Inaccessible> set1, Set<Inaccessible2> set2) {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "",
            "@Module",
            "public abstract class TestModule {",
            "  @Multibinds abstract Set<Inaccessible> objects();",
            "",
            "  @Provides @ElementsIntoSet",
            "  static Set<Inaccessible2> emptySet() { ",
            "    return Collections.emptySet();",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
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
            "import com.google.common.collect.ImmutableSet;",
            "import other.TestModule_EmptySetFactory;",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private Set getSetOfInaccessible2() {",
            "    return ImmutableSet.copyOf(TestModule_EmptySetFactory.emptySet());",
            "  }",
            "",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newInstance(",
            "        (Set) ImmutableSet.of(),",
            "        (Set) getSetOfInaccessible2());",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(module, inaccessible, inaccessible2, usesInaccessible, componentFile);
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
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides @IntoSet static Object parentObject() {",
            "    return \"parent object\";",
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
            "@Subcomponent",
            "interface Child {",
            "  Set<Object> objectSet();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "  private final class ChildImpl implements Child {",
            "    @Override",
            "    public Set<Object> objectSet() {",
            "      return ImmutableSet.<Object>of(",
            "          ParentModule_ParentObjectFactory.parentObject());",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(parent, parentModule, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void productionComponents() {
    JavaFileObject emptySetModuleFile = JavaFileObjects.forSourceLines("test.EmptySetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "",
        "@Module",
        "abstract class EmptySetModule {",
        "  @Provides @ElementsIntoSet",
        "  static Set<String> emptySet() { ",
        "    return Collections.emptySet();",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "import java.util.Set;",
        "",
        "@ProductionComponent(modules = EmptySetModule.class)",
        "interface TestComponent {",
        "  ListenableFuture<Set<String>> strings();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.internal.CancellationListener;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent, "
                + "CancellationListener {",
            "  private DaggerTestComponent() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  private Set<String> getSetOfString() {",
            "    return ImmutableSet.<String>copyOf(",
            "        EmptySetModule_EmptySetFactory.emptySet());",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Set<String>> strings() {",
            "    return Futures.immediateFuture(getSetOfString());",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {}",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent();",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(emptySetModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }
}
