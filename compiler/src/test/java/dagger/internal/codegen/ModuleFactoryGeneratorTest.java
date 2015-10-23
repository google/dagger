/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.writer.StringLiteral;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS;

@RunWith(JUnit4.class)
public class ModuleFactoryGeneratorTest {

  private final JavaFileObject NULLABLE = JavaFileObjects.forSourceLines("test.Nullable",
      "package test;",
      "public @interface Nullable {}");

  private static final StringLiteral NPE_LITERAL =
      StringLiteral.forValue(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);

  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

  private String formatErrorMessage(String msg) {
    return String.format(msg, "Provides");
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, "Provides", "Module");
  }

  @Test public void providesMethodNotInModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE));
  }

  @Test public void providesMethodAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "abstract class TestModule {",
        "  @Provides abstract String provideString();",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_ABSTRACT));
  }

  @Test public void providesMethodPrivate() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides private String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_PRIVATE));
  }

  @Test public void providesMethodReturnVoid() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides void provideNothing() {}",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
  }

  @Test public void providesMethodWithTypeParameter() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides <T> String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
  }

  @Test public void providesMethodSetValuesWildcard() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set<?> provideWildcard() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_RETURN_TYPE);
  }

  @Test public void providesMethodSetValuesRawSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set provideSomething() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void providesMethodSetValuesNotASet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) List<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_SET_VALUES_RETURN_SET);
  }

  @Test public void modulesWithTypeParamsMustBeAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule<A> {}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT);
  }

  @Test public void provideOverriddenByNoProvide() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class Parent {",
        "  @Provides String foo() { return null; }",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "class Child extends Parent{",
        "  String foo() { return null; }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(parent, child))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(ErrorMessages.METHOD_OVERRIDES_PROVIDES_METHOD,
            "Provides", "@Provides String test.Parent.foo()"));
  }

  @Test public void provideOverriddenByProvide() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class Parent {",
        "  @Provides String foo() { return null; }",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class Child extends Parent{",
        "  @Provides String foo() { return null; }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(parent, child))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(ErrorMessages.PROVIDES_METHOD_OVERRIDES_ANOTHER,
            "Provides", "@Provides String test.Parent.foo()"));
  }

  @Test public void providesOverridesNonProvides() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "class Parent {",
        "  String foo() { return null; }",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class Child extends Parent{",
        "  @Provides String foo() { return null; }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(parent, child))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(ErrorMessages.PROVIDES_METHOD_OVERRIDES_ANOTHER,
            "Provides", "String test.Parent.foo()"));
  }

  @Test public void validatesIncludedModules() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = Void.class)",
        "class TestModule {}");
    assertAbout(javaSources()).that(ImmutableList.of(module))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(ErrorMessages.REFERENCED_MODULE_NOT_ANNOTATED,
            "java.lang.Void", "Module"));
  }

  @Test public void referencedModulesMustNotBeAbstract() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AbstractModule.class)",
        "class TestModule {}");
    JavaFileObject abstractModule = JavaFileObjects.forSourceLines("test.AbstractModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "abstract class AbstractModule {}");
    assertAbout(javaSources()).that(ImmutableList.of(module, abstractModule))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(ErrorMessages.REFERENCED_MODULES_MUST_NOT_BE_ABSTRACT,
            "test.AbstractModule"));
  }

  @Test public void singleProvidesMethodNoArgs() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProvideStringFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public String get() {",
        "    String provided = module.provideString();",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static Factory<String> create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProvidesMethodNoArgs_disableNullable() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProvideStringFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public String get() {",
        "    return module.provideString();",
        "  }",
        "",
        "  public static Factory<String> create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void nullableProvides() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @Nullable String provideString() { return null; }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProvideStringFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideStringFactory implements Factory<String> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override",
        "  @Nullable",
        "  public String get() {",
        "    return module.provideString();",
        "  }",
        "",
        "  public static Factory<String> create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(moduleFile, NULLABLE))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

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

  @Test public void multipleProvidesMethods() {
    JavaFileObject classXFile = JavaFileObjects.forSourceLines("test.X",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class X {",
        "  @Inject public String s;",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Arrays;",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides List<Object> provideObjects(",
        "      @QualifierA Object a, @QualifierB Object b, MembersInjector<X> x) {",
        "    return Arrays.asList(a, b);",
        "  }",
        "",
        "  @Provides @QualifierA Object provideAObject() {",
        "    return new Object();",
        "  }",
        "",
        "  @Provides @QualifierB Object provideBObject() {",
        "    return new Object();",
        "  }",
        "}");
    JavaFileObject listFactoryFile = JavaFileObjects.forSourceLines(
        "TestModule_ProvideObjectsFactory",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.Factory;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideObjectsFactory implements Factory<List<Object>> {",
        "  private final TestModule module;",
        "  private final Provider<Object> aProvider;",
        "  private final Provider<Object> bProvider;",
        "  private final MembersInjector<X> xMembersInjector;",
        "",
        "  public TestModule_ProvideObjectsFactory(",
        "      TestModule module,",
        "      Provider<Object> aProvider,",
        "      Provider<Object> bProvider,",
        "      MembersInjector<X> xMembersInjector) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert aProvider != null;",
        "    this.aProvider = aProvider;",
        "    assert bProvider != null;",
        "    this.bProvider = bProvider;",
        "    assert xMembersInjector != null;",
        "    this.xMembersInjector = xMembersInjector;",
        "  }",
        "",
        "  @Override public List<Object> get() {",
        "    List<Object> provided =",
        "        module.provideObjects(aProvider.get(), bProvider.get(), xMembersInjector);",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static Factory<List<Object>> create(",
        "      TestModule module,",
        "      Provider<Object> aProvider,",
        "      Provider<Object> bProvider,",
        "      MembersInjector<X> xMembersInjector) {",
        "    return new TestModule_ProvideObjectsFactory(",
        "        module, aProvider, bProvider, xMembersInjector);",
        "  }",
        "}");
    assertAbout(javaSources()).that(
            ImmutableList.of(classXFile, moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(listFactoryFile);
  }

  @Test public void providesSetElement() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET;",
        "",
        "import java.util.logging.Logger;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET) String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProvideStringFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideStringFactory implements Factory<Set<String>> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public Set<String> get() {",
        "    return Collections.<String>singleton(module.provideString());",
        "  }",
        "",
        "  public static Factory<Set<String>> create(TestModule module) {",
        "    return new TestModule_ProvideStringFactory(module);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void providesSetElementWildcard() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET;",
        "",
        "import java.util.logging.Logger;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET) List<List<?>> provideWildcardList() {",
        "    return new ArrayList<>();",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines(
        "TestModule_ProvideWildcardListFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import java.util.Collections;",
        "import java.util.List;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideWildcardListFactory implements "
            + "Factory<Set<List<List<?>>>> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideWildcardListFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public Set<List<List<?>>> get() {",
        "    return Collections.<List<List<?>>>singleton(module.provideWildcardList());",
        "  }",
        "",
        "  public static Factory<Set<List<List<?>>>> create(TestModule module) {",
        "    return new TestModule_ProvideWildcardListFactory(module);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void providesSetValues() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProvideStringsFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProvideStringsFactory implements Factory<Set<String>> {",
        "  private final TestModule module;",
        "",
        "  public TestModule_ProvideStringsFactory(TestModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override public Set<String> get() {",
        "    Set<String> provided = module.provideStrings();",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static Factory<Set<String>> create(TestModule module) {",
        "    return new TestModule_ProvideStringsFactory(module);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void multipleProvidesMethodsWithSameName() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides Object provide(int i) {",
        "    return i;",
        "  }",
        "",
        "  @Provides String provide() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_WITH_SAME_NAME)).in(moduleFile).onLine(8)
        .and().withErrorContaining(formatErrorMessage(BINDING_METHOD_WITH_SAME_NAME))
        .in(moduleFile).onLine(12);
  }

  @Test
  public void providedTypes() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.io.Closeable;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String string() {",
        "    return null;",
        "  }",
        "",
        "  @Provides Set<String> strings() {",
        "    return null;",
        "  }",
        "",
        "  @Provides Set<? extends Closeable> closeables() {",
        "    return null;",
        "  }",
        "",
        "  @Provides String[] stringArray() {",
        "    return null;",
        "  }",
        "",
        "  @Provides int integer() {",
        "    return 0;",
        "  }",
        "",
        "  @Provides int[] integers() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }

  @Test
  public void privateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "final class Enclosing {",
        "  @Module private static final class PrivateModule {",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be private.")
        .in(moduleFile).onLine(6);
  }

  @Test
  public void enclosedInPrivateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "final class Enclosing {",
        "  private static final class PrivateEnclosing {",
        "    @Module static final class TestModule {",
        "    }",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be enclosed in private types.")
        .in(moduleFile).onLine(7);
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {",
        "    NonPublicModule1.class, OtherPublicModule.class, NonPublicModule2.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject nonPublicModule1File = JavaFileObjects.forSourceLines("test.NonPublicModule1",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class NonPublicModule1 {",
        "}");
    JavaFileObject nonPublicModule2File = JavaFileObjects.forSourceLines("test.NonPublicModule2",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class NonPublicModule2 {",
        "}");
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "public final class OtherPublicModule {",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(
            publicModuleFile, nonPublicModule1File, nonPublicModule2File, otherPublicModuleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("This module is public, but it includes non-public "
            + "(or effectively non-public) modules. "
            + "Either reduce the visibility of this module or make "
            + "test.NonPublicModule1 and test.NonPublicModule2 public.")
        .in(publicModuleFile).onLine(8);
  }

  @Test
  public void genericSubclassedModule() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.ParentModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.List;",
        "import java.util.ArrayList;",
        "",
        "@Module",
        "abstract class ParentModule<A extends CharSequence,",
        "                            B,",
        "                            C extends Number & Comparable<C>> {",
        "  @Provides List<B> provideListB(B b) {",
        "    List<B> list = new ArrayList<B>();",
        "    list.add(b);",
        "    return list;",
        "  }",
        "}");
    JavaFileObject numberChild = JavaFileObjects.forSourceLines("test.ChildNumberModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildNumberModule extends ParentModule<String, Number, Double> {",
        "  @Provides Number provideNumber() { return 1; }",
        "}");
    JavaFileObject integerChild = JavaFileObjects.forSourceLines("test.ChildIntegerModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildIntegerModule extends ParentModule<StringBuilder, Integer, Float> {",
        "  @Provides Integer provideInteger() { return 2; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.List;",
        "",
        "@Component(modules={ChildNumberModule.class, ChildIntegerModule.class})",
        "interface C {",
        "  List<Number> numberList();",
        "  List<Integer> integerList();",
        "}");
    JavaFileObject listBFactory = JavaFileObjects.forSourceLines(
        "test.ParentModule_ProvidesListBFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import java.util.List;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class ParentModule_ProvideListBFactory<A extends CharSequence,",
        "    B, C extends Number & Comparable<C>> implements Factory<List<B>> {",
        "  private final ParentModule<A, B, C> module;",
        "  private final Provider<B> bProvider;",
        "",
        "  public ParentModule_ProvideListBFactory(",
        "        ParentModule<A, B, C> module, Provider<B> bProvider) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert bProvider != null;",
        "    this.bProvider = bProvider;",
        "  }",
        "",
        "  @Override",
        "  public List<B> get() {  ",
        "    List<B> provided = module.provideListB(bProvider.get());",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
        "      Factory<List<B>> create(ParentModule<A, B, C> module, Provider<B> bProvider) {",
        "    return new ParentModule_ProvideListBFactory<A, B, C>(module, bProvider);",
        "  }",
        "}");
    JavaFileObject numberFactory = JavaFileObjects.forSourceLines(
        "test.ChildNumberModule_ProvideNumberFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class ChildNumberModule_ProvideNumberFactory implements Factory<Number> {",
        "  private final ChildNumberModule module;",
        "",
        "  public ChildNumberModule_ProvideNumberFactory(ChildNumberModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override",
        "  public Number get() {  ",
        "    Number provided = module.provideNumber();",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static Factory<Number> create(ChildNumberModule module) {",
        "    return new ChildNumberModule_ProvideNumberFactory(module);",
        "  }",
        "}");
    JavaFileObject integerFactory = JavaFileObjects.forSourceLines(
        "test.ChildIntegerModule_ProvideIntegerFactory",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class ChildIntegerModule_ProvideIntegerFactory",
        "    implements Factory<Integer> {",
        "  private final ChildIntegerModule module;",
        "",
        "  public ChildIntegerModule_ProvideIntegerFactory(ChildIntegerModule module) {",
        "    assert module != null;",
        "    this.module = module;",
        "  }",
        "",
        "  @Override",
        "  public Integer get() {  ",
        "    Integer provided = module.provideInteger();",
        "    if (provided == null) {",
        "      throw new NullPointerException(" + NPE_LITERAL + ");",
        "    }",
        "    return provided;",
        "  }",
        "",
        "  public static Factory<Integer> create(ChildIntegerModule module) {",
        "    return new ChildIntegerModule_ProvideIntegerFactory(module);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(parent, numberChild, integerChild, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(listBFactory, numberFactory, integerFactory);
  }

  @Test public void providesMethodMultipleQualifiers() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import javax.annotation.Nullable;",
        "import javax.inject.Singleton;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @QualifierA @QualifierB String provideString() {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS);
  }
}
