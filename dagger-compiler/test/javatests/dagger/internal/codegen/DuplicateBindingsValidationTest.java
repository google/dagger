/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.TestUtils.message;
import static org.junit.Assume.assumeFalse;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateBindingsValidationTest {

  @Parameters(name = "fullBindingGraphValidation={0}")
  public static ImmutableList<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{false}, {true}});
  }

  private final boolean fullBindingGraphValidation;

  public DuplicateBindingsValidationTest(boolean fullBindingGraphValidation) {
    this.fullBindingGraphValidation = fullBindingGraphValidation;
  }

  @Test public void duplicateExplicitBindings_ProvidesAndComponentProvision() {
    assumeFalse(fullBindingGraphValidation);

    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  interface B {}",
            "",
            "  @Module",
            "  static class AModule {",
            "    @Provides String provideString() { return \"\"; }",
            "    @Provides A provideA(String s) { return new A() {}; }",
            "  }",
            "",
            "  @Component(modules = AModule.class)",
            "  interface Parent {",
            "    A getA();",
            "  }",
            "",
            "  @Module",
            "  static class BModule {",
            "    @Provides B provideB(A a) { return new B() {}; }",
            "  }",
            "",
            "  @Component(dependencies = Parent.class, modules = { BModule.class, AModule.class})",
            "  interface Child {",
            "    B getB();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      message(
                          "Outer.A is bound multiple times:",
                          "    @Provides Outer.A Outer.AModule.provideA(String)",
                          "    Outer.A Outer.Parent.getA()"))
                  .onSource(component)
                  .onLineContaining("interface Child");
            });
  }

  @Test public void duplicateExplicitBindings_TwoProvidesMethods() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static class B {",
            "    @Inject B(A a) {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module2 {",
            "    @Provides String provideString() { return \"\"; }",
            "    @Provides A provideA2(String s) { return new A() {}; }",
            "  }",
            "",
            "  @Module(includes = { Module1.class, Module2.class})",
            "  abstract static class Module3 {}",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "    B getB();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              // The duplicate bindngs are also requested from B, but we don't want to report them
              // again.
              subject.hasErrorCount(fullBindingGraphValidation ? 2 : 1);

              subject.hasErrorContaining(
                      message(
                          "Outer.A is bound multiple times:",
                          "    @Provides Outer.A Outer.Module1.provideA1()",
                          "    @Provides Outer.A Outer.Module2.provideA2(String)"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");

              if (fullBindingGraphValidation) {
                subject.hasErrorContaining(
                        message(
                            "Outer.A is bound multiple times:",
                            "    @Provides Outer.A Outer.Module1.provideA1()",
                            "    @Provides Outer.A Outer.Module2.provideA2(String)"))
                    .onSource(component)
                    .onLineContaining("class Module3");
              }
            });
  }

  @Test
  public void duplicateExplicitBindings_ProvidesVsBinds() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static final class B implements A {",
            "    @Inject B() {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static abstract class Module2 {",
            "    @Binds abstract A bindA2(B b);",
            "  }",
            "",
            "  @Module(includes = { Module1.class, Module2.class})",
            "  abstract static class Module3 {}",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Outer.A is bound multiple times:",
                      "    @Provides Outer.A Outer.Module1.provideA1()",
                      "    @Binds Outer.A Outer.Module2.bindA2(Outer.B)");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class Module3");
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              }
            });
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitSets() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "import javax.inject.Qualifier;",
            "",
            "final class Outer {",
            "  @Qualifier @interface SomeQualifier {}",
            "",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Provides @IntoSet static String stringSetElement() { return \"\"; }",
            "",
            "    @Binds",
            "    @IntoSet abstract String bindStringSetElement(@SomeQualifier String value);",
            "",
            "    @Provides @SomeQualifier",
            "    static String provideSomeQualifiedString() { return \"\"; }",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
            "  }",
            "",
            "  @Module(includes = { TestModule1.class, TestModule2.class})",
            "  abstract static class TestModule3 {}",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Set<String> getStringSet();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Set<String> has incompatible bindings or declarations:",
                      "    Set bindings and declarations:",
                      "        @Binds @IntoSet String "
                          + "Outer.TestModule1.bindStringSetElement(@Outer.SomeQualifier String)",
                      "        @Provides @IntoSet String "
                          + "Outer.TestModule1.stringSetElement()",
                      "    Unique bindings and declarations:",
                      "        @Provides Set<String> Outer.TestModule2.stringSet()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class TestModule3");
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              }
            });
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitMaps() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.HashMap;",
            "import java.util.Map;",
            "import javax.inject.Qualifier;",
            "",
            "final class Outer {",
            "  @Qualifier @interface SomeQualifier {}",
            "",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Provides @IntoMap",
            "    @StringKey(\"foo\")",
            "    static String stringMapEntry() { return \"\"; }",
            "",
            "    @Binds @IntoMap @StringKey(\"bar\")",
            "    abstract String bindStringMapEntry(@SomeQualifier String value);",
            "",
            "    @Provides @SomeQualifier",
            "    static String provideSomeQualifiedString() { return \"\"; }",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Map<String, String> stringMap() {",
            "      return new HashMap<String, String>();",
            "    }",
            "  }",
            "",
            "  @Module(includes = { TestModule1.class, TestModule2.class})",
            "  abstract static class TestModule3 {}",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Map<String, String> getStringMap();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Map<String,String> has incompatible bindings or declarations:",
                      "    Map bindings and declarations:",
                      "        @Binds @IntoMap @StringKey(\"bar\") String"
                          + " Outer.TestModule1.bindStringMapEntry(@Outer.SomeQualifier String)",
                      "        @Provides @IntoMap @StringKey(\"foo\") String"
                          + " Outer.TestModule1.stringMapEntry()",
                      "    Unique bindings and declarations:",
                      "        @Provides Map<String,String> Outer.TestModule2.stringMap()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class TestModule3");
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              }
            });
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Set() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "",
            "final class Outer {",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Multibinds abstract Set<String> stringSet();",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
            "  }",
            "",
            "  @Module(includes = { TestModule1.class, TestModule2.class})",
            "  abstract static class TestModule3 {}",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Set<String> getStringSet();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Set<String> has incompatible bindings or declarations:",
                      "    Set bindings and declarations:",
                      "        @Multibinds Set<String> Outer.TestModule1.stringSet()",
                      "    Unique bindings and declarations:",
                      "        @Provides Set<String> Outer.TestModule2.stringSet()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class TestModule3");
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              }
            });
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Map() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.HashMap;",
            "import java.util.Map;",
            "",
            "final class Outer {",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Multibinds abstract Map<String, String> stringMap();",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Map<String, String> stringMap() {",
            "      return new HashMap<String, String>();",
            "    }",
            "  }",
            "",
            "  @Module(includes = { TestModule1.class, TestModule2.class})",
            "  abstract static class TestModule3 {}",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Map<String, String> getStringMap();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Map<String,String> has incompatible bindings or declarations:",
                      "    Map bindings and declarations:",
                      "        @Multibinds Map<String,String> Outer.TestModule1.stringMap()",
                      "    Unique bindings and declarations:",
                      "        @Provides Map<String,String> Outer.TestModule2.stringMap()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class TestModule3");
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("interface TestComponent");
              }
            });
  }

  @Test public void duplicateBindings_TruncateAfterLimit() {
    Source component =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  @Module",
            "  static class Module01 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module02 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module03 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module04 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module05 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module06 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module07 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module08 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module09 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module10 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module11 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module12 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module(includes = {",
            "    Module01.class,",
            "    Module02.class,",
            "    Module03.class,",
            "    Module04.class,",
            "    Module05.class,",
            "    Module06.class,",
            "    Module07.class,",
            "    Module08.class,",
            "    Module09.class,",
            "    Module10.class,",
            "    Module11.class,",
            "    Module12.class",
            "  })",
            "  abstract static class Modules {}",
            "",
            "  @Component(modules = {",
            "    Module01.class,",
            "    Module02.class,",
            "    Module03.class,",
            "    Module04.class,",
            "    Module05.class,",
            "    Module06.class,",
            "    Module07.class,",
            "    Module08.class,",
            "    Module09.class,",
            "    Module10.class,",
            "    Module11.class,",
            "    Module12.class",
            "  })",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(fullBindingGraphValidation ? 2 : 1);
              String errorMessage =
                  message(
                      "Outer.A is bound multiple times:",
                      "    @Provides Outer.A Outer.Module01.provideA()",
                      "    @Provides Outer.A Outer.Module02.provideA()",
                      "    @Provides Outer.A Outer.Module03.provideA()",
                      "    @Provides Outer.A Outer.Module04.provideA()",
                      "    @Provides Outer.A Outer.Module05.provideA()",
                      "    @Provides Outer.A Outer.Module06.provideA()",
                      "    @Provides Outer.A Outer.Module07.provideA()",
                      "    @Provides Outer.A Outer.Module08.provideA()",
                      "    @Provides Outer.A Outer.Module09.provideA()",
                      "    @Provides Outer.A Outer.Module10.provideA()",
                      "    and 2 others");

              subject.hasErrorContaining(errorMessage)
                  .onSource(component)
                  .onLineContaining("interface TestComponent");

              if (fullBindingGraphValidation) {
                subject.hasErrorContaining(errorMessage)
                    .onSource(component)
                    .onLineContaining("class Modules");
              }
            });
  }

  @Test
  public void childBindingConflictsWithParent() {
    Source aComponent =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B.Builder b();",
            "",
            "  @Module(subcomponents = B.class)",
            "  static class AModule {",
            "    @Provides static Object abConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    Source bComponent =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    B build();",
            "  }",
            "",
            "  @Module",
            "  static class BModule {",
            "    @Provides static Object abConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(aComponent, bComponent)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Object is bound multiple times:",
                      "    @Provides Object test.A.AModule.abConflict()",
                      "    @Provides Object test.B.BModule.abConflict()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining("test.A.AModule has errors")
                    .onSource(aComponent)
                    .onLineContaining("@Component(");
                subject.hasErrorContaining(errorMessage)
                    .onSource(aComponent)
                    .onLineContaining("class AModule");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(aComponent)
                    .onLineContaining("interface A {");
              }
            });
  }

  @Test
  public void grandchildBindingConflictsWithGrandparent() {
    Source aComponent =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B.Builder b();",
            "",
            "  @Module(subcomponents = B.class)",
            "  static class AModule {",
            "    @Provides static Object acConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    Source bComponent =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface B {",
            "  C.Builder c();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    B build();",
            "  }",
            "}");
    Source cComponent =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    C build();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object acConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(aComponent, bComponent, cComponent)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Object is bound multiple times:",
                      "    @Provides Object test.A.AModule.acConflict()",
                      "    @Provides Object test.C.CModule.acConflict()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining("test.A.AModule has errors")
                    .onSource(aComponent)
                    .onLineContaining("@Component(");
                subject.hasErrorContaining(errorMessage)
                    .onSource(aComponent)
                    .onLineContaining("class AModule");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(aComponent)
                    .onLineContaining("interface A {");
              }
            });
  }

  @Test
  public void grandchildBindingConflictsWithChild() {
    Source aComponent =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface A {",
            "  B b();",
            "}");
    Source bComponent =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  C.Builder c();",
            "",
            "  @Module(subcomponents = C.class)",
            "  static class BModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");
    Source cComponent =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    C build();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(aComponent, bComponent, cComponent)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Object is bound multiple times:",
                      "    @Provides Object test.B.BModule.bcConflict()",
                      "    @Provides Object test.C.CModule.bcConflict()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining("test.B.BModule has errors")
                    .onSource(bComponent)
                    .onLineContaining("@Subcomponent(modules = B.BModule.class)");
                subject.hasErrorContaining(errorMessage)
                    .onSource(bComponent)
                    .onLineContaining("class BModule");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(aComponent)
                    .onLineContaining("interface A {");
              }
            });
  }

  @Test
  public void childProvidesConflictsWithParentInjects() {
    assumeFalse(fullBindingGraphValidation);

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "final class Foo {",
            "  @Inject Foo(Set<String> strings) {}",
            "}");
    Source injected1 =
        CompilerTests.javaSource(
            "test.Injected1",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Component(modules = Injected1.Injected1Module.class)",
            "interface Injected1 {",
            "  Foo foo();",
            "  Injected2 injected2();",
            "",
            "  @Module",
            "  interface Injected1Module {",
            "    @Provides @IntoSet static String string() {",
            "      return \"injected1\";",
            "    }",
            "  }",
            "}");
    Source injected2 =
        CompilerTests.javaSource(
            "test.Injected2",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = Injected2.Injected2Module.class)",
            "interface Injected2 {",
            "  Foo foo();",
            "  Provided1 provided1();",
            "",
            "  @Module",
            "  interface Injected2Module {",
            "    @Provides @IntoSet static String string() {",
            "      return \"injected2\";",
            "    }",
            "  }",
            "}");
    Source provided1 =
        CompilerTests.javaSource(
            "test.Provided1",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = Provided1.Provided1Module.class)",
            "interface Provided1 {",
            "  Foo foo();",
            "  Provided2 provided2();",
            "",
            "  @Module",
            "  static class Provided1Module {",
            "    @Provides static Foo provideFoo(Set<String> strings) {",
            "      return new Foo(strings);",
            "    }",
            "",
            "    @Provides @IntoSet static String string() {",
            "      return \"provided1\";",
            "    }",
            "  }",
            "}");
    Source provided2 =
        CompilerTests.javaSource(
            "test.Provided2",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Subcomponent(modules = Provided2.Provided2Module.class)",
            "interface Provided2 {",
            "  Foo foo();",
            "",
            "  @Module",
            "  static class Provided2Module {",
            "    @Provides @IntoSet static String string() {",
            "      return \"provided2\";",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo, injected1, injected2, provided1, provided2)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      message(
                          "Foo is bound multiple times:",
                          "    @Inject Foo(Set<String>) [Injected1]",
                          "    @Provides Foo Provided1.Provided1Module.provideFoo(Set<String>) "
                              + "[Injected1 → Injected2 → Provided1]"))
                  .onSource(injected1)
                  .onLineContaining("interface Injected1 {");
            });
  }

  @Test
  public void grandchildBindingConflictsWithParentWithNullableViolationAsWarning() {
    Source parentConflictsWithChild =
        CompilerTests.javaSource(
            "test.ParentConflictsWithChild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.annotation.Nullable;",
            "",
            "@Component(modules = ParentConflictsWithChild.ParentModule.class)",
            "interface ParentConflictsWithChild {",
            "  Child.Builder child();",
            "",
            "  @Module(subcomponents = Child.class)",
            "  static class ParentModule {",
            "    @Provides @Nullable static Object nullableParentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child.ChildModule.class)",
            "interface Child {",
            "  Object parentChildConflictThatViolatesNullability();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "",
            "  @Module",
            "  static class ChildModule {",
            "    @Provides static Object nonNullableParentChildConflict() {",
            "      return \"child\";",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parentConflictsWithChild, child)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .put("dagger.nullableValidation", "WARNING")
                .putAll(fullBindingGraphValidationOption())
                .buildOrThrow())
        .compile(
            subject -> {
              String errorMessage =
                  message(
                      "Object is bound multiple times:",
                      "    @Provides Object Child.ChildModule.nonNullableParentChildConflict()",
                      "    @Provides @Nullable Object"
                          + " ParentConflictsWithChild.ParentModule.nullableParentChildConflict()");
              if (fullBindingGraphValidation) {
                subject.hasErrorCount(2);
                subject.hasErrorContaining(errorMessage)
                    .onSource(parentConflictsWithChild)
                    .onLineContaining("class ParentModule");
                subject.hasErrorContaining(
                        "Object is not nullable, but is being provided by @Provides @Nullable "
                            + "Object")
                    .onSource(parentConflictsWithChild)
                    .onLineContaining("class ParentModule");
              } else {
                subject.hasErrorCount(1);
                subject.hasErrorContaining(errorMessage)
                    .onSource(parentConflictsWithChild)
                    .onLineContaining("interface ParentConflictsWithChild");
              }
            });
  }

  private ImmutableMap<String, String> fullBindingGraphValidationOption() {
    return ImmutableMap.of(
        "dagger.fullBindingGraphValidation",
        fullBindingGraphValidation ? "ERROR" : "NONE");
  }

  @Test
  public void reportedInParentAndChild() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child.Builder childBuilder();",
            "  String duplicated();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Provides static String one(Optional<Object> optional) { return \"one\"; }",
            "  @Provides static String two() { return \"two\"; }",
            "  @BindsOptionalOf Object optional();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String duplicated();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Object object() { return \"object\"; }",
            "}");
    CompilerTests.daggerCompiler(parent, parentModule, child, childModule)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("String is bound multiple times")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  // Tests the format of the error for a somewhat complex binding method.
  @Test
  public void formatTest() {
    Source modules =
        CompilerTests.javaSource(
            "test.Modules",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableList;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "interface Modules {",
            "  @interface Foo {",
            "    Class<?> bar();",
            "  }",
            "",
            "  @Module",
            "  interface Module1 {",
            "    @Provides",
            "    @Singleton",
            "    @Foo(bar = String.class)",
            "    static String foo(",
            "        @SuppressWarnings(\"unused\") int a,",
            "        @SuppressWarnings(\"unused\") ImmutableList<Boolean> blah) {",
            "      return \"\";",
            "    }",
            "  }",
            "",
            "  @Module",
            "  interface Module2 {",
            "    @Provides",
            "    @Singleton",
            "    @Foo(bar = String.class)",
            "    static String foo(",
            "        @SuppressWarnings(\"unused\") int a,",
            "        @SuppressWarnings(\"unused\") ImmutableList<Boolean> blah) {",
            "      return \"\";",
            "    }",
            "  }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = {Modules.Module1.class, Modules.Module2.class})",
            "interface TestComponent {",
            "  @Modules.Foo(bar = String.class) String foo();",
            "}");
    CompilerTests.daggerCompiler(modules, component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.format(
                      String.join(
                          "\n",
                          "String is bound multiple times:",
                          "    @Provides @Singleton @Modules.Foo(%1$s) String "
                              + "Modules.Module1.foo(int, ImmutableList<Boolean>)",
                          "    @Provides @Singleton @Modules.Foo(%1$s) String "
                              + "Modules.Module2.foo(int, ImmutableList<Boolean>)"),
                      // TODO(b/241293838): KSP and java should match after this is fixed.
                      CompilerTests.backend(subject) == XProcessingEnv.Backend.KSP
                          ? "bar=String"
                          : "bar = String.class"));
            });
  }
}
