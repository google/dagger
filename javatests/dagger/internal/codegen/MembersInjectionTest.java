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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MembersInjectionTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MembersInjectionTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void parentClass_noInjectedMembers() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "public abstract class Parent {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Child child() {",
            "    return new Child();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void parentClass_injectedMembersInSupertype() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Child child() {",
            "    return injectChild(Child_Factory.newInstance());",
            "  }",
            "",
            "  @CanIgnoreReturnValue",
            "  private Child injectChild(Child instance) {",
            "    Parent_MembersInjector.injectDep(instance, new Dep());",
            "    return instance;",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(childFile, parentFile, depFile, componentFile);

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
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
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.GenericClass_MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        IMPORT_GENERATED_ANNOTATION,
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class GenericClass_MembersInjector<A, B>",
        "    implements MembersInjector<GenericClass<A, B>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<B> bProvider;",
        "",
        "  public GenericClass_MembersInjector(Provider<A> aProvider, Provider<B> bProvider) {",
        "    this.aProvider = aProvider;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  public static <A, B> MembersInjector<GenericClass<A, B>> create(",
        "      Provider<A> aProvider, Provider<B> bProvider) {",
        "    return new GenericClass_MembersInjector<A, B>(aProvider, bProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(GenericClass<A, B> instance) {",
        "    injectA(instance, aProvider.get());",
        "    injectRegister(instance, bProvider.get());",
        "  }",
        "",
        "  public static <A, B> void injectA(Object instance, A a) {",
        "    ((GenericClass<A, B>) instance).a = a;",
        "  }",
        "",
        "  public static <A, B> void injectRegister(Object instance, B b) {",
        "    ((GenericClass<A, B>) instance).register(b);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void subclassedGenericMembersInjectors() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject a2 = JavaFileObjects.forSourceLines("test.A2",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A2 {",
        "  @Inject A2() {}",
        "}");
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
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
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
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
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.Child_MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        IMPORT_GENERATED_ANNOTATION,
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class Child_MembersInjector<T>",
        "    implements MembersInjector<Child<T>> {",
        "  private final Provider<T> tAndXProvider;",
        "  private final Provider<A> aAndYProvider;",
        "  private final Provider<A2> a2Provider;",
        "",
        "  public Child_MembersInjector(",
        "      Provider<T> tAndXProvider, Provider<A> aAndYProvider, Provider<A2> a2Provider) {",
        "    this.tAndXProvider = tAndXProvider;",
        "    this.aAndYProvider = aAndYProvider;",
        "    this.a2Provider = a2Provider;",
        "  }",
        "",
        "  public static <T> MembersInjector<Child<T>> create(",
        "      Provider<T> tAndXProvider, Provider<A> aAndYProvider, Provider<A2> a2Provider) {",
        "    return new Child_MembersInjector<T>(tAndXProvider, aAndYProvider, a2Provider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(Child<T> instance) {",
        "    Parent_MembersInjector.injectX(instance, tAndXProvider.get());",
        "    Parent_MembersInjector.injectY(instance, aAndYProvider.get());",
        "    Parent_MembersInjector.injectA2(instance, a2Provider.get());",
        "    injectA(instance, aAndYProvider.get());",
        "    injectT(instance, tAndXProvider.get());",
        "  }",
        "",
        "  public static <T> void injectA(Object instance, Object a) {",
        "    ((Child<T>) instance).a = (A) a;",
        "  }",
        "",
        "  public static <T> void injectT(Object instance, T t) {",
        "    ((Child<T>) instance).t = t;",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(a, a2, parent, child))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void fieldInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.FieldInjection",
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
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.FieldInjection_MembersInjector",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class FieldInjection_MembersInjector",
            "    implements MembersInjector<FieldInjection> {",
            "  private final Provider<String> stringProvider;",
            "",
            "  public FieldInjection_MembersInjector(Provider<String> stringProvider) {",
            "    this.stringProvider = stringProvider;",
            "  }",
            "",
            "  public static MembersInjector<FieldInjection> create(",
            "      Provider<String> stringProvider) {",
            "    return new FieldInjection_MembersInjector(stringProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(FieldInjection instance) {",
            "    injectString(instance, stringProvider.get());",
            "    injectLazyString(instance, DoubleCheck.lazy(stringProvider));",
            "    injectStringProvider(instance, stringProvider);",
            "  }",
            "",
            "  public static void injectString(Object instance, String string) {",
            "    ((FieldInjection) instance).string = string;",
            "  }",
            "",
            "  public static void injectLazyString(Object instance, Lazy<String> lazyString) {",
            "    ((FieldInjection) instance).lazyString = lazyString;",
            "  }",
            "",
            "  public static void injectStringProvider(",
            "      Object instance, Provider<String> stringProvider) {",
            "    ((FieldInjection) instance).stringProvider = stringProvider;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test public void methodInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.MethodInjection",
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
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.MethodInjection_MembersInjector",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class MethodInjection_MembersInjector",
            "     implements MembersInjector<MethodInjection> {",
            "",
            "  private final Provider<String> stringProvider;",
            "",
            "  public MethodInjection_MembersInjector(Provider<String> stringProvider) {",
            "    this.stringProvider = stringProvider;",
            "  }",
            "",
            "  public static MembersInjector<MethodInjection> create(",
            "      Provider<String> stringProvider) {",
            "    return new MethodInjection_MembersInjector(stringProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(MethodInjection instance) {",
            "    injectNoArgs(instance);",
            "    injectOneArg(instance, stringProvider.get());",
            "    injectManyArgs(",
            "        instance,",
            "        stringProvider.get(),",
            "        DoubleCheck.lazy(stringProvider),",
            "        stringProvider);",
            "  }",
            "",
            "  public static void injectNoArgs(Object instance) {",
            "    ((MethodInjection) instance).noArgs();",
            "  }",
            "",
            "  public static void injectOneArg(Object instance, String string) {",
            "    ((MethodInjection) instance).oneArg(string);",
            "  }",
            "",
            "  public static void injectManyArgs(",
            "      Object instance,",
            "      String string,",
            "      Lazy<String> lazyString,",
            "      Provider<String> stringProvider) {",
            "    ((MethodInjection) instance).manyArgs(string, lazyString, stringProvider);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
  }

  @Test
  public void mixedMemberInjection() {
    JavaFileObject file = JavaFileObjects.forSourceLines(
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
    JavaFileObject expected = JavaFileObjects.forSourceLines(
        "test.MixedMemberInjection_MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        IMPORT_GENERATED_ANNOTATION,
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class MixedMemberInjection_MembersInjector",
        "    implements MembersInjector<MixedMemberInjection> {",
        "",
        "  private final Provider<String> stringAndSProvider;",
        "  private final Provider<Object> objectAndOProvider;",
        "",
        "  public MixedMemberInjection_MembersInjector(",
        "      Provider<String> stringAndSProvider,",
        "      Provider<Object> objectAndOProvider) {",
        "    this.stringAndSProvider = stringAndSProvider;",
        "    this.objectAndOProvider = objectAndOProvider;",
        "  }",
        "",
        "  public static MembersInjector<MixedMemberInjection> create(",
        "      Provider<String> stringAndSProvider,",
        "      Provider<Object> objectAndOProvider) {",
        "    return new MixedMemberInjection_MembersInjector(",
        "        stringAndSProvider, objectAndOProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(MixedMemberInjection instance) {",
        "    injectString(instance, stringAndSProvider.get());",
        "    injectObject(instance, objectAndOProvider.get());",
        "    injectSetString(instance, stringAndSProvider.get());",
        "    injectSetObject(instance, objectAndOProvider.get());",
        "  }",
        "",
        "  public static void injectString(Object instance, String string) {",
        "    ((MixedMemberInjection) instance).string = string;",
        "  }",
        "",
        "  public static void injectObject(Object instance, Object object) {",
        "    ((MixedMemberInjection) instance).object = object;",
        "  }",
        "",
        "  public static void injectSetString(Object instance, String s) {",
        "    ((MixedMemberInjection) instance).setString(s);",
        "  }",
        "",
        "  public static void injectSetObject(Object instance, Object o) {",
        "    ((MixedMemberInjection) instance).setObject(o);",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expected);
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
    JavaFileObject expectedMembersInjector = JavaFileObjects.forSourceLines(
        "test.AllInjections_MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        IMPORT_GENERATED_ANNOTATION,
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class AllInjections_MembersInjector ",
        "    implements MembersInjector<AllInjections> {",
        "",
        "  private final Provider<String> sProvider;",
        "",
        "  public AllInjections_MembersInjector(Provider<String> sProvider) {",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  public static MembersInjector<AllInjections> create(Provider<String> sProvider) {",
        "      return new AllInjections_MembersInjector(sProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(AllInjections instance) {",
        "    injectS(instance, sProvider.get());",
        "    injectS2(instance, sProvider.get());",
        "  }",
        "",
        // TODO(b/64477506): now that these all take "object", it would be nice to rename "instance"
        // to the type name
        "  public static void injectS(Object instance, String s) {",
        "    ((AllInjections) instance).s = s;",
        "  }",
        "",
        "  public static void injectS2(Object instance, String s) {",
        "    ((AllInjections) instance).s(s);",
        "  }",
        "",
        "}");
    assertAbout(javaSource())
        .that(file)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test public void supertypeMembersInjection() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "class A {}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class B extends A {",
        "  @Inject String s;",
        "}");
    JavaFileObject expectedMembersInjector = JavaFileObjects.forSourceLines(
        "test.AllInjections_MembersInjector",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        IMPORT_GENERATED_ANNOTATION,
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class B_MembersInjector implements MembersInjector<B> {",
        "  private final Provider<String> sProvider;",
        "",
        "  public B_MembersInjector(Provider<String> sProvider) {",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  public static MembersInjector<B> create(Provider<String> sProvider) {",
        "      return new B_MembersInjector(sProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(B instance) {",
        "    injectS(instance, sProvider.get());",
        "  }",
        "",
        "  public static void injectS(Object instance, String s) {",
        "    ((B) instance).s = s;",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test
  public void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines(
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
    JavaFileObject bMembersInjector = JavaFileObjects.forSourceLines(
          "test.OuterType_B_MembersInjector",
          "package test;",
          "",
          "import dagger.MembersInjector;",
          IMPORT_GENERATED_ANNOTATION,
          "import javax.inject.Provider;",
          "",
          GENERATED_ANNOTATION,
          "public final class OuterType_B_MembersInjector",
          "    implements MembersInjector<OuterType.B> {",
          "  private final Provider<OuterType.A> aProvider;",
          "",
          "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
          "    this.aProvider = aProvider;",
          "  }",
          "",
          "  public static MembersInjector<OuterType.B> create(Provider<OuterType.A> aProvider) {",
          "    return new OuterType_B_MembersInjector(aProvider);",
          "  }",
          "",
          "  @Override",
          "  public void injectMembers(OuterType.B instance) {",
          "    injectA(instance, aProvider.get());",
          "  }",
          "",
          "  public static void injectA(Object instance, Object a) {",
          "    ((OuterType.B) instance).a = (OuterType.A) a;",
          "  }",
          "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(nestedTypesFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(bMembersInjector);
  }

  @Test
  public void componentWithNestingAndGeneratedType() {
    JavaFileObject nestedTypesFile =
        JavaFileObjects.forSourceLines(
            "test.OuterType",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "",
            "final class OuterType {",
            "  @Inject GeneratedType generated;",
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
    JavaFileObject bMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.OuterType_B_MembersInjector",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class OuterType_B_MembersInjector",
            "    implements MembersInjector<OuterType.B> {",
            "  private final Provider<OuterType.A> aProvider;",
            "",
            "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
            "    this.aProvider = aProvider;",
            "  }",
            "",
            "  public static MembersInjector<OuterType.B> create(",
            "      Provider<OuterType.A> aProvider) {",
            "    return new OuterType_B_MembersInjector(aProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(OuterType.B instance) {",
            "    injectA(instance, aProvider.get());",
            "  }",
            "",
            "  public static void injectA(Object instance, Object a) {",
            "    ((OuterType.B) instance).a = (OuterType.A) a;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(nestedTypesFile)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(
            new ComponentProcessor(),
            new AbstractProcessor() {
              private boolean done;

              @Override
              public Set<String> getSupportedAnnotationTypes() {
                return ImmutableSet.of("*");
              }

              @Override
              public boolean process(
                  Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (!done) {
                  done = true;
                  try (Writer writer =
                      processingEnv
                          .getFiler()
                          .createSourceFile("test.GeneratedType")
                          .openWriter()) {
                    writer.write(
                        Joiner.on('\n')
                            .join(
                                "package test;",
                                "",
                                "import javax.inject.Inject;",
                                "",
                                "class GeneratedType {",
                                "  @Inject GeneratedType() {}",
                                "}"));
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
                return false;
              }
            })
        .compilesWithoutError()
        .and()
        .generatesSources(bMembersInjector);
  }

  @Test
  public void lowerCaseNamedMembersInjector_forLowerCaseType() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class foo {",
            "  @Inject String string;",
            "}");
    JavaFileObject fooModule =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.fooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = fooModule.class)",
            "interface fooComponent {",
            "  void inject(foo target);",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, fooModule, fooComponent);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedFile(CLASS_OUTPUT, "test", "foo_MembersInjector.class");
  }

  @Test
  public void fieldInjectionForShadowedMember() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Parent { ",
            "  @Inject Foo object;",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Child extends Parent { ",
            "  @Inject Bar object;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C { ",
            "  void inject(Child child);",
            "}");

    JavaFileObject expectedMembersInjector =
        JavaFileObjects.forSourceLines(
            "test.Child_MembersInjector",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class Child_MembersInjector implements MembersInjector<Child> {",
            "  private final Provider<Foo> objectProvider;",
            "  private final Provider<Bar> objectProvider2;",
            "",
            "  public Child_MembersInjector(",
            "        Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
            "    this.objectProvider = objectProvider;",
            "    this.objectProvider2 = objectProvider2;",
            "  }",
            "",
            "  public static MembersInjector<Child> create(",
            "      Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
            "    return new Child_MembersInjector(objectProvider, objectProvider2);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(Child instance) {",
            "    Parent_MembersInjector.injectObject(instance, objectProvider.get());",
            "    injectObject(instance, objectProvider2.get());",
            "  }",
            "",
            "  public static void injectObject(Object instance, Object object) {",
            "    ((Child) instance).object = (Bar) object;",
            "  }",
            "}");

    assertAbout(javaSources())
        .that(ImmutableList.of(foo, bar, parent, child, component))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(expectedMembersInjector);
  }

  @Test public void privateNestedClassError() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().withOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateNestedClassWarning() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  private static final class InnerClass {",
        "    @Inject int field;",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                compilerMode.javacopts().append("-Adagger.privateMemberValidation=WARNING"))
            .compile(file);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("Dagger does not support injection into private classes")
        .inFile(file)
        .onLine(6);
  }

  @Test public void privateSuperclassIsOkIfNotInjectedInto() {
    JavaFileObject file = JavaFileObjects.forSourceLines("test.OuterClass",
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
    Compilation compilation = daggerCompiler().withOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).succeeded();
  }

  @Test
  public void rawFrameworkTypeField() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderField {",
            "  @Inject Provider fieldWithRawProvider;",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderField rawProviderField);",
            "}");

    Compilation compilation = daggerCompiler().withOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("javax.inject.Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @Test
  public void rawFrameworkTypeParameter() {
    JavaFileObject file =
        JavaFileObjects.forSourceLines(
            "test.RawFrameworkTypes",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class RawProviderParameter {",
            "  @Inject void methodInjection(Provider rawProviderParameter) {}",
            "}",
            "",
            "@Component",
            "interface C {",
            "  void inject(RawProviderParameter rawProviderParameter);",
            "}");

    Compilation compilation = daggerCompiler().withOptions(compilerMode.javacopts()).compile(file);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("javax.inject.Provider cannot be provided")
        .inFile(file)
        .onLineContaining("interface C");
  }

  @Test
  public void injectsPrimitive() {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject membersInjector =
        JavaFileObjects.forSourceLines(
            "test.InjectedType_MembersInjector",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectedType_MembersInjector ",
            "    implements MembersInjector<InjectedType> {",
            "  private final Provider<Integer> boxedIntAndPrimitiveIntProvider;",
            "",
            "  public InjectedType_MembersInjector(",
            "      Provider<Integer> boxedIntAndPrimitiveIntProvider) {",
            "    this.boxedIntAndPrimitiveIntProvider = boxedIntAndPrimitiveIntProvider;",
            "  }",
            "",
            "  public static MembersInjector<InjectedType> create(",
            "      Provider<Integer> boxedIntAndPrimitiveIntProvider) {",
            "    return new InjectedType_MembersInjector(boxedIntAndPrimitiveIntProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(InjectedType instance) {",
            "    injectPrimitiveInt(instance, boxedIntAndPrimitiveIntProvider.get());",
            "    injectBoxedInt(instance, boxedIntAndPrimitiveIntProvider.get());",
            "  }",
            "",
            "  public static void injectPrimitiveInt(Object instance, int primitiveInt) {",
            "    ((InjectedType) instance).primitiveInt = primitiveInt;",
            "  }",
            "",
            "  public static void injectBoxedInt(Object instance, Integer boxedInt) {",
            "    ((InjectedType) instance).boxedInt = boxedInt;",
            "  }",
            "}");
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.InjectedType_Factory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class InjectedType_Factory implements Factory<InjectedType> {",
            "  private final Provider<Integer> boxedIntAndPrimitiveIntProvider;",
            "",
            "  public InjectedType_Factory(Provider<Integer> boxedIntAndPrimitiveIntProvider) {",
            "    this.boxedIntAndPrimitiveIntProvider = boxedIntAndPrimitiveIntProvider;",
            "  }",
            "",
            "  @Override",
            "  public InjectedType get() {",
            "    InjectedType instance = new InjectedType();",
            "    InjectedType_MembersInjector.injectPrimitiveInt(",
            "        instance, boxedIntAndPrimitiveIntProvider.get());",
            "    InjectedType_MembersInjector.injectBoxedInt(",
            "        instance, boxedIntAndPrimitiveIntProvider.get());",
            "    return instance;",
            "  }",
            "",
            "  public static InjectedType_Factory create(",
            "      Provider<Integer> boxedIntAndPrimitiveIntProvider) {",
            "    return new InjectedType_Factory(boxedIntAndPrimitiveIntProvider);",
            "  }",
            "",
            "  public static InjectedType newInstance() {",
            "    return new InjectedType();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(injectedType);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_MembersInjector")
        .hasSourceEquivalentTo(membersInjector);
    assertThat(compilation)
        .generatedSourceFile("test.InjectedType_Factory")
        .hasSourceEquivalentTo(factory);
  }

  @Test
  public void accessibility() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Inaccessible inaccessible) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, inaccessible, usesInaccessible, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("other.Inaccessible_MembersInjector")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "other.Inaccessible_MembersInjector",
                "package other;",
                "",
                "import dagger.MembersInjector;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class Inaccessible_MembersInjector",
                "    implements MembersInjector<Inaccessible> {",
                "  private final Provider<Foo> fooProvider;",
                "",
                "  public Inaccessible_MembersInjector(Provider<Foo> fooProvider) {",
                "    this.fooProvider = fooProvider;",
                "  }",
                "",
                "  public static MembersInjector<Inaccessible> create(Provider<Foo> fooProvider) {",
                "    return new Inaccessible_MembersInjector(fooProvider);",
                "  }",
                "",
                "  @Override",
                "  public void injectMembers(Inaccessible instance) {",
                "    injectFoo(instance, fooProvider.get());",
                "    injectMethod(instance, fooProvider.get());",
                "  }",
                "",
                "  public static void injectFoo(Object instance, Object foo) {",
                "    ((Inaccessible) instance).foo = (Foo) foo;",
                "  }",
                "",
                "  public static void injectMethod(Object instance, Object foo) {",
                "    ((Inaccessible) instance).method((Foo) foo);",
                "  }",
                "}"));
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
            IMPORT_GENERATED_ANNOTATION,
            "import other.Foo_Factory;",
            "import other.Inaccessible_Factory;",
            "import other.Inaccessible_MembersInjector;",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private Object getInaccessible() {",
            "    return injectInaccessible(Inaccessible_Factory.newInstance());",
            "  }",
            "",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newInstance(",
            "        getInaccessible());",
            "  }",
            "",
            // TODO(ronshapiro): if possible, it would be great to rename "instance", but we
            // need to make sure that this doesn't conflict with any framework field in this or
            // any parent component
            "  @CanIgnoreReturnValue",
            "  private Object injectInaccessible(Object instance) {",
            "    Inaccessible_MembersInjector.injectFoo(instance, Foo_Factory.newInstance());",
            "    Inaccessible_MembersInjector.injectMethod(instance, Foo_Factory.newInstance());",
            "    return instance;",
            "  }",
            "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void accessibleRawType_ofInaccessibleType() {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    JavaFileObject inaccessiblesModule =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject usesInaccessibles =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(inaccessible, inaccessiblesModule, usesInaccessibles, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                "import other.InaccessiblesModule;",
                "import other.InaccessiblesModule_InaccessiblesFactory;",
                "import other.UsesInaccessibles;",
                "import other.UsesInaccessibles_Factory;",
                "import other.UsesInaccessibles_MembersInjector;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private volatile Object listOfInaccessible = new MemoizedSentinel();",
                "",
                "  private List getListOfInaccessible() {",
                "    Object local = listOfInaccessible;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = listOfInaccessible;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = InaccessiblesModule_InaccessiblesFactory.inaccessibles();",
                "          listOfInaccessible =",
                "              DoubleCheck.reentrantCheck(listOfInaccessible, local);",
                "        }",
                "      }",
                "    }",
                "    return (List) local;",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider inaccessiblesProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.inaccessiblesProvider =",
                "        DoubleCheck.provider(InaccessiblesModule_InaccessiblesFactory.create());",
                "  }")
            .addLines(
                "",
                "  @Override",
                "  public UsesInaccessibles usesInaccessibles() {",
                "    return injectUsesInaccessibles(",
                "        UsesInaccessibles_Factory.newInstance());",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private UsesInaccessibles injectUsesInaccessibles(",
                "        UsesInaccessibles instance) {",
                "    UsesInaccessibles_MembersInjector.injectInaccessibles(")
            .addLinesIn(
                FAST_INIT_MODE,
                "        instance, (List) getListOfInaccessible());")
            .addLinesIn(
                DEFAULT_MODE,
                "        instance, (List) inaccessiblesProvider.get());")
            .addLines(
                "    return instance;",
                "  }",
                "}")
            .build();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void publicSupertypeHiddenSubtype() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "other.Foo",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Supertype<T> {",
            "  @Inject T t;",
            "}");
    JavaFileObject subtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Subtype extends Supertype<Foo> {",
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject injectsSubtype =
        JavaFileObjects.forSourceLines(
            "other.InjectsSubtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class InjectsSubtype {",
            "  @Inject InjectsSubtype(Subtype s) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  other.InjectsSubtype injectsSubtype();",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, supertype, subtype, injectsSubtype, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
            "import other.Foo_Factory;",
            "import other.InjectsSubtype;",
            "import other.InjectsSubtype_Factory;",
            "import other.Subtype_Factory;",
            "import other.Supertype;",
            "import other.Supertype_MembersInjector;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private Object getSubtype() {",
            "    return injectSubtype(Subtype_Factory.newInstance());",
            "  }",
            "",
            "  @Override",
            "  public InjectsSubtype injectsSubtype() {",
            "    return InjectsSubtype_Factory.newInstance(getSubtype());",
            "  }",
            "",
            "  @CanIgnoreReturnValue",
            "  private Object injectSubtype(Object instance) {",
            "    Supertype_MembersInjector.injectT(",
            "        (Supertype) instance, Foo_Factory.newInstance());",
            "    return instance;",
            "  }",
            "}");

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }
}
