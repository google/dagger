/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

@RunWith(JUnit4.class)
public class MembersInjectionTest {
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
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.MembersInjectors;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<Child> childProvider;",
        "",
        "  private DaggerTestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.childProvider =",
        "        Child_Factory.create(MembersInjectors.<Child>noOp());",
        "  }",
        "",
        "  @Override",
        "  public Child child() {",
        "    return childProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new DaggerTestComponent(this);",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(childFile, parentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
            "import dagger.MembersInjector;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private MembersInjector<Child> childMembersInjector;",
            "  private Provider<Child> childProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.childMembersInjector = Child_MembersInjector.create(Dep_Factory.create());",
            "    this.childProvider = Child_Factory.create(childMembersInjector);",
            "  }",
            "",
            "  @Override",
            "  public Child child() {",
            "    return childProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(childFile, parentFile, depFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
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
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class GenericClass_MembersInjector<A, B>",
        "    implements MembersInjector<GenericClass<A, B>> {",
        "  private final Provider<A> aProvider;",
        "  private final Provider<B> bProvider;",
        "",
        "  public GenericClass_MembersInjector(Provider<A> aProvider, Provider<B> bProvider) {",
        "    assert aProvider != null;",
        "    this.aProvider = aProvider;",
        "    assert bProvider != null;",
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
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.a = aProvider.get();",
        "    instance.register(bProvider.get());",
        "  }",
        "",
        "  public static <A, B> void injectA(GenericClass<A, B> instance, Provider<A> aProvider) {",
        "    instance.a = aProvider.get();",
        "  }",
        "",
        "  public static <A, B> void injectRegister(",
        "      GenericClass<A, B> instance, Provider<B> bProvider) {",
        "    instance.register(bProvider.get());",
        "  }",
        "",
        "}");
    assertAbout(javaSource())
        .that(file)
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
        "import javax.annotation.Generated;",
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
        "    assert tAndXProvider != null;",
        "    this.tAndXProvider = tAndXProvider;",
        "    assert aAndYProvider != null;",
        "    this.aAndYProvider = aAndYProvider;",
        "    assert a2Provider != null;",
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
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    ((Parent) instance).x = tAndXProvider.get();",
        "    ((Parent) instance).y = aAndYProvider.get();",
        "    ((Parent) instance).a2 = a2Provider.get();",
        "    instance.a = aAndYProvider.get();",
        "    instance.t = tAndXProvider.get();",
        "  }",
        "",
        "  public static <T> void injectA(Child<T> instance, Provider<A> aProvider) {",
        "    instance.a = aProvider.get();",
        "  }",
        "",
        "  public static <T> void injectT(Child<T> instance, Provider<T> tProvider) {",
        "    instance.t = tProvider.get();",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(a, a2, parent, child))
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
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class FieldInjection_MembersInjector",
            "    implements MembersInjector<FieldInjection> {",
            "  private final Provider<String> stringProvider;",
            "",
            "  public FieldInjection_MembersInjector(Provider<String> stringProvider) {",
            "    assert stringProvider != null;",
            "    this.stringProvider = stringProvider;",
            "  }",
            "",
            "  public static MembersInjector<FieldInjection> create(Provider<String> stringProvider) {",
            "    return new FieldInjection_MembersInjector(stringProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(FieldInjection instance) {",
            "    if (instance == null) {",
            "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
            "    }",
            "    instance.string = stringProvider.get();",
            "    instance.lazyString = DoubleCheck.lazy(stringProvider);",
            "    instance.stringProvider = stringProvider;",
            "  }",
            "",
            "  public static void injectString(",
            "      FieldInjection instance, Provider<String> stringProvider) {",
            "    instance.string = stringProvider.get();",
            "  }",
            "",
            "  public static void injectLazyString(",
            "      FieldInjection instance, Provider<String> lazyStringProvider) {",
            "    instance.lazyString = DoubleCheck.lazy(lazyStringProvider);",
            "  }",
            "",
            "  public static void injectStringProvider(",
            "      FieldInjection instance, Provider<String> stringProvider) {",
            "    instance.stringProvider = stringProvider;",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
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
            "import dagger.MembersInjector;",
            "import dagger.internal.DoubleCheck;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class MethodInjection_MembersInjector",
            "     implements MembersInjector<MethodInjection> {",
            "",
            "  private final Provider<String> stringProvider;",
            "",
            "  public MethodInjection_MembersInjector(Provider<String> stringProvider) {",
            "    assert stringProvider != null;",
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
            "    if (instance == null) {",
            "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
            "    }",
            "    instance.noArgs();",
            "    instance.oneArg(stringProvider.get());",
            "    instance.manyArgs(",
            "        stringProvider.get(),",
            "        DoubleCheck.lazy(stringProvider),",
            "        stringProvider);",
            "  }",
            "",
            "  public static void injectNoArgs(MethodInjection instance) {",
            "    instance.noArgs();",
            "  }",
            "",
            "  public static void injectOneArg(",
            "      MethodInjection instance, Provider<String> stringProvider) {",
            "    instance.oneArg(stringProvider.get());",
            "  }",
            "",
            "  public static void injectManyArgs(",
            "      MethodInjection instance,",
            "      Provider<String> stringProvider,",
            "      Provider<String> lazyStringProvider,",
            "      Provider<String> stringProvider2) {",
            "    instance.manyArgs(",
            "        stringProvider.get(),",
            "        DoubleCheck.lazy(lazyStringProvider),",
            "        stringProvider2);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(file)
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
        "import javax.annotation.Generated;",
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
        "    assert stringAndSProvider != null;",
        "    this.stringAndSProvider = stringAndSProvider;",
        "    assert objectAndOProvider != null;",
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
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.string = stringAndSProvider.get();",
        "    instance.object = objectAndOProvider.get();",
        "    instance.setString(stringAndSProvider.get());",
        "    instance.setObject(objectAndOProvider.get());",
        "  }",
        "",
        "  public static void injectString(",
        "      MixedMemberInjection instance, Provider<String> stringProvider) {",
        "    instance.string = stringProvider.get();",
        "  }",
        "",
        "  public static void injectObject(",
        "      MixedMemberInjection instance, Provider<Object> objectProvider) {",
        "    instance.object = objectProvider.get();",
        "  }",
        "",
        "  public static void injectSetString(",
        "      MixedMemberInjection instance, Provider<String> sProvider) {",
        "    instance.setString(sProvider.get());",
        "  }",
        "",
        "  public static void injectSetObject(",
        "      MixedMemberInjection instance, Provider<Object> oProvider) {",
        "    instance.setObject(oProvider.get());",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
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
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class AllInjections_MembersInjector ",
        "    implements MembersInjector<AllInjections> {",
        "",
        "  private final Provider<String> sProvider;",
        "",
        "  public AllInjections_MembersInjector(Provider<String> sProvider) {",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  public static MembersInjector<AllInjections> create(Provider<String> sProvider) {",
        "      return new AllInjections_MembersInjector(sProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(AllInjections instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.s = sProvider.get();",
        "    instance.s(sProvider.get());",
        "  }",
        "",
        "  public static void injectS(AllInjections instance, Provider<String> sProvider) {",
        "    instance.s = sProvider.get();",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(file)
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
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class B_MembersInjector implements MembersInjector<B> {",
        "  private final Provider<String> sProvider;",
        "",
        "  public B_MembersInjector(Provider<String> sProvider) {",
        "    assert sProvider != null;",
        "    this.sProvider = sProvider;",
        "  }",
        "",
        "  public static MembersInjector<B> create(Provider<String> sProvider) {",
        "      return new B_MembersInjector(sProvider);",
        "  }",
        "",
        "  @Override",
        "  public void injectMembers(B instance) {",
        "    if (instance == null) {",
        "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "    }",
        "    instance.s = sProvider.get();",
        "  }",
        "",
        "  public static void injectS(B instance, Provider<String> sProvider) {",
        "    instance.s = sProvider.get();",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile))
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
          "import javax.annotation.Generated;",
          "import javax.inject.Provider;",
          "",
          GENERATED_ANNOTATION,
          "public final class OuterType_B_MembersInjector",
          "    implements MembersInjector<OuterType.B> {",
          "  private final Provider<OuterType.A> aProvider;",
          "",
          "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
          "    assert aProvider != null;",
          "    this.aProvider = aProvider;",
          "  }",
          "",
          "  public static MembersInjector<OuterType.B> create(Provider<OuterType.A> aProvider) {",
          "    return new OuterType_B_MembersInjector(aProvider);",
          "  }",
          "",
          "  @Override",
          "  public void injectMembers(OuterType.B instance) {",
          "    if (instance == null) {",
          "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
          "    }",
          "    instance.a = aProvider.get();",
          "  }",
          "",
          "  public static void injectA(OuterType.B instance, Provider<OuterType.A> aProvider) {",
          "    instance.a = aProvider.get();",
          "  }",
          "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(nestedTypesFile))
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
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class OuterType_B_MembersInjector",
            "    implements MembersInjector<OuterType.B> {",
            "  private final Provider<OuterType.A> aProvider;",
            "",
            "  public OuterType_B_MembersInjector(Provider<OuterType.A> aProvider) {",
            "    assert aProvider != null;",
            "    this.aProvider = aProvider;",
            "  }",
            "",
            "  public static MembersInjector<OuterType.B> create(Provider<OuterType.A> aProvider) {",
            "    return new OuterType_B_MembersInjector(aProvider);",
            "  }",
            "",
            "  @Override",
            "  public void injectMembers(OuterType.B instance) {",
            "    if (instance == null) {",
            "      throw new NullPointerException(\"Cannot inject members into a null reference\");",
            "    }",
            "    instance.a = aProvider.get();",
            "  }",
            "",
            "  public static void injectA(OuterType.B instance, Provider<OuterType.A> aProvider) {",
            "    instance.a = aProvider.get();",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(nestedTypesFile)
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

    assertAbout(javaSources())
        .that(ImmutableList.of(foo, fooModule, fooComponent))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesFileNamed(CLASS_OUTPUT, "test", "foo_MembersInjector.class");
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
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class Child_MembersInjector implements MembersInjector<Child> {",
            "  private final Provider<Foo> objectProvider;",
            "  private final Provider<Bar> objectProvider2;",
            "",
            "  public Child_MembersInjector(",
            "        Provider<Foo> objectProvider, Provider<Bar> objectProvider2) {",
            "    assert objectProvider != null;",
            "    this.objectProvider = objectProvider;",
            "    assert objectProvider2 != null;",
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
            "    if (instance == null) {",
            "      throw new NullPointerException(",
            "          \"Cannot inject members into a null reference\");",
            "    }",
            "    ((Parent) instance).object = objectProvider.get();",
            "    instance.object = objectProvider2.get();",
            "  }",
            "",
            "  public static void injectObject(Child instance, Provider<Bar> objectProvider) {",
            "    instance.object = objectProvider.get();",
            "  }",
            "}");

    assertAbout(javaSources())
        .that(ImmutableList.of(foo, bar, parent, child, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedMembersInjector);
  }
}
