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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
// TODO(gak): add tests for generation in the default package.
public final class InjectConstructorFactoryGeneratorTest {
  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines("test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");
  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines("test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");
  private static final JavaFileObject SCOPE_A =
      JavaFileObjects.forSourceLines("test.ScopeA",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");
  private static final JavaFileObject SCOPE_B =
      JavaFileObjects.forSourceLines("test.ScopeB",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test public void injectOnPrivateConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateConstructor {",
        "  @Inject private PrivateConstructor() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test public void injectConstructorOnInnerClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class OuterClass {",
        "  class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@Inject constructors are invalid on inner classes. "
                + "Did you mean to make the class static?")
        .inFile(file)
        .onLine(7);
  }

  @Test public void injectConstructorOnAbstractClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AbstractClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "abstract class AbstractClass {",
        "  @Inject AbstractClass() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Inject is nonsense on the constructor of an abstract class")
        .inFile(file)
        .onLine(6);
  }

  @Test public void injectConstructorOnGenericClass() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class GenericClass<T> {",
        "  @Inject GenericClass(T t) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<T> implements Factory<GenericClass<T>> {",
            "  private final Provider<T> tProvider;",
            "",
            "  public GenericClass_Factory(Provider<T> tProvider) {",
            "    this.tProvider = tProvider;",
            "  }",
            "",
            "  @Override",
            "  public GenericClass<T> get() {",
            "    return new GenericClass<T>(tProvider.get());",
            "  }",
            "",
            "  public static <T> GenericClass_Factory<T> create(Provider<T> tProvider) {",
            "    return new GenericClass_Factory<T>(tProvider);",
            "  }",
            "",
            "  public static <T> GenericClass<T> newInstance(T t) {",
            "    return new GenericClass<T>(t);",
            "  }",
            "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void fieldAndMethodGenerics() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
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
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<A, B> implements Factory<GenericClass<A, B>> {",
            "  private final Provider<A> aProvider;",
            "  private final Provider<B> bProvider;",
            "",
            "  public GenericClass_Factory(",
            "      Provider<A> aProvider, Provider<B> bProvider) {",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  @Override",
            "  public GenericClass<A, B> get() {",
            "    GenericClass<A, B> instance = new GenericClass<A, B>();",
            "    GenericClass_MembersInjector.injectA(instance, aProvider.get());",
            "    GenericClass_MembersInjector.injectRegister(instance, bProvider.get());",
            "    return instance;",
            "  }",
            "",
            "  public static <A, B> GenericClass_Factory<A, B> create(",
            "      Provider<A> aProvider, Provider<B> bProvider) {",
            "    return new GenericClass_Factory<A, B>(aProvider, bProvider);",
            "  }",
            "",
            "  public static <A, B> GenericClass<A, B> newInstance() {",
            "    return new GenericClass<A, B>();",
            "  }",
            "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void genericClassWithNoDependencies() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class GenericClass<T> {",
        "  @Inject GenericClass() {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<T> implements Factory<GenericClass<T>> {",
            "  @SuppressWarnings(\"rawtypes\")",
            "  private static final GenericClass_Factory INSTANCE = new GenericClass_Factory();",
            "",
            "  @Override",
            "  public GenericClass<T> get() {",
            "    return new GenericClass<T>();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  public static <T> GenericClass_Factory<T> create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static <T> GenericClass<T> newInstance() {",
            "    return new GenericClass<T>();",
            "  }",
            "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void twoGenericTypes() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class GenericClass<A, B> {",
        "  @Inject GenericClass(A a, B b) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<A, B> implements Factory<GenericClass<A, B>> {",
            "  private final Provider<A> aProvider;",
            "  private final Provider<B> bProvider;",
            "",
            "  public GenericClass_Factory(Provider<A> aProvider, Provider<B> bProvider) {",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  @Override",
            "  public GenericClass<A, B> get() {",
            "    return new GenericClass<A, B>(aProvider.get(), bProvider.get());",
            "  }",
            "",
            "  public static <A, B> GenericClass_Factory<A, B> create(",
            "      Provider<A> aProvider, Provider<B> bProvider) {",
            "    return new GenericClass_Factory<A, B>(aProvider, bProvider);",
            "  }",
            "",
            "  public static <A, B> GenericClass<A, B> newInstance(A a, B b) {",
            "    return new GenericClass<A, B>(a, b);",
            "  }",
            "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void boundedGenerics() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
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
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import java.util.List;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<A extends Number & Comparable<A>,",
            "        B extends List<? extends String>,",
            "        C extends List<? super String>>",
            "    implements Factory<GenericClass<A, B, C>> {",
            "  private final Provider<A> aProvider;",
            "  private final Provider<B> bProvider;",
            "  private final Provider<C> cProvider;",
            "",
            "  public GenericClass_Factory(Provider<A> aProvider,",
            "      Provider<B> bProvider,",
            "      Provider<C> cProvider) {",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "    this.cProvider = cProvider;",
            "  }",
            "",
            "  @Override",
            "  public GenericClass<A, B, C> get() {",
            "    return new GenericClass<A, B, C>(",
            "        aProvider.get(), bProvider.get(), cProvider.get());",
            "  }",
            "",
            "  public static <A extends Number & Comparable<A>,",
            "      B extends List<? extends String>,",
            "      C extends List<? super String>> GenericClass_Factory<A, B, C> create(",
            "          Provider<A> aProvider, Provider<B> bProvider, Provider<C> cProvider) {",
            "    return new GenericClass_Factory<A, B, C>(aProvider, bProvider, cProvider);",
            "  }",
            "",
            "  public static <",
            "          A extends Number & Comparable<A>,",
            "          B extends List<? extends String>,",
            "          C extends List<? super String>>",
            "      GenericClass<A, B, C> newInstance(A a, B b, C c) {",
            "    return new GenericClass<A, B, C>(a, b, c);",
            "  }",
            "}");
    assertAbout(javaSource()).that(file)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void multipleSameTypesWithGenericsAndQualifiersAndLazies() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericClass",
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
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.GenericClass_Factory",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class GenericClass_Factory<A, B>",
            "    implements Factory<GenericClass<A, B>> {",
            "  private final Provider<A> aAndA2AndPaAndLaProvider;",
            "  private final Provider<A> qaProvider;",
            "  private final Provider<String> sAndS2AndPsAndLsProvider;",
            "  private final Provider<String> qsProvider;",
            "  private final Provider<B> bAndB2AndPbAndLbProvider;",
            "  private final Provider<B> qbProvider;",
            "",
            "  public GenericClass_Factory(Provider<A> aAndA2AndPaAndLaProvider,",
            "      Provider<A> qaProvider,",
            "      Provider<String> sAndS2AndPsAndLsProvider,",
            "      Provider<String> qsProvider,",
            "      Provider<B> bAndB2AndPbAndLbProvider,",
            "      Provider<B> qbProvider) {",
            "    this.aAndA2AndPaAndLaProvider = aAndA2AndPaAndLaProvider;",
            "    this.qaProvider = qaProvider;",
            "    this.sAndS2AndPsAndLsProvider = sAndS2AndPsAndLsProvider;",
            "    this.qsProvider = qsProvider;",
            "    this.bAndB2AndPbAndLbProvider = bAndB2AndPbAndLbProvider;",
            "    this.qbProvider = qbProvider;",
            "  }",
            "",
            "  @Override",
            "  public GenericClass<A, B> get() {",
            "    return new GenericClass<A, B>(",
            "      aAndA2AndPaAndLaProvider.get(),",
            "      aAndA2AndPaAndLaProvider.get(),",
            "      aAndA2AndPaAndLaProvider,",
            "      qaProvider.get(),",
            "      DoubleCheck.lazy(aAndA2AndPaAndLaProvider),",
            "      sAndS2AndPsAndLsProvider.get(),",
            "      sAndS2AndPsAndLsProvider.get(),",
            "      sAndS2AndPsAndLsProvider,",
            "      qsProvider.get(),",
            "      DoubleCheck.lazy(sAndS2AndPsAndLsProvider),",
            "      bAndB2AndPbAndLbProvider.get(),",
            "      bAndB2AndPbAndLbProvider.get(),",
            "      bAndB2AndPbAndLbProvider,",
            "      qbProvider.get(),",
            "      DoubleCheck.lazy(bAndB2AndPbAndLbProvider));",
            "  }",
            "",
            "  public static <A, B> GenericClass_Factory<A, B> create(",
            "      Provider<A> aAndA2AndPaAndLaProvider,",
            "      Provider<A> qaProvider,",
            "      Provider<String> sAndS2AndPsAndLsProvider,",
            "      Provider<String> qsProvider,",
            "      Provider<B> bAndB2AndPbAndLbProvider,",
            "      Provider<B> qbProvider) {",
            "    return new GenericClass_Factory<A, B>(",
            "        aAndA2AndPaAndLaProvider,",
            "        qaProvider,",
            "        sAndS2AndPsAndLsProvider,",
            "        qsProvider,",
            "        bAndB2AndPbAndLbProvider,",
            "        qbProvider);",
            "  }",
            "",
            "  public static <A, B> GenericClass<A, B> newInstance(",
            "      A a,",
            "      A a2,",
            "      Provider<A> pa,",
            "      A qa,",
            "      Lazy<A> la,",
            "      String s,",
            "      String s2,",
            "      Provider<String> ps,",
            "      String qs,",
            "      Lazy<String> ls,",
            "      B b,",
            "      B b2,",
            "      Provider<B> pb,",
            "      B qb,",
            "      Lazy<B> lb) {",
            "    return new GenericClass<A, B>(",
            "        a, a2, pa, qa, la, s, s2, ps, qs, ls, b, b2, pb, qb, lb);",
            "  }",
            "}");
    assertAbout(javaSources()).that(ImmutableList.of(file, QUALIFIER_A))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void multipleInjectConstructors() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.TooManyInjectConstructors",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class TooManyInjectConstructors {",
        "  @Inject TooManyInjectConstructors() {}",
        "  TooManyInjectConstructors(int i) {}",
        "  @Inject TooManyInjectConstructors(String s) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Types may only contain one @Inject constructor")
        .inFile(file)
        .onLine(6);
    assertThat(compilation)
        .hadErrorContaining("Types may only contain one @Inject constructor")
        .inFile(file)
        .onLine(8);
  }

  @Test public void multipleQualifiersOnInjectConstructorParameter() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierConstructorParam",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierConstructorParam {",
        "  @Inject MultipleQualifierConstructorParam(@QualifierA @QualifierB String s) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    // for whatever reason, javac only reports the error once on the constructor
    assertThat(compilation)
        .hadErrorContaining("A single dependency request may not use more than one @Qualifier")
        .inFile(file)
        .onLine(6);
  }

  @Test public void injectConstructorOnClassWithMultipleScopes() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "@ScopeA @ScopeB class MultipleScopeClass {",
        "  @Inject MultipleScopeClass() {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, SCOPE_A, SCOPE_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("A single binding may not declare more than one @Scope")
        .inFile(file)
        .onLine(5)
        .atColumn(1);
    assertThat(compilation)
        .hadErrorContaining("A single binding may not declare more than one @Scope")
        .inFile(file)
        .onLine(5)
        .atColumn(9);
  }

  @Test public void injectConstructorWithQualifier() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleScopeClass",
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
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Qualifier annotations are not allowed on @Inject constructors")
        .inFile(file)
        .onLine(7);
    assertThat(compilation)
        .hadErrorContaining("@Qualifier annotations are not allowed on @Inject constructors")
        .inFile(file)
        .onLine(8);
  }

  @Test public void injectConstructorWithCheckedExceptionsError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.CheckedExceptionClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class CheckedExceptionClass {",
        "  @Inject CheckedExceptionClass() throws Exception {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support checked exceptions on @Inject constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test public void injectConstructorWithCheckedExceptionsWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.CheckedExceptionClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class CheckedExceptionClass {",
        "  @Inject CheckedExceptionClass() throws Exception {}",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support checked exceptions on @Inject constructors")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateInjectClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(7);
  }

  @Test public void privateInjectClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(7);
  }

  @Test public void nestedInPrivateInjectClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
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
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(8);
  }

  @Test public void nestedInPrivateInjectClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
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
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(8);
  }

  @Test public void finalInjectField() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FinalInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class FinalInjectField {",
        "  @Inject final String s;",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Inject fields may not be final")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateInjectFieldError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectField {",
        "  @Inject private String s;",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private fields")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateInjectFieldWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectField {",
        "  @Inject private String s;",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test public void staticInjectFieldError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.StaticInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class StaticInjectField {",
        "  @Inject static String s;",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into static fields")
        .inFile(file)
        .onLine(6);
  }

  @Test public void staticInjectFieldWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.StaticInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class StaticInjectField {",
        "  @Inject static String s;",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.staticMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test public void multipleQualifiersOnField() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierInjectField",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierInjectField {",
        "  @Inject @QualifierA @QualifierB String s;",
        "}");
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("A single dependency request may not use more than one @Qualifier")
        .inFile(file)
        .onLine(6)
        .atColumn(11);
    assertThat(compilation)
        .hadErrorContaining("A single dependency request may not use more than one @Qualifier")
        .inFile(file)
        .onLine(6)
        .atColumn(23);
  }

  @Test public void abstractInjectMethod() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AbstractInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "abstract class AbstractInjectMethod {",
        "  @Inject abstract void method();",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Methods with @Inject may not be abstract")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateInjectMethodError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectMethod {",
        "  @Inject private void method(){}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private methods")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateInjectMethodWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.PrivateInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class PrivateInjectMethod {",
        "  @Inject private void method(){}",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.privateMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test public void staticInjectMethodError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.StaticInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class StaticInjectMethod {",
        "  @Inject static void method(){}",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into static methods")
        .inFile(file)
        .onLine(6);
  }

  @Test public void staticInjectMethodWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.StaticInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class StaticInjectMethod {",
        "  @Inject static void method(){}",
        "}");
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.staticMemberValidation=WARNING").compile(file);
    assertThat(compilation).succeeded(); // TODO: Verify warning message when supported
  }

  @Test public void genericInjectMethod() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.GenericInjectMethod",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class AbstractInjectMethod {",
        "  @Inject <T> void method();",
        "}");
    Compilation compilation = daggerCompiler().compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Methods with @Inject may not declare type parameters")
        .inFile(file)
        .onLine(6);
  }

  @Test public void multipleQualifiersOnInjectMethodParameter() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MultipleQualifierMethodParam",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class MultipleQualifierMethodParam {",
        "  @Inject void method(@QualifierA @QualifierB String s) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(file, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("A single dependency request may not use more than one @Qualifier")
        .inFile(file)
        .onLine(6);
  }

  @Test public void injectConstructorDependsOnProduced() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Produced;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(Produced<String> str) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Produced may only be injected in @Produces methods");
  }

  @Test public void injectConstructorDependsOnProducer() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Producer;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(Producer<String> str) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Producer may only be injected in @Produces methods");
  }

  @Test public void injectFieldDependsOnProduced() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Produced;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject Produced<String> str;",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Produced may only be injected in @Produces methods");
  }

  @Test public void injectFieldDependsOnProducer() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Producer;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject Producer<String> str;",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Producer may only be injected in @Produces methods");
  }

  @Test public void injectMethodDependsOnProduced() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Produced;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject void inject(Produced<String> str) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Produced may only be injected in @Produces methods");
  }

  @Test public void injectMethodDependsOnProducer() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import dagger.producers.Producer;",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject void inject(Producer<String> str) {}",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Producer may only be injected in @Produces methods");
  }

  @Test public void injectConstructor() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(String s) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.InjectConstructor_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectConstructor_Factory ",
            "    implements Factory<InjectConstructor> {",
            "",
            "  private final Provider<String> sProvider;",
            "",
            "  public InjectConstructor_Factory(Provider<String> sProvider) {",
            "    this.sProvider = sProvider;",
            "  }",
            "",
            "  @Override public InjectConstructor get() {",
            "    return new InjectConstructor(sProvider.get());",
            "  }",
            "",
            "  public static InjectConstructor_Factory create(Provider<String> sProvider) {",
            "    return new InjectConstructor_Factory(sProvider);",
            "  }",
            "",
            "  public static InjectConstructor newInstance(String s) {",
            "    return new InjectConstructor(s);",
            "  }",
            "}");
    assertAbout(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test public void injectConstructorAndMembersInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.AllInjections",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class AllInjections {",
        "  @Inject String s;",
        "  @Inject AllInjections(String s) {}",
        "  @Inject void s(String s) {}",
        "}");
    JavaFileObject expectedFactory =
        JavaFileObjects.forSourceLines(
            "test.AllInjections_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class AllInjections_Factory implements Factory<AllInjections> {",
            "  private final Provider<String> sProvider;",
            "",
            "  public AllInjections_Factory(Provider<String> sProvider) {",
            "    this.sProvider = sProvider;",
            "  }",
            "",
            "  @Override public AllInjections get() {",
            "    AllInjections instance = new AllInjections(sProvider.get());",
            "    AllInjections_MembersInjector.injectS(instance, sProvider.get());",
            "    AllInjections_MembersInjector.injectS2(instance, sProvider.get());",
            "    return instance;",
            "  }",
            "",
            "  public static AllInjections_Factory create(Provider<String> sProvider) {",
            "    return new AllInjections_Factory(sProvider);",
            "  }",
            "",
            "  public static AllInjections newInstance(String s) {",
            "     return new AllInjections(s);",
            "   }",
            "}");
    assertAbout(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedFactory);
  }

  @Test
  public void wildcardDependency() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import java.util.List;",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(List<?> objects) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.InjectConstructor_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import java.util.List;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectConstructor_Factory ",
            "    implements Factory<InjectConstructor> {",
            "",
            "  private final Provider<List<?>> objectsProvider;",
            "",
            "  public InjectConstructor_Factory(Provider<List<?>> objectsProvider) {",
            "    this.objectsProvider = objectsProvider;",
            "  }",
            "",
            "  @Override public InjectConstructor get() {",
            "    return new InjectConstructor(objectsProvider.get());",
            "  }",
            "",
            "  public static InjectConstructor_Factory create(",
            "      Provider<List<?>> objectsProvider) {",
            "    return new InjectConstructor_Factory(objectsProvider);",
            "  }",
            "",
            "  public static InjectConstructor newInstance(List<?> objects) {",
            "    return new InjectConstructor(objects);",
            "  }",
            "}");
    assertAbout(javaSource()).that(file).processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void basicNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Factory",
        "package other.pkg;",
        "",
        "public class Factory {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Factory;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Factory factory) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.InjectConstructor_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectConstructor_Factory ",
            "    implements Factory<InjectConstructor> {",
            "",
            "  private final Provider<other.pkg.Factory> factoryProvider;",
            "",
            "  public InjectConstructor_Factory(Provider<other.pkg.Factory> factoryProvider) {",
            "    this.factoryProvider = factoryProvider;",
            "  }",
            "",
            "  @Override public InjectConstructor get() {",
            "    return new InjectConstructor(factoryProvider.get());",
            "  }",
            "",
            "  public static InjectConstructor_Factory create(",
            "      Provider<other.pkg.Factory> factoryProvider) {",
            "    return new InjectConstructor_Factory(factoryProvider);",
            "  }",
            "",
            "  public static InjectConstructor newInstance(",
            "      other.pkg.Factory factory) {",
            "    return new InjectConstructor(factory);",
            "  }",
            "}");
    assertAbout(javaSources()).that(ImmutableList.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void nestedNameCollision() {
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("other.pkg.Outer",
        "package other.pkg;",
        "",
        "public class Outer {",
        "  public class Factory {}",
        "}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import other.pkg.Outer;",
        "",
        "class InjectConstructor {",
        "  @Inject InjectConstructor(Outer.Factory factory) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.InjectConstructor_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "import other.pkg.Outer;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectConstructor_Factory ",
            "    implements Factory<InjectConstructor> {",
            "",
            "  private final Provider<Outer.Factory> factoryProvider;",
            "",
            "  public InjectConstructor_Factory(Provider<Outer.Factory> factoryProvider) {",
            "    this.factoryProvider = factoryProvider;",
            "  }",
            "",
            "  @Override public InjectConstructor get() {",
            "    return new InjectConstructor(factoryProvider.get());",
            "  }",
            "",
            "  public static InjectConstructor_Factory create(",
            "      Provider<Outer.Factory> factoryProvider) {",
            "    return new InjectConstructor_Factory(factoryProvider);",
            "  }",
            "",
            "  public static InjectConstructor newInstance(",
            "      Outer.Factory factory) {",
            "    return new InjectConstructor(factory);",
            "  }",
            "}");
    assertAbout(javaSources()).that(ImmutableList.of(factoryFile, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void samePackageNameCollision() {
    JavaFileObject samePackageInterface = JavaFileObjects.forSourceLines("test.CommonName",
        "package test;",
        "",
        "public interface CommonName {}");
    JavaFileObject differentPackageInterface = JavaFileObjects.forSourceLines(
        "other.pkg.CommonName",
        "package other.pkg;",
        "",
        "public interface CommonName {}");
    JavaFileObject file = JavaFileObjects.forSourceLines("test.InjectConstructor",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class InjectConstructor implements CommonName {",
        "  @Inject InjectConstructor(other.pkg.CommonName otherPackage, CommonName samePackage) {}",
        "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.InjectConstructor_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectConstructor_Factory ",
            "    implements Factory<InjectConstructor> {",
            "",
            "  private final Provider<other.pkg.CommonName> otherPackageProvider;",
            "  private final Provider<CommonName> samePackageProvider;",
            "",
            "  public InjectConstructor_Factory(",
            "      Provider<other.pkg.CommonName> otherPackageProvider,",
            "      Provider<CommonName> samePackageProvider) {",
            "    this.otherPackageProvider = otherPackageProvider;",
            "    this.samePackageProvider = samePackageProvider;",
            "  }",
            "",
            "  @Override public InjectConstructor get() {",
            "    return new InjectConstructor(",
            "        otherPackageProvider.get(), samePackageProvider.get());",
            "  }",
            "",
            "  public static InjectConstructor_Factory create(",
            "      Provider<other.pkg.CommonName> otherPackageProvider,",
            "      Provider<CommonName> samePackageProvider) {",
            "    return new InjectConstructor_Factory(otherPackageProvider, samePackageProvider);",
            "  }",
            "",
            "  public static InjectConstructor newInstance(",
            "      other.pkg.CommonName otherPackage, CommonName samePackage) {",
            "    return new InjectConstructor(otherPackage, samePackage);",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(samePackageInterface, differentPackageInterface, file))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expected);
  }

  @Test
  public void noDeps() {
    JavaFileObject simpleType = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.SimpleType_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class SimpleType_Factory implements Factory<SimpleType> {",
            "  private static final SimpleType_Factory INSTANCE = new SimpleType_Factory();",
            "",
            "  @Override public SimpleType get() {",
            "    return new SimpleType();",
            "  }",
            "",
            "  public static SimpleType_Factory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static SimpleType newInstance() {",
            "    return new SimpleType();",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(simpleType)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factory);
  }

  @Test public void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines("test.OuterType",
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
    JavaFileObject aFactory =
        JavaFileObjects.forSourceLines(
            "test.OuterType_A_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class OuterType_A_Factory implements Factory<OuterType.A> {",
            "  private static final OuterType_A_Factory INSTANCE = new OuterType_A_Factory();",
            "",
            "  @Override public OuterType.A get() {",
            "    return new OuterType.A();",
            "  }",
            "",
            "  public static OuterType_A_Factory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static OuterType.A newInstance() {",
            "    return new OuterType.A();",
            "  }",
            "}");
    assertAbout(javaSources()).that(ImmutableList.of(nestedTypesFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(aFactory);
  }
}
