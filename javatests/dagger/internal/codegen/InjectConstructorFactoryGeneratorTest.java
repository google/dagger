/*
 * Copyright (C) 2014 The Dagger Authors.
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
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
// TODO(gak): add tests for generation in the default package.
public final class InjectConstructorFactoryGeneratorTest {
  private static final Source QUALIFIER_A =
        CompilerTests.javaSource("test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");
  private static final Source QUALIFIER_B =
        CompilerTests.javaSource("test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");
  private static final Source SCOPE_A =
        CompilerTests.javaSource("test.ScopeA",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");
  private static final Source SCOPE_B =
        CompilerTests.javaSource("test.ScopeB",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  @Test public void injectOnPrivateConstructor() {
    Source file =
        CompilerTests.javaSource(
            "test.PrivateConstructor",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrivateConstructor {",
            "  @Inject private PrivateConstructor() {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support injection into private constructors")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void injectConstructorOnInnerClass() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class OuterClass {",
            "  class InnerClass {",
            "    @Inject InnerClass() {}",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@Inject constructors are invalid on inner classes. "
                          + "Did you mean to make the class static?")
                  .onSource(file)
                  .onLine(7);
            });
  }

  @Test public void injectConstructorOnAbstractClass() {
    Source file =
        CompilerTests.javaSource(
            "test.AbstractClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "abstract class AbstractClass {",
            "  @Inject AbstractClass() {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@Inject is nonsense on the constructor of an abstract class")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void injectConstructorOnGenericClass() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class GenericClass<T> {",
            "  @Inject GenericClass(T t) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void injectConstructorOnInlineValueClass() {
    Source file =
        CompilerTests.kotlinSource(
            "test.InlineValueClass.kt",
            "package test",
            "",
            "import javax.inject.Inject;",
            "",
            "@JvmInline value class InlineValueClass",
            "  @Inject constructor(private val v: Int)");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@Inject constructors on Kotlin inline value classes is not supported")
                  .onSource(file)
                  .onLine(6);
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
            "  @Inject void register(B b) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void genericClassWithNoDependencies() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class GenericClass<T> {",
            "  @Inject GenericClass() {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void twoGenericTypes() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class GenericClass<A, B> {",
            "  @Inject GenericClass(A a, B b) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void boundedGenerics() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import java.util.List;",
            "",
            "class GenericClass<A extends Number & Comparable<A>,",
            "    B extends List<? extends String>,",
            "    C extends List<? super String>> {",
            "  @Inject GenericClass(A a, B b, C c) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void multipleSameTypesWithGenericsAndQualifiersAndLazies() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "import dagger.Lazy;",
            "",
            "class GenericClass<A, B> {",
            "  @Inject GenericClass(A a, A a2, Provider<A> pa, @QualifierA A qa, Lazy<A> la, ",
            "                       String s, String s2, Provider<String> ps, ",
            "                       @QualifierA String qs, Lazy<String> ls,",
            "                       B b, B b2, Provider<B> pb, @QualifierA B qb, Lazy<B> lb) {}",
            "}");
    CompilerTests.daggerCompiler(file, QUALIFIER_A)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/GenericClass_Factory"));
            });
  }

  @Test public void multipleInjectConstructors() {
    Source file =
        CompilerTests.javaSource(
            "test.TooManyInjectConstructors",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class TooManyInjectConstructors {",
            "  @Inject TooManyInjectConstructors() {}",
            "  TooManyInjectConstructors(int i) {}",
            "  @Inject TooManyInjectConstructors(String s) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Type test.TooManyInjectConstructors may only contain one injected "
                          + "constructor. Found: ["
                          + "@Inject test.TooManyInjectConstructors(), "
                          + "@Inject test.TooManyInjectConstructors(String)"
                          + "]")
                  .onSource(file)
                  .onLine(5);
            });
  }

  @Test public void multipleQualifiersOnInjectConstructorParameter() {
    Source file =
        CompilerTests.javaSource(
            "test.MultipleQualifierConstructorParam",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class MultipleQualifierConstructorParam {",
            "  @Inject MultipleQualifierConstructorParam(",
            "      @QualifierA",
            "      @QualifierB",
            "      String s) {}",
            "}");
    CompilerTests.daggerCompiler(file, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(7);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void injectConstructorOnClassWithMultipleScopes() {
    Source file =
        CompilerTests.javaSource(
            "test.MultipleScopeClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "@ScopeA",
            "@ScopeB",
            "class MultipleScopeClass {",
            "  @Inject MultipleScopeClass() {}",
            "}");
    CompilerTests.daggerCompiler(file, SCOPE_A, SCOPE_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("A single binding may not declare more than one @Scope")
                  .onSource(file)
                  .onLine(5);
              subject.hasErrorContaining("A single binding may not declare more than one @Scope")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void injectConstructorWithQualifier() {
    Source file =
        CompilerTests.javaSource(
            "test.MultipleScopeClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class MultipleScopeClass {",
            "  @Inject",
            "  @QualifierA",
            "  @QualifierB",
            "  MultipleScopeClass() {}",
            "}");
    CompilerTests.daggerCompiler(file, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "@Qualifier annotations are not allowed on @Inject constructors")
                  .onSource(file)
                  .onLine(7);
              subject.hasErrorContaining(
                      "@Qualifier annotations are not allowed on @Inject constructors")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void injectConstructorWithCheckedExceptionsError() {
    Source file =
        CompilerTests.javaSource(
            "test.CheckedExceptionClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class CheckedExceptionClass {",
            "  @Inject CheckedExceptionClass() throws Exception {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support checked exceptions on @Inject constructors")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void injectConstructorWithCheckedExceptionsWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.CheckedExceptionClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class CheckedExceptionClass {",
            "  @Inject CheckedExceptionClass() throws Exception {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.privateMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining(
                      "Dagger does not support checked exceptions on @Inject constructors")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateInjectClassError() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class InnerClass {",
            "    @Inject InnerClass() {}",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(7);
            });
  }

  @Test public void privateInjectClassWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class InnerClass {",
            "    @Inject InnerClass() {}",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.privateMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(7);
            });
  }

  @Test public void nestedInPrivateInjectClassError() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class MiddleClass {",
            "    static final class InnerClass {",
            "      @Inject InnerClass() {}",
            "    }",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void nestedInPrivateInjectClassWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.OuterClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class OuterClass {",
            "  private static final class MiddleClass {",
            "    static final class InnerClass {",
            "      @Inject InnerClass() {}",
            "    }",
            "  }",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.privateMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining("Dagger does not support injection into private classes")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void finalInjectField() {
    Source file =
        CompilerTests.javaSource(
            "test.FinalInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FinalInjectField {",
            "  @Inject final String s;",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@Inject fields may not be final")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateInjectFieldError() {
    Source file =
        CompilerTests.javaSource(
            "test.PrivateInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrivateInjectField {",
            "  @Inject private String s;",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private fields")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateInjectFieldWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.PrivateInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrivateInjectField {",
            "  @Inject private String s;",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.privateMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // TODO: Verify warning message when supported
              // subject.hasWarningCount(1);
            });
  }

  @Test public void staticInjectFieldError() {
    Source file =
        CompilerTests.javaSource(
            "test.StaticInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class StaticInjectField {",
            "  @Inject static String s;",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into static fields")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void staticInjectFieldWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.StaticInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class StaticInjectField {",
            "  @Inject static String s;",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.staticMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // TODO: Verify warning message when supported
              // subject.hasWarningCount(1);
            });
  }

  @Test public void multipleQualifiersOnField() {
    Source file =
        CompilerTests.javaSource(
            "test.MultipleQualifierInjectField",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class MultipleQualifierInjectField {",
            "  @Inject",
            "  @QualifierA",
            "  @QualifierB",
            "  String s;",
            "}");
    CompilerTests.daggerCompiler(file, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(7);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void abstractInjectMethod() {
    Source file =
        CompilerTests.javaSource(
            "test.AbstractInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "abstract class AbstractInjectMethod {",
            "  @Inject abstract void method();",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Methods with @Inject may not be abstract")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateInjectMethodError() {
    Source file =
        CompilerTests.javaSource(
            "test.PrivateInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrivateInjectMethod {",
            "  @Inject private void method(){}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private methods")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void privateInjectMethodWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.PrivateInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrivateInjectMethod {",
            "  @Inject private void method(){}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.privateMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // TODO: Verify warning message when supported
              // subject.hasWarningCount(1);
            });
  }

  @Test public void staticInjectMethodError() {
    Source file =
        CompilerTests.javaSource(
            "test.StaticInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class StaticInjectMethod {",
            "  @Inject static void method(){}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into static methods")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void staticInjectMethodWarning() {
    Source file =
        CompilerTests.javaSource(
            "test.StaticInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class StaticInjectMethod {",
            "  @Inject static void method(){}",
            "}");
    CompilerTests.daggerCompiler(file)
        .withProcessingOptions(ImmutableMap.of("dagger.staticMemberValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // TODO: Verify warning message when supported
              // subject.hasWarningCount(1);
            });
  }

  @Test public void genericInjectMethod() {
    Source file =
        CompilerTests.javaSource(
            "test.GenericInjectMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class AbstractInjectMethod {",
            "  @Inject <T> void method();",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Methods with @Inject may not declare type parameters")
                  .onSource(file)
                  .onLine(6);
            });
  }

  @Test public void multipleQualifiersOnInjectMethodParameter() {
    Source file =
        CompilerTests.javaSource(
            "test.MultipleQualifierMethodParam",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class MultipleQualifierMethodParam {",
            "  @Inject void method(",
            "      @QualifierA",
            "      @QualifierB",
            "      String s) {}",
            "}");
    CompilerTests.daggerCompiler(file, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(7);
              subject.hasErrorContaining(
                      "A single dependency request may not use more than one @Qualifier")
                  .onSource(file)
                  .onLine(8);
            });
  }

  @Test public void injectConstructorDependsOnProduced() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Produced;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Produced<String> str) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Produced may only be injected in @Produces methods");
            });
  }

  @Test public void injectConstructorDependsOnProducer() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Producer<String> str) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Producer may only be injected in @Produces methods");
            });
  }

  @Test public void injectFieldDependsOnProduced() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Produced;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject Produced<String> str;",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Produced may only be injected in @Produces methods");
            });
  }

  @Test public void injectFieldDependsOnProducer() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject Producer<String> str;",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Producer may only be injected in @Produces methods");
            });
  }

  @Test public void injectMethodDependsOnProduced() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Produced;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject void inject(Produced<String> str) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Produced may only be injected in @Produces methods");
            });
  }

  @Test public void injectMethodDependsOnProducer() {
    Source file =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject void inject(Producer<String> str) {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Producer may only be injected in @Produces methods");
            });
  }


  @Test public void injectConstructor() {
    Source file =
        CompilerTests.javaSource("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(String s) {}",
        "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/InjectConstructor_Factory"));
            });
  }

  @Test public void injectConstructorAndMembersInjection() {
    Source file =
        CompilerTests.javaSource("test.AllInjections",
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
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/AllInjections_Factory"));
            });
  }

  @Test
  public void wildcardDependency() {
    Source file =
        CompilerTests.javaSource("test.InjectConstructor",
        "package test;",
        "",
        "import java.util.List;",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(List<?> objects) {}",
        "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/InjectConstructor_Factory"));
            });
  }

  @Test
  public void basicNameCollision() {
    Source factoryFile =
        CompilerTests.javaSource("other.pkg.Factory",
        "package other.pkg;",
        "",
        "public class Factory {}");
    Source file =
        CompilerTests.javaSource("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Factory;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Factory factory) {}",
        "}");
    CompilerTests.daggerCompiler(factoryFile, file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/InjectConstructor_Factory"));
            });
  }

  @Test
  public void nestedNameCollision() {
    Source factoryFile =
        CompilerTests.javaSource("other.pkg.Outer",
        "package other.pkg;",
        "",
        "public class Outer {",
        "  public class Factory {}",
        "}");
    Source file =
        CompilerTests.javaSource("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Outer;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Outer.Factory factory) {}",
        "}");
    CompilerTests.daggerCompiler(factoryFile, file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/InjectConstructor_Factory"));
            });
  }

  @Test
  public void samePackageNameCollision() {
    Source samePackageInterface =
        CompilerTests.javaSource(
            "test.CommonName",
            "package test;",
            "",
            "public interface CommonName {}");
    Source differentPackageInterface =
        CompilerTests.javaSource(
            "other.pkg.CommonName",
            "package other.pkg;",
            "",
            "public interface CommonName {}");
    Source file =
        CompilerTests.javaSource(
            "test.InjectConstructor",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectConstructor implements CommonName {",
            "  @Inject InjectConstructor("
                + "other.pkg.CommonName otherPackage, CommonName samePackage) {}",
            "}");
    CompilerTests.daggerCompiler(samePackageInterface, differentPackageInterface, file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/InjectConstructor_Factory"));
            });
  }

  @Test
  public void noDeps() {
    Source file =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/SimpleType_Factory"));
            });
  }

  @Test public void simpleComponentWithNesting() {
    Source file =
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
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/OuterType_A_Factory"));
            });
  }

  @Test
  public void testScopedMetadata() throws Exception {
    Source file =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class ScopedBinding {",
            "  @Inject",
            "  ScopedBinding() {}",
            "}");
    CompilerTests.daggerCompiler(file)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/ScopedBinding_Factory"));
            });
  }

  @Test
  public void testScopedMetadataWithCustomScope() throws Exception {
    Source customScope =
        CompilerTests.javaSource(
            "test.CustomScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface CustomScope {",
            "  String value();",
            "}");

    Source customAnnotation =
        CompilerTests.javaSource(
            "test.CustomAnnotation",
            "package test;",
            "",
            "@interface CustomAnnotation {",
            "  String value();",
            "}");

    Source scopedBinding =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@CustomAnnotation(\"someValue\")",
            "@CustomScope(\"someOtherValue\")",
            "class ScopedBinding {",
            "  @Inject",
            "  ScopedBinding() {}",
            "}");
    CompilerTests.daggerCompiler(scopedBinding, customScope, customAnnotation)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/ScopedBinding_Factory"));
            });
  }

  @Test
  public void testQualifierMetadata() throws Exception {
    Source someBinding =
        CompilerTests.javaSource(
            "test.SomeBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@NonQualifier",
            "@MisplacedQualifier",
            "class SomeBinding {",
            "  @NonQualifier @FieldQualifier @Inject String injectField;",
            "  @NonQualifier @MisplacedQualifier String nonDaggerField;",
            "",
            "  @NonQualifier",
            "  @Inject",
            "  SomeBinding(@NonQualifier @ConstructorParameterQualifier Double d) {}",
            "",
            "  @NonQualifier",
            "  @MisplacedQualifier",
            "  SomeBinding(@NonQualifier @MisplacedQualifier Double d, int i) {}",
            "",
            "  @NonQualifier",
            "  @MisplacedQualifier",
            "  @Inject",
            "  void injectMethod(@NonQualifier @MethodParameterQualifier Float f) {}",
            "",
            "  @NonQualifier",
            "  @MisplacedQualifier",
            "  void nonDaggerMethod(@NonQualifier @MisplacedQualifier Float f) {}",
            "}");
    Source fieldQualifier =
        CompilerTests.javaSource(
            "test.FieldQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FieldQualifier {}");
    Source constructorParameterQualifier =
        CompilerTests.javaSource(
            "test.ConstructorParameterQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface ConstructorParameterQualifier {}");
    Source methodParameterQualifier =
        CompilerTests.javaSource(
            "test.MethodParameterQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface MethodParameterQualifier {}");
    Source misplacedQualifier =
        CompilerTests.javaSource(
            "test.MisplacedQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface MisplacedQualifier {}");
    Source nonQualifier =
        CompilerTests.javaSource(
            "test.NonQualifier",
            "package test;",
            "",
            "@interface NonQualifier {}");
    CompilerTests.daggerCompiler(
            someBinding,
            fieldQualifier,
            constructorParameterQualifier,
            methodParameterQualifier,
            misplacedQualifier,
            nonQualifier)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/SomeBinding_Factory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/SomeBinding_MembersInjector"));
            });
  }

  @Test
  public void testComplexQualifierMetadata() throws Exception {
    Source someBinding =
        CompilerTests.javaSource(
            "test.SomeBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Inject;",
            "",
            "class SomeBinding {",
            "  @QualifierWithValue(1) @Inject String injectField;",
            "",
            "  @Inject",
            "  SomeBinding(",
            "      @pkg1.SameNameQualifier String str1,",
            "      @pkg2.SameNameQualifier String str2) {}",
            "",
            "  @Inject",
            "  void injectMethod(@test.Outer.NestedQualifier Float f) {}",
            "}");
    Source qualifierWithValue =
        CompilerTests.javaSource(
            "test.QualifierWithValue",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface QualifierWithValue {",
            "  int value();",
            "}");
    Source pkg1SameNameQualifier =
        CompilerTests.javaSource(
            "pkg1.SameNameQualifier",
            "package pkg1;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "public @interface SameNameQualifier {}");
    Source pkg2SameNameQualifier =
        CompilerTests.javaSource(
            "pkg2.SameNameQualifier",
            "package pkg2;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "public @interface SameNameQualifier {}");
    Source nestedQualifier =
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "interface Outer {",
            "  @Qualifier",
            "  @interface NestedQualifier {}",
            "}");
    CompilerTests.daggerCompiler(
            someBinding,
            qualifierWithValue,
            pkg1SameNameQualifier,
            pkg2SameNameQualifier,
            nestedQualifier)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/SomeBinding_Factory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/SomeBinding_MembersInjector"));
            });
  }

  @Test
  public void testBaseClassQualifierMetadata() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "class Foo extends FooBase {",
            "  @FooFieldQualifier @Inject String injectField;",
            "",
            "  @Inject",
            "  Foo(@FooConstructorQualifier int i) { super(i); }",
            "",
            "  @Inject",
            "  void injectMethod(@FooMethodQualifier float f) {}",
            "}");
    Source fooFieldQualifier =
        CompilerTests.javaSource(
            "test.FooFieldQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooFieldQualifier {}");
    Source fooConstructorQualifier =
        CompilerTests.javaSource(
            "test.FooConstructorQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooConstructorQualifier {}");
    Source fooMethodQualifier =
        CompilerTests.javaSource(
            "test.FooMethodQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooMethodQualifier {}");
    Source fooBase =
        CompilerTests.javaSource(
            "test.FooBase",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "class FooBase {",
            "  @FooBaseFieldQualifier @Inject String injectField;",
            "",
            "  @Inject",
            "  FooBase(@FooBaseConstructorQualifier int i) {}",
            "",
            "  @Inject",
            "  void injectMethod(@FooBaseMethodQualifier float f) {}",
            "}");
    Source fooBaseFieldQualifier =
        CompilerTests.javaSource(
            "test.FooBaseFieldQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooBaseFieldQualifier {}");
    Source fooBaseConstructorQualifier =
        CompilerTests.javaSource(
            "test.FooBaseConstructorQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooBaseConstructorQualifier {}");
    Source fooBaseMethodQualifier =
        CompilerTests.javaSource(
            "test.FooBaseMethodQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface FooBaseMethodQualifier {}");
    CompilerTests.daggerCompiler(
            foo,
            fooBase,
            fooFieldQualifier,
            fooConstructorQualifier,
            fooMethodQualifier,
            fooBaseFieldQualifier,
            fooBaseConstructorQualifier,
            fooBaseMethodQualifier)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/Foo_Factory"));
              subject.generatedSource(goldenFileRule.goldenSource("test/Foo_MembersInjector"));
              subject.generatedSource(goldenFileRule.goldenSource("test/FooBase_Factory"));
              subject.generatedSource(goldenFileRule.goldenSource("test/FooBase_MembersInjector"));
            });
  }
}
