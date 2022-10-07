/*
 * Copyright (C) 2015 The Dagger Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MembersInjectionTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public MembersInjectionTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void parentClass_noInjectedMembers() throws Exception {
    Source childFile =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class Child extends Parent {",
            "  @Inject Child() {}",
            "}");
    Source parentFile =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "public abstract class Parent {}");

    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Child child();",
            "}");

    CompilerTests.daggerCompiler(childFile, parentFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void parentClass_injectedMembersInSupertype() throws Exception {
    Source childFile =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class Child extends Parent {",
            "  @Inject Child() {}",
            "}");
    Source parentFile =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public abstract class Parent {",
            "  @Inject Dep dep;",
            "}");
    Source depFile =
        CompilerTests.javaSource(
            "test.Dep",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Dep {",
            "  @Inject Dep() {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Child child();",
            "}");

    CompilerTests.daggerCompiler(childFile, parentFile, depFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test public void fieldAndMethodGenerics() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class GenericClass<A, B> {",
            "  @Inject A a;",
            "",
            "  @Inject GenericClass() {}",
            "",
            " @Inject void register(B b) {}",
            "}");

    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_MembersInjector"));
            });
  }

  @Test public void subclassedGenericMembersInjectors() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A() {}",
            "}");
    Source a2 =
        CompilerTests.javaSource(
            "test.A2",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A2 {",
            "  @Inject A2() {}",
            "}");
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Parent<X, Y> {",
            "  @Inject X x;",
            "  @Inject Y y;",
            "  @Inject A2 a2;",
            "",
            "  @Inject Parent() {}",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Child<T> extends Parent<T, A> {",
            "  @Inject A a;",
            "  @Inject T t;",
            "",
            "  @Inject Child() {}",
            "}");
    CompilerTests.daggerCompiler(a, a2, parent, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/Child_MembersInjector"));
            });
  }

  @Test public void fieldInjection() {
    Source file =
        CompilerTests.javaSource(
            "test.FieldInjection",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class FieldInjection {",
            "  @Inject String string;",
            "  @Inject Lazy<String> lazyString;",
            "  @Inject Provider<String> stringProvider;",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/FieldInjection_MembersInjector"));
            });
  }

  @Test
  public void fieldInjectionWithQualifier() {
    Source file =
        CompilerTests.javaSource(
            "test.FieldInjectionWithQualifier",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "class FieldInjectionWithQualifier {",
            "  @Inject @Named(\"A\") String a;",
            "  @Inject @Named(\"B\") String b;",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/FieldInjectionWithQualifier_MembersInjector"));
            });
  }

  @Test public void methodInjection() {
    Source file =
        CompilerTests.javaSource(
            "test.MethodInjection",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class MethodInjection {",
            "  @Inject void noArgs() {}",
            "  @Inject void oneArg(String string) {}",
            "  @Inject void manyArgs(",
            "      String string, Lazy<String> lazyString, Provider<String> stringProvider) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MethodInjection_MembersInjector"));
            });
  }

  @Test
  public void mixedMemberInjection() {
    Source file =
        CompilerTests.javaSource(
            "test.MixedMemberInjection",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class MixedMemberInjection {",
            "  @Inject String string;",
            "  @Inject void setString(String s) {}",
            "  @Inject Object object;",
            "  @Inject void setObject(Object o) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MixedMemberInjection_MembersInjector"));
            });
  }

  @Test public void injectConstructorAndMembersInjection() {
    Source file =
        CompilerTests.javaSource(
            "test.AllInjections",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class AllInjections {",
            "  @Inject String s;",
            "  @Inject AllInjections(String s) {}",
            "  @Inject void s(String s) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/AllInjections_MembersInjector"));
            });
  }

  @Test public void supertypeMembersInjection() {
    Source aFile =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "class A {}");
    Source bFile =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B extends A {",
            "  @Inject String s;",
            "}");
    CompilerTests.daggerCompiler(aFile, bFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/B_MembersInjector"));
            });
  }

  @Test
  public void simpleComponentWithNesting() {
    Source nestedTypesFile =
        CompilerTests.javaSource(
            "test.OuterType",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "",
            "final class OuterType {",
            "  static class A {",
            "    @Inject A() {}",
            "  }",
            "  static class B {",
            "    @Inject A a;",
            "  }",
            "  @Component interface SimpleComponent {",
            "    A a();",
            "    void inject(B b);",
            "  }",
            "}");
    CompilerTests.daggerCompiler(nestedTypesFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/OuterType_B_MembersInjector"));
            });
  }

  @Test
  public void componentWithNestingAndGeneratedType() {
    Source nestedTypesFile =
        CompilerTests.javaSource(
            "test.OuterType",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "",
            "final class OuterType {",
            "  @Inject GeneratedInjectType generated;",
            "  static class A {",
            "    @Inject A() {}",
            "  }",
            "  static class B {",
            "    @Inject A a;",
            "  }",
            "  @Component interface SimpleComponent {",
            "    A a();",
            "    void inject(B b);",
            "  }",
            "}");
    TypeSpec generatedInjectType =
        TypeSpec.classBuilder("GeneratedInjectType")
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addAnnotation(TypeNames.INJECT_JAVAX)
                    .build())
            .build();

    CompilerTests.daggerCompiler(nestedTypesFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", generatedInjectType))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/OuterType_B_MembersInjector"));
            });
  }

  @Test
  public void lowerCaseNamedMembersInjector_forLowerCaseType() {
    Source foo =
        CompilerTests.javaSource(
            "test.foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class foo {",
            "  @Inject String string;",
            "}");
    Source fooModule =
        CompilerTests.javaSource(
            "test.fooModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class fooModule {",
            "  @Provides String string() { return \"foo\"; }",
            "}");
    Source fooComponent =
        CompilerTests.javaSource(
            "test.fooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = fooModule.class)",
            "interface fooComponent {",
            "  void inject(foo target);",
            "}");

    CompilerTests.daggerCompiler(foo, fooModule, fooComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSourceFileWithPath("test/foo_MembersInjector.java");
            });
  }

  @Test
  public void fieldInjectionForShadowedMember() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Parent { ",
            "  @Inject Foo object;",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Child extends Parent { ",
            "  @Inject Bar object;",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C { ",
            "  void inject(Child child);",
            "}");

    CompilerTests.daggerCompiler(foo, bar, parent, child, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/Child_MembersInjector"));
            });
  }

  @Test public void privateNestedClassError() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class InnerClass {",
            "    @Inject int field;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateNestedClassWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class InnerClass {",
            "    @Inject int field;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.privateMemberValidation", "WARNING")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateSuperclassIsOkIfNotInjectedInto() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static class BaseClass {}",
            "",
            "  static final class DerivedClass extends BaseClass {",
            "    @Inject int field;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void rawFrameworkTypeField() {
    Source file =
        CompilerTests.javaSource(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderField {",
            "  @Inject",
            "  Provider fieldWithRawProvider;",
            "}");

    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support injecting raw type: javax.inject.Provider")
                  .onSource(file)
                  .onLineContaining("Provider fieldWithRawProvider");
            });
  }

  @Test
  public void throwExceptionInjectedMethod() {
    Source file =
        CompilerTests.javaSource(
            "test.",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "class SomeClass {",
            "@Inject void inject() throws Exception {}",
            "}");

    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Methods with @Inject may not throw checked exceptions. "
                          + "Please wrap your exceptions in a RuntimeException instead.")
                  .onSource(file)
                  .onLineContaining("throws Exception");
            });
  }

  @Test
  public void rawFrameworkMethodTypeParameter() {
    Source file =
        CompilerTests.javaSource(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderParameter {",
            "  @Inject",
            "  void methodInjection(",
            "      Provider rawProviderParameter) {}",
            "}");

    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support injecting raw type: javax.inject.Provider")
                  .onSource(file)
                  .onLineContaining("Provider rawProviderParameter");
            });
  }

  @Test
  public void rawFrameworkConstructorTypeParameter() {
    Source file =
        CompilerTests.javaSource(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderParameter {",
            "  @Inject",
            "  RawProviderParameter(",
            "      Provider rawProviderParameter) {}",
            "}");

    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support injecting raw type: javax.inject.Provider")
                  .onSource(file)
                  .onLineContaining("Provider rawProviderParameter");
            });
  }

  @Test
  public void injectsPrimitive() throws Exception {
    Source injectedType =
        CompilerTests.javaSource(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectedType {",
            "  @Inject InjectedType() {}",
            "",
            "  @Inject int primitiveInt;",
            "  @Inject Integer boxedInt;",
            "}");

    CompilerTests.daggerCompiler(injectedType)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/InjectedType_MembersInjector"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/InjectedType_Factory"));
            });
  }

  @Test
  public void accessibility() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source inaccessible =
        CompilerTests.javaSource(
            "other.Inaccessible",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Inaccessible {",
            "  @Inject Inaccessible() {}",
            "  @Inject Foo foo;",
            "  @Inject void method(Foo foo) {}",
            "}");
    Source usesInaccessible =
        CompilerTests.javaSource(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Inaccessible inaccessible) {}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.UsesInaccessible;",
            "",
            "@Component",
            "interface TestComponent {",
            "  UsesInaccessible usesInaccessible();",
            "}");

    CompilerTests.daggerCompiler(foo, inaccessible, usesInaccessible, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("other/Inaccessible_MembersInjector"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void accessibleRawType_ofInaccessibleType() throws Exception {
    Source inaccessible =
        CompilerTests.javaSource(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    Source inaccessiblesModule =
        CompilerTests.javaSource(
            "other.InaccessiblesModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "public class InaccessiblesModule {",
            // force Provider initialization
            "  @Provides @Singleton static List<Inaccessible> inaccessibles() {",
            "    return new ArrayList<>();",
            "  }",
            "}");
    Source usesInaccessibles =
        CompilerTests.javaSource(
            "other.UsesInaccessibles",
            "package other;",
            "",
            "import java.util.List;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessibles {",
            "  @Inject UsesInaccessibles() {}",
            "  @Inject List<Inaccessible> inaccessibles;",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import other.UsesInaccessibles;",
            "",
            "@Singleton",
            "@Component(modules = other.InaccessiblesModule.class)",
            "interface TestComponent {",
            "  UsesInaccessibles usesInaccessibles();",
            "}");

    CompilerTests.daggerCompiler(inaccessible, inaccessiblesModule, usesInaccessibles, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void publicSupertypeHiddenSubtype() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source supertype =
        CompilerTests.javaSource(
            "other.Supertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Supertype<T> {",
            "  @Inject T t;",
            "}");
    Source subtype =
        CompilerTests.javaSource(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Subtype extends Supertype<Foo> {",
            "  @Inject Subtype() {}",
            "}");
    Source injectsSubtype =
        CompilerTests.javaSource(
            "other.InjectsSubtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class InjectsSubtype {",
            "  @Inject InjectsSubtype(Subtype s) {}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  other.InjectsSubtype injectsSubtype();",
            "}");

    CompilerTests.daggerCompiler(foo, supertype, subtype, injectsSubtype, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // Shows that we shouldn't create a members injector for a type that doesn't have
  // @Inject fields or @Inject constructor even if it extends and is extended by types that do.
  @Test
  public void middleClassNoFieldInjection() throws Exception {
    Source classA =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject String valueA;",
            "}");
    Source classB =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "class B extends C {",
            "}");
    Source classC =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class C { ",
            "  @Inject String valueC;",
            "}");

    CompilerTests.daggerCompiler(classA, classB, classC)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/A_MembersInjector"));
              subject.generatedSource(goldenFileRule.goldenSource("test/C_MembersInjector"));

              try {
                subject.generatedSourceFileWithPath("test/B_MembersInjector");
                // Can't throw an assertion error since it would be caught.
                throw new IllegalStateException("Test generated a B_MembersInjector");
              } catch (AssertionError expected) {}
            });
  }

  // Shows that we do generate a MembersInjector for a type that has an @Inject
  // constructor and that extends a type with @Inject fields, even if it has no local field
  // injection sites
  // TODO(erichang): Are these even used anymore?
  @Test
  public void testConstructorInjectedFieldInjection() throws Exception {
    Source classA =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A extends B {",
            "  @Inject A() {}",
            "}");
    Source classB =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B { ",
            "  @Inject String valueB;",
            "}");

    CompilerTests.daggerCompiler(classA, classB)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/A_MembersInjector"));
              subject.generatedSource(goldenFileRule.goldenSource("test/B_MembersInjector"));
            });
  }

  // Regression test for https://github.com/google/dagger/issues/3143
  @Test
  public void testMembersInjectionBindingExistsInParentComponent() throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = MyComponentModule.class)",
            "public interface MyComponent {",
            "  void inject(Bar bar);",
            "",
            "  MySubcomponent subcomponent();",
            "}");

    Source subcomponent =
        CompilerTests.javaSource(
            "test.MySubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = MySubcomponentModule.class)",
            "interface MySubcomponent {",
            "  Foo foo();",
            "}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Set<String> multibindingStrings;",
            "  @Inject Bar() {}",
            "}");

    Source componentModule =
        CompilerTests.javaSource(
            "test.MyComponentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface MyComponentModule {",
            "  @Provides",
            "  @IntoSet",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "}");

    Source subcomponentModule =
        CompilerTests.javaSource(
            "test.MySubcomponentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface MySubcomponentModule {",
            "  @Provides",
            "  @IntoSet",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "}");

    CompilerTests.daggerCompiler(
            component, subcomponent, foo, bar, componentModule, subcomponentModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // Check that the injectBar() method is not shared across components.
              // We avoid sharing them in general because they may be different (e.g. in this case
              // we inject multibindings that are different across components).
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });

  }

  // Test that if both a MembersInjectionBinding and ProvisionBinding both exist in the same
  // component they share the same inject methods rather than generating their own.
  @Test
  public void testMembersInjectionBindingSharesInjectMethodsWithProvisionBinding()
      throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "public interface MyComponent {",
            "  Foo foo();",
            "",
            "  void inject(Foo foo);",
            "}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Bar bar;",
            "  @Inject Foo() {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    CompilerTests.daggerCompiler(component, foo, bar)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }
}
