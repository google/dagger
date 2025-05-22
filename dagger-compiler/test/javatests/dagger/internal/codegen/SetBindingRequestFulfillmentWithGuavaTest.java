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

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
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

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public SetBindingRequestFulfillmentWithGuavaTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void setBindings() throws Exception {
    Source emptySetModuleFile =
        CompilerTests.javaSource(
            "test.EmptySetModule",
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
    Source setModuleFile =
        CompilerTests.javaSource(
            "test.SetModule",
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
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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

    CompilerTests.daggerCompiler(emptySetModuleFile, setModuleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void inaccessible() throws Exception {
    Source inaccessible =
        CompilerTests.javaSource(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    Source inaccessible2 =
        CompilerTests.javaSource(
            "other.Inaccessible2",
            "package other;",
            "",
            "class Inaccessible2 {}");
    Source usesInaccessible =
        CompilerTests.javaSource(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Set<Inaccessible> set1, Set<Inaccessible2> set2) {}",
            "}");

    Source module =
        CompilerTests.javaSource(
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
    Source componentFile =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(
            module, inaccessible, inaccessible2, usesInaccessible, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void subcomponentOmitsInheritedBindings() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
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
    Source child =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(parent, parentModule, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerParent"));
            });
  }

  @Test
  public void productionComponents() throws Exception {
    Source emptySetModuleFile =
        CompilerTests.javaSource(
            "test.EmptySetModule",
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
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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

    CompilerTests.daggerCompiler(emptySetModuleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
