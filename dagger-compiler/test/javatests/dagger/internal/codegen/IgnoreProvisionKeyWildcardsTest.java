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
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IgnoreProvisionKeyWildcardsTest {
  enum SourceKind { JAVA, KOTLIN }

  @Parameters(name = "sourceKind={0}, isIgnoreProvisionKeyWildcardsEnabled={1}")
  public static ImmutableList<Object[]> parameters() {
    return ImmutableList.of(
        new Object[] {SourceKind.JAVA, false},
        new Object[] {SourceKind.KOTLIN, false},
        new Object[] {SourceKind.JAVA, true},
        new Object[] {SourceKind.KOTLIN, true}
    );
  }

  private static final Joiner NEW_LINES = Joiner.on("\n");
  private static final Joiner NEW_LINES_FOR_ERROR_MSG = Joiner.on("\n      ");

  private final boolean isIgnoreProvisionKeyWildcardsEnabled;
  private final SourceKind sourceKind;
  private final ImmutableMap<String, String> processingOptions;

  public IgnoreProvisionKeyWildcardsTest(
      SourceKind sourceKind,
      boolean isIgnoreProvisionKeyWildcardsEnabled) {
    this.sourceKind = sourceKind;
    this.isIgnoreProvisionKeyWildcardsEnabled = isIgnoreProvisionKeyWildcardsEnabled;
    processingOptions =
        isIgnoreProvisionKeyWildcardsEnabled
            ? ImmutableMap.of("dagger.ignoreProvisionKeyWildcards", "enabled")
            : ImmutableMap.of("dagger.ignoreProvisionKeyWildcards", "disabled");
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
            "  @Provides fun fooExtends(): Foo<out Bar> = TODO()",
            "  @Provides fun foo(): Foo<Bar> = TODO()",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Foo<? extends Bar> is bound multiple times:",
                    "        @Provides Foo<Bar> MyModule.foo()",
                    "        @Provides Foo<? extends Bar> MyModule.fooExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesUniqueBindingsWithMatchingWildcardArguments() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Map<Foo<? extends Bar>, Foo<? extends Bar>> mapFooExtendsBarFooExtendsBar();",
            "  Map<Foo<? extends Bar>, Foo<Bar>> mapFooExtendsBarFooBar();",
            "  Map<Foo<Bar>, Foo<? extends Bar>> mapFooBarFooExtendsBar();",
            "  Map<Foo<Bar>, Foo<Bar>> mapFooBarFooBar();",
            "}",
            "@Module",
            "class MyModule {",
            "  @Provides",
            "  Map<Foo<? extends Bar>, Foo<? extends Bar>> mapFooExtendsBarFooExtendsBar() {",
            "    return null; ",
            "  }",
            "  @Provides",
            "  Map<Foo<? extends Bar>, Foo<Bar>> mapFooExtendsBarFooBar() {",
            "    return null;",
            "  }",
            "  @Provides",
            "  Map<Foo<Bar>, Foo<? extends Bar>> mapFooBarFooExtendsBar() {",
            "    return null;",
            "  }",
            "  @Provides",
            "  Map<Foo<Bar>, Foo<Bar>> mapFooBarFooBar() {",
            "    return null;",
            "  }",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun mapFooExtendsBarFooExtendsBar(): Map<Foo<out Bar>, Foo<out Bar>>",
            "  fun mapFooExtendsBarFooBar(): Map<Foo<out Bar>, Foo<Bar>>",
            "  fun mapFooBarFooExtendsBar(): Map<Foo<Bar>, Foo<out Bar>>",
            "  fun mapFooBarFooBar(): Map<Foo<Bar>, Foo<Bar>>",
            "}",
            "@Module",
            "class MyModule {",
            "  @Provides",
            "  fun mapFooExtendsBarFooExtendsBar(): Map<Foo<out Bar>, Foo<out Bar>> = TODO()",
            "  @Provides",
            "  fun mapFooExtendsBarFooBar(): Map<Foo<out Bar>, Foo<Bar>> = TODO()",
            "  @Provides",
            "  fun mapFooBarFooExtendsBar(): Map<Foo<Bar>, Foo<out Bar>> = TODO()",
            "  @Provides",
            "  fun mapFooBarFooBar(): Map<Foo<Bar>, Foo<Bar>> = TODO()",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Map<Foo<? extends Bar>,Foo<? extends Bar>> is bound multiple times:",
                    "        @Provides Map<Foo<Bar>,Foo<Bar>> MyModule.mapFooBarFooBar()",
                    "        @Provides Map<Foo<Bar>,Foo<? extends Bar>> "
                        + "MyModule.mapFooBarFooExtendsBar()",
                    "        @Provides Map<Foo<? extends Bar>,Foo<Bar>> "
                        + "MyModule.mapFooExtendsBarFooBar()",
                    "        @Provides Map<Foo<? extends Bar>,Foo<? extends Bar>> "
                        + "MyModule.mapFooExtendsBarFooExtendsBar()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
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
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Set<Foo<? extends Bar>> has incompatible bindings or declarations:",
                    "    Set bindings and declarations:",
                    "        @Multibinds Set<Foo<Bar>> MyModule.set()",
                    "        @Multibinds Set<Foo<? extends Bar>> MyModule.setExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesMultibindsSetContributionsWithDifferentTypeVariances() {
    compile(
        /* javaComponentClass= */ NEW_LINES.join(
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
        /* kotlinComponentClass= */ NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun setExtends(): Set<Foo<out Bar>>",
            "  fun set(): Set<Foo<Bar>>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides @IntoSet fun setExtends(): Foo<out Bar> = TODO()",
            "  @Provides @IntoSet fun set(): Foo<Bar> = TODO()",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Set<Foo<? extends Bar>> has incompatible bindings or declarations:",
                    "    Set bindings and declarations:",
                    "        @Provides @IntoSet Foo<Bar> MyModule.set()",
                    "        @Provides @IntoSet Foo<? extends Bar> MyModule.setExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesMultibindsSetContributionAndMultibindsWithDifferentVariances() {
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
            "  @Multibinds Set<Foo<Bar>> mulitbindSet();",
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
            "  @Multibinds abstract fun mulitbindSet(): Set<Foo<Bar>>",
            "",
            "  companion object {",
            "    @Provides @IntoSet fun setExtends(): Foo<out Bar> = TODO()",
            "  }",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                String.format(
                    NEW_LINES_FOR_ERROR_MSG.join(
                        "Set<Foo<? extends Bar>> has incompatible bindings or declarations:",
                        "    Set bindings and declarations:",
                        "        @Multibinds Set<Foo<Bar>> MyModule.mulitbindSet()",
                        "        @Provides @IntoSet Foo<? extends Bar> %s.setExtends()",
                        "    in component: [MyComponent]"),
                    sourceKind == SourceKind.KOTLIN ? "MyModule.Companion" : "MyModule"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesIntoSetAndElementsIntoSetContributionsWithDifferentVariances() {
    compile(
        /* javaComponentClass= */ NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Set<Foo<? extends Bar>> setExtends();",
            "  Set<Foo<Bar>> set();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Provides @IntoSet static Foo<? extends Bar> setExtends() { return null; }",
            "",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<Foo<Bar>> set() { return null; }",
            "}"),
        /* kotlinComponentClass= */ NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun setExtends(): Set<Foo<out Bar>>",
            "  fun set(): Set<Foo<Bar>>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides @IntoSet fun setExtends(): Foo<out Bar> = TODO()",
            "  @Provides @ElementsIntoSet fun set(): Set<Foo<Bar>> = TODO()",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Set<Foo<? extends Bar>> has incompatible bindings or declarations:",
                    "    Set bindings and declarations:",
                    "        @Provides @ElementsIntoSet Set<Foo<Bar>> MyModule.set()",
                    "        @Provides @IntoSet Foo<? extends Bar> MyModule.setExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesMultibindsSetContributionsWithSameTypeVariances() {
    compile(
        /* javaComponentClass = */
        NEW_LINES.join(
            "@Component(modules = MyModule.class)",
            "interface MyComponent {",
            "  Set<Foo<Bar>> set();",
            "}",
            "@Module",
            "interface MyModule {",
            "  @Provides @IntoSet static Foo<Bar> set1() { return null; }",
            "  @Provides @IntoSet static Foo<Bar> set2() { return null; }",
            "  @Provides @ElementsIntoSet static Set<Foo<Bar>> set3() { return null; }",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun set(): Set<Foo<Bar>>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides @IntoSet fun set1(): Foo<Bar> = TODO()",
            "  @Provides @IntoSet fun set2(): Foo<Bar> = TODO()",
            "  @Provides @ElementsIntoSet fun set3(): Set<Foo<Bar>> = TODO()",
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
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Map<String,Foo<? extends Bar>> has incompatible bindings or declarations:",
                    "    Map bindings and declarations:",
                    "        @Multibinds Map<String,Foo<Bar>> MyModule.map()",
                    "        @Multibinds Map<String,Foo<? extends Bar>> MyModule.mapExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
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
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Map<Foo<? extends Bar>,String> has incompatible bindings or declarations:",
                    "    Map bindings and declarations:",
                    "        @Multibinds Map<Foo<Bar>,String> MyModule.map()",
                    "        @Multibinds Map<Foo<? extends Bar>,String> MyModule.mapExtends()",
                    "    in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  @Test
  public void testProvidesMultibindsMapContributionsWithDifferentTypeVariances() {
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
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"fooExtends\")",
            "  static Foo<? extends Bar> fooExtends() { return null; }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"foo\")",
            "  static Foo<Bar> foo() { return null; }",
            "}"),
        /* kotlinComponentClass = */
        NEW_LINES.join(
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun mapExtends(): Map<String, Foo<out Bar>>",
            "  fun map(): Map<String, Foo<Bar>>",
            "}",
            "@Module",
            "object MyModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"fooExtends\")",
            "  fun fooExtends(): Foo<out Bar> = TODO()",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"foo\")",
            "  fun foo(): Foo<Bar> = TODO()",
            "}"),
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorContaining(
                String.format(
                    NEW_LINES_FOR_ERROR_MSG.join(
                        "Map<String,Foo<? extends Bar>> has incompatible bindings or declarations:",
                        "    Map bindings and declarations:",
                        "        %s Foo<Bar> MyModule.foo()",
                        "        %s Foo<? extends Bar> MyModule.fooExtends()",
                        "    in component: [MyComponent]"),
                    "@Provides @IntoMap @StringKey(\"foo\")",
                    "@Provides @IntoMap @StringKey(\"fooExtends\")"));
          } else {
            subject.hasErrorCount(0);
          }
        });
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
        subject -> {
          if (isIgnoreProvisionKeyWildcardsEnabled) {
            subject.hasErrorCount(1);
            subject.hasErrorContaining(
                NEW_LINES_FOR_ERROR_MSG.join(
                    "Optional<Foo<? extends Bar>> is bound multiple times:",
                    "    @BindsOptionalOf Foo<Bar> MyModule.foo()",
                    "    @BindsOptionalOf Foo<? extends Bar> MyModule.fooExtends()",
                    "in component: [MyComponent]"));
          } else {
            subject.hasErrorCount(0);
          }
        });
  }

  private void compile(
      String javaComponentClass,
      String kotlinComponentClass,
      Consumer<CompilationResultSubject> onCompilationResult) {
    compileInternal(
        javaComponentClass,
        kotlinComponentClass,
        subject -> {
          if (!isIgnoreProvisionKeyWildcardsEnabled) {
            if (CompilerTests.backend(subject) ==
                androidx.room.compiler.processing.XProcessingEnv.Backend.KSP) {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "When using KSP, you must also enable the 'dagger.ignoreProvisionKeyWildcards'");
              return;
            }
          }
          onCompilationResult.accept(subject);
        });
  }

  private void compileInternal(
      String javaComponentClass,
      String kotlinComponentClass,
      Consumer<CompilationResultSubject> onCompilationResult) {
    if (sourceKind == SourceKind.JAVA) {
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
                  "import dagger.multibindings.ElementsIntoSet;",
                  "import dagger.multibindings.IntoSet;",
                  "import dagger.multibindings.IntoMap;",
                  "import dagger.multibindings.Multibinds;",
                  "import dagger.multibindings.StringKey;",
                  "import java.util.Map;",
                  "import java.util.Optional;",
                  "import java.util.Set;",
                  "import javax.inject.Inject;",
                  "import javax.inject.Provider;",
                  "",
                  javaComponentClass,
                  "",
                  "interface Foo<T> {}",
                  "",
                  "class Bar {}"))
          .withProcessingOptions(processingOptions)
          .compile(onCompilationResult);
    }

    if (sourceKind == SourceKind.KOTLIN) {
      // Compile with Kotlin sources
      CompilerTests.daggerCompiler(
              CompilerTests.kotlinSource(
                  "test.MyComponent.kt",
                  // TODO(bcorso): See if there's a better way to fix the following error.
                  //
                  //   Error: Cannot inline bytecode built with JVM target 11 into bytecode that is
                  //          being built with JVM target 1.8
                  "@file:Suppress(\"INLINE_FROM_HIGHER_PLATFORM\")",
                  "package test",
                  "",
                  "import dagger.BindsOptionalOf",
                  "import dagger.Component",
                  "import dagger.Module",
                  "import dagger.Provides",
                  "import dagger.multibindings.ElementsIntoSet",
                  "import dagger.multibindings.IntoSet",
                  "import dagger.multibindings.IntoMap",
                  "import dagger.multibindings.Multibinds",
                  "import dagger.multibindings.StringKey",
                  "import java.util.Optional;",
                  "import javax.inject.Inject",
                  "import javax.inject.Provider",
                  "",
                  kotlinComponentClass,
                  "",
                  "interface Foo<T>",
                  "",
                  "class Bar"))
          .withProcessingOptions(processingOptions)
          .compile(onCompilationResult);
    }
  }
}
