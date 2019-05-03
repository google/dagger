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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;
import static org.junit.Assume.assumeFalse;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
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

    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.AModule.provideA(String)",
                "    test.Outer.A test.Outer.Parent.getA()"))
        .inFile(component)
        .onLineContaining("interface Child");
  }

  @Test public void duplicateExplicitBindings_TwoProvidesMethods() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                "    @Provides test.Outer.A test.Outer.Module2.provideA2(String)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");

    if (fullBindingGraphValidation) {
      assertThat(compilation)
          .hadErrorContaining(
              message(
                  "test.Outer.A is bound multiple times:",
                  "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                  "    @Provides test.Outer.A test.Outer.Module2.provideA2(String)"))
          .inFile(component)
          .onLineContaining("class Module3");
    }

    // The duplicate bindngs are also requested from B, but we don't want to report them again.
    assertThat(compilation).hadErrorCount(fullBindingGraphValidation ? 2 : 1);
  }

  @Test
  public void duplicateExplicitBindings_ProvidesVsBinds() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                "    @Binds test.Outer.A test.Outer.Module2.bindA2(test.Outer.B)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");

    if (fullBindingGraphValidation) {
      assertThat(compilation)
          .hadErrorContaining(
              message(
                  "test.Outer.A is bound multiple times:",
                  "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                  "    @Binds test.Outer.A test.Outer.Module2.bindA2(test.Outer.B)"))
          .inFile(component)
          .onLineContaining("class Module3");
    }
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitSets() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<java.lang.String> has incompatible bindings or declarations:",
                "    Set bindings and declarations:",
                "        @Binds @dagger.multibindings.IntoSet String "
                    + "test.Outer.TestModule1.bindStringSetElement(@test.Outer.SomeQualifier "
                    + "String)",
                "        @Provides @dagger.multibindings.IntoSet String "
                    + "test.Outer.TestModule1.stringSetElement()",
                "    Unique bindings and declarations:",
                "        @Provides Set<String> test.Outer.TestModule2.stringSet()"))
        .inFile(component)
        .onLineContaining(
            fullBindingGraphValidation ? "class TestModule3" : "interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitMaps() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
                    + "or declarations:",
                "    Map bindings and declarations:",
                "        @Binds @dagger.multibindings.IntoMap "
                    + "@dagger.multibindings.StringKey(\"bar\") String"
                    + " test.Outer.TestModule1.bindStringMapEntry(@test.Outer.SomeQualifier "
                    + "String)",
                "        @Provides @dagger.multibindings.IntoMap "
                    + "@dagger.multibindings.StringKey(\"foo\") String"
                    + " test.Outer.TestModule1.stringMapEntry()",
                "    Unique bindings and declarations:",
                "        @Provides Map<String,String> test.Outer.TestModule2.stringMap()"))
        .inFile(component)
        .onLineContaining(
            fullBindingGraphValidation ? "class TestModule3" : "interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Set() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<java.lang.String> has incompatible bindings or declarations:",
                "    Set bindings and declarations:",
                "        @dagger.multibindings.Multibinds Set<String> "
                    + "test.Outer.TestModule1.stringSet()",
                "    Unique bindings and declarations:",
                "        @Provides Set<String> test.Outer.TestModule2.stringSet()"))
        .inFile(component)
        .onLineContaining(
            fullBindingGraphValidation ? "class TestModule3" : "interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Map() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
                    + "or declarations:",
                "    Map bindings and declarations:",
                "        @dagger.multibindings.Multibinds Map<String,String> "
                    + "test.Outer.TestModule1.stringMap()",
                "    Unique bindings and declarations:",
                "        @Provides Map<String,String> test.Outer.TestModule2.stringMap()"))
        .inFile(component)
        .onLineContaining(
            fullBindingGraphValidation ? "class TestModule3" : "interface TestComponent");
  }

  @Test public void duplicateBindings_TruncateAfterLimit() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(fullBindingGraphValidationOption()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module01.provideA()",
                "    @Provides test.Outer.A test.Outer.Module02.provideA()",
                "    @Provides test.Outer.A test.Outer.Module03.provideA()",
                "    @Provides test.Outer.A test.Outer.Module04.provideA()",
                "    @Provides test.Outer.A test.Outer.Module05.provideA()",
                "    @Provides test.Outer.A test.Outer.Module06.provideA()",
                "    @Provides test.Outer.A test.Outer.Module07.provideA()",
                "    @Provides test.Outer.A test.Outer.Module08.provideA()",
                "    @Provides test.Outer.A test.Outer.Module09.provideA()",
                "    @Provides test.Outer.A test.Outer.Module10.provideA()",
                "    and 2 others"))
        .inFile(component)
        .onLineContaining(fullBindingGraphValidation ? "class Modules" : "interface TestComponent");
  }

  @Test
  public void childBindingConflictsWithParent() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(fullBindingGraphValidationOption())
            .compile(aComponent, bComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.A.AModule.abConflict()",
                "    @Provides Object test.B.BModule.abConflict()"))
        .inFile(aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class AModule" : "interface A {");
  }

  @Test
  public void grandchildBindingConflictsWithGrandparent() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(fullBindingGraphValidationOption())
            .compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.A.AModule.acConflict()",
                "    @Provides Object test.C.CModule.acConflict()"))
        .inFile(aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class AModule" : "interface A {");
  }

  @Test
  public void grandchildBindingConflictsWithChild() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface A {",
            "  B b();",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(fullBindingGraphValidationOption())
            .compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.B.BModule.bcConflict()",
                "    @Provides Object test.C.CModule.bcConflict()"))
        .inFile(fullBindingGraphValidation ? bComponent : aComponent)
        .onLineContaining(fullBindingGraphValidation ? "class BModule" : "interface A {");
  }

  @Test
  public void childProvidesConflictsWithParentInjects() {
    assumeFalse(fullBindingGraphValidation);

    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "final class Foo {",
            "  @Inject Foo(Set<String> strings) {}",
            "}");
    JavaFileObject injected1 =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject injected2 =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject provided1 =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject provided2 =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(foo, injected1, injected2, provided1, provided2);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            message(
                "test.Foo is bound multiple times:",
                "    @Inject test.Foo(Set<String>) [test.Injected1]",
                "    @Provides test.Foo test.Provided1.Provided1Module.provideFoo(Set<String>) "
                    + "[test.Injected1 → test.Injected2 → test.Provided1]"))
        .inFile(injected1)
        .onLineContaining("interface Injected1 {");
  }

  @Test
  public void grandchildBindingConflictsWithParentWithNullableViolationAsWarning() {
    JavaFileObject parentConflictsWithChild =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.nullableValidation=WARNING", fullBindingGraphValidationOption())
            .compile(parentConflictsWithChild, child);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.Child.ChildModule.nonNullableParentChildConflict()",
                "    @Provides @javax.annotation.Nullable Object"
                    + " test.ParentConflictsWithChild.ParentModule.nullableParentChildConflict()"))
        .inFile(parentConflictsWithChild)
        .onLineContaining(
            fullBindingGraphValidation
                ? "class ParentModule"
                : "interface ParentConflictsWithChild");
  }

  private String fullBindingGraphValidationOption() {
    return "-Adagger.fullBindingGraphValidation=" + (fullBindingGraphValidation ? "ERROR" : "NONE");
  }

  @Test
  public void reportedInParentAndChild() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("java.lang.String is bound multiple times")
        .inFile(parent)
        .onLineContaining("interface Parent");
    assertThat(compilation).hadErrorCount(1);
  }
}
