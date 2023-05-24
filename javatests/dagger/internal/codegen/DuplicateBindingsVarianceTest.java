/*
 * Copyright (C) 2023 The Dagger Authors.
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

import androidx.room.compiler.processing.util.CompilationResultSubject;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateBindingsVarianceTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private static final Joiner NEW_LINES = Joiner.on("\n");

  private final CompilerMode compilerMode;

  public DuplicateBindingsVarianceTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testProvidesUniqueBindingsWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Foo<? extends Bar> fooExtends();",
            "  Foo<Bar> foo();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Provides static Foo<? extends Bar> fooExtends() { return null; }",
            "  @Provides static Foo<Bar> foo() { return null; }",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun fooExtends(): Foo<out Bar>",
            "  fun foo(): Foo<Bar>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides fun fooExtends(): Foo<out Bar> { return object:Foo<Bar> {} }",
            "  @Provides fun foo(): Foo<Bar> { return object:Foo<Bar> {} }",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProvidesMultibindsSetDeclarationsWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Set<Foo<? extends Bar>> setExtends();",
            "  Set<Foo<Bar>> set();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds Set<Foo<? extends Bar>> setExtends();",
            "  @Multibinds Set<Foo<Bar>> set();",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun setExtends(): Set<Foo<out Bar>>",
            "  fun set(): Set<Foo<Bar>>",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds fun setExtends(): Set<Foo<out Bar>>",
            "  @Multibinds fun set(): Set<Foo<Bar>>",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProvidesMultibindsSetContributionsWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Set<Foo<? extends Bar>> setExtends();",
            "  Set<Foo<Bar>> set();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Provides @IntoSet static Foo<? extends Bar> setExtends() { return null; }",
            "  @Provides @IntoSet static Foo<Bar> set() { return null; }",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun setExtends(): Set<Foo<out Bar>>",
            "  fun set(): Set<Foo<Bar>>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides @IntoSet fun setExtends(): Foo<out Bar> { return object:Foo<Bar> {} }",
            "  @Provides @IntoSet fun set(): Foo<Bar> { return object:Foo<Bar> {} }",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProvidesMultibindsMapDeclarationValuesWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Map<String, Foo<? extends Bar>> mapExtends();",
            "  Map<String, Foo<Bar>> map();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds Map<String, Foo<? extends Bar>> mapExtends();",
            "  @Multibinds Map<String, Foo<Bar>> map();",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun mapExtends(): Map<String, Foo<out Bar>>",
            "  fun map(): Map<String, Foo<Bar>>",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds fun mapExtends():Map<String, Foo<out Bar>>",
            "  @Multibinds fun map(): Map<String, Foo<Bar>>",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProvidesMultibindsMapDeclarationKeysWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Map<Foo<? extends Bar>, String> mapExtends();",
            "  Map<Foo<Bar>, String> map();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds Map<Foo<? extends Bar>, String> mapExtends();",
            "  @Multibinds Map<Foo<Bar>, String> map();",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun mapExtends(): Map<Foo<out Bar>, String>",
            "  fun map(): Map<Foo<Bar>, String>",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Multibinds fun mapExtends():Map<Foo<out Bar>, String>",
            "  @Multibinds fun map(): Map<Foo<Bar>, String>",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProvidesOptionalDeclarationWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Optional<Foo<? extends Bar>> fooExtends();",
            "  Optional<Foo<Bar>> foo();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @BindsOptionalOf Foo<? extends Bar> fooExtends();",
            "  @BindsOptionalOf Foo<Bar> foo();",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun fooExtends(): Optional<Foo<out Bar>>",
            "  fun foo(): Optional<Foo<Bar>>",
            "}",
            "@Module",
            "interface MyModule {",
            "  @BindsOptionalOf fun fooExtends(): Foo<out Bar>",
            "  @BindsOptionalOf fun foo(): Foo<Bar>",
            "}"),
        subject -> subject.hasErrorCount(0));
  }

  private void compile(
      String javaComponentClass,
      String kotlinComponentClass,
      Consumer<CompilationResultSubject> onCompilationResult) {
    // Compile with Java sources
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.MyComponent",
                "package test;",
                "",
                "import dagger.BindsOptionalOf;",
                "import dagger.Component;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoSet;",
                "import dagger.multibindings.Multibinds;",
                "import java.util.Map;",
                "import java.util.Optional;",
                "import java.util.Set;",
                "",
                javaComponentClass,
                "",
                "interface Foo<T> {}",
                "",
                "class Bar {}"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(onCompilationResult);

    // Compile with Kotlin sources
    CompilerTests.daggerCompiler(
            CompilerTests.kotlinSource(
                "test.MyComponent.kt",
                "package test",
                "",
                "import dagger.BindsOptionalOf",
                "import dagger.Component",
                "import dagger.Module",
                "import dagger.Provides",
                "import dagger.multibindings.IntoSet",
                "import dagger.multibindings.IntoMap",
                "import dagger.multibindings.Multibinds",
                "import java.util.Optional;",
                "",
                kotlinComponentClass,
                "",
                "interface Foo<T>",
                "",
                "class Bar"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(onCompilationResult);
  }
}
