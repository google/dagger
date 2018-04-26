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
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_THROWS_CHECKED;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.CompileTester;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.CodeBlock;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleFactoryGeneratorTest {

  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", "package test;", "public @interface Nullable {}");

  private static final CodeBlock NPE_LITERAL =
      CodeBlocks.stringLiteral(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);

  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

  private String formatErrorMessage(String msg) {
    return String.format(msg, "Provides");
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, "Provides", "Module");
  }

  @Test public void providesMethodNotInModule() {
    assertThatMethodInUnannotatedClass("@Provides String provideString() { return null; }")
        .hasError(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE));
  }

  @Test public void providesMethodAbstract() {
    assertThatModuleMethod("@Provides abstract String abstractMethod();")
        .hasError(formatErrorMessage(BINDING_METHOD_ABSTRACT));
  }

  @Test public void providesMethodPrivate() {
    assertThatModuleMethod("@Provides private String privateMethod() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_PRIVATE));
  }

  @Test public void providesMethodReturnVoid() {
    assertThatModuleMethod("@Provides void voidMethod() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
  }

  @Test
  public void providesMethodReturnsProvider() {
    assertThatModuleMethod("@Provides Provider<String> provideProvider() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void providesMethodReturnsLazy() {
    assertThatModuleMethod("@Provides Lazy<String> provideLazy() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void providesMethodReturnsMembersInjector() {
    assertThatModuleMethod("@Provides MembersInjector<String> provideMembersInjector() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void providesMethodReturnsProducer() {
    assertThatModuleMethod("@Provides Producer<String> provideProducer() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void providesMethodReturnsProduced() {
    assertThatModuleMethod("@Provides Produced<String> provideProduced() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test public void providesMethodWithTypeParameter() {
    assertThatModuleMethod("@Provides <T> String typeParameter() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
  }

  @Test public void providesMethodSetValuesWildcard() {
    assertThatModuleMethod("@Provides @ElementsIntoSet Set<?> provideWildcard() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_RETURN_TYPE));
  }

  @Test public void providesMethodSetValuesRawSet() {
    assertThatModuleMethod("@Provides @ElementsIntoSet Set provideSomething() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void providesMethodSetValuesNotASet() {
    assertThatModuleMethod(
            "@Provides @ElementsIntoSet List<String> provideStrings() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_SET_VALUES_RETURN_SET));
  }

  @Test public void modulesWithTypeParamsMustBeAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule<A> {}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MODULES_WITH_TYPE_PARAMS_MUST_BE_ABSTRACT)
        .in(moduleFile)
        .onLine(6);
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
    assertThatModuleMethod("String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            String.format(
                ErrorMessages.METHOD_OVERRIDES_PROVIDES_METHOD,
                "Provides",
                "@Provides String test.Parent.foo()"));
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
    assertThatModuleMethod("@Provides String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            String.format(
                ErrorMessages.PROVIDES_METHOD_OVERRIDES_ANOTHER,
                "Provides",
                "@Provides String test.Parent.foo()"));
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
    assertThatModuleMethod("@Provides String foo() { return null; }")
        .withDeclaration("@Module class %s extends Parent { %s }")
        .withAdditionalSources(parent)
        .hasError(
            String.format(
                ErrorMessages.PROVIDES_METHOD_OVERRIDES_ANOTHER,
                "Provides",
                "String test.Parent.foo()"));
  }

  @Test public void validatesIncludedModules() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = Void.class)",
        "class TestModule {}");
    assertAbout(javaSources())
        .that(ImmutableList.of(module))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(
                ErrorMessages.REFERENCED_MODULE_NOT_ANNOTATED, "java.lang.Void", "@Module"));
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
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideStringFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideStringFactory implements Factory<String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideStringFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override public String get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static String provideInstance(TestModule module) {",
            "    return proxyProvideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String proxyProvideString(TestModule instance) {",
            "    return Preconditions.checkNotNull(instance.provideString(), " + NPE_LITERAL + ");",
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
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideStringFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideStringFactory implements Factory<String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideStringFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override public String get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static String provideInstance(TestModule module) {",
            "    return proxyProvideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String proxyProvideString(TestModule instance) {",
            "    return instance.provideString();",
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
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideStringFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideStringFactory implements Factory<String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideStringFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override",
            "  @Nullable",
            "  public String get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  @Nullable",
            "  public static String provideInstance(TestModule module) {",
            "    return proxyProvideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  @Nullable",
            "  public static String proxyProvideString(TestModule instance) {",
            "    return instance.provideString();",
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
        "import java.util.Arrays;",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides List<Object> provideObjects(",
        "      @QualifierA Object a, @QualifierB Object b, MembersInjector<X> xInjector) {",
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
    JavaFileObject listFactoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideObjectsFactory",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.List;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideObjectsFactory",
            "    implements Factory<List<Object>> {",
            "  private final TestModule module;",
            "  private final Provider<Object> aProvider;",
            "  private final Provider<Object> bProvider;",
            "  private final Provider<MembersInjector<X>> xInjectorProvider;",
            "",
            "  public TestModule_ProvideObjectsFactory(",
            "      TestModule module,",
            "      Provider<Object> aProvider,",
            "      Provider<Object> bProvider,",
            "      Provider<MembersInjector<X>> xInjectorProvider) {",
            "    this.module = module;",
            "    this.aProvider = aProvider;",
            "    this.bProvider = bProvider;",
            "    this.xInjectorProvider = xInjectorProvider;",
            "  }",
            "",
            "  @Override public List<Object> get() {",
            "    return provideInstance(module, aProvider, bProvider, xInjectorProvider);",
            "  }",
            "",
            "  public static List<Object> provideInstance(",
            "      TestModule module,",
            "      Provider<Object> aProvider,",
            "      Provider<Object> bProvider,",
            "      Provider<MembersInjector<X>> xInjectorProvider) {",
            "    return proxyProvideObjects(",
            "        module, aProvider.get(), bProvider.get(), xInjectorProvider.get());",
            "  }",
            "",
            "  public static TestModule_ProvideObjectsFactory create(",
            "      TestModule module,",
            "      Provider<Object> aProvider,",
            "      Provider<Object> bProvider,",
            "      Provider<MembersInjector<X>> xInjectorProvider) {",
            "    return new TestModule_ProvideObjectsFactory(",
            "        module, aProvider, bProvider, xInjectorProvider);",
            "  }",
            "",
            "  public static List<Object> proxyProvideObjects(",
            "      TestModule instance, Object a, Object b, MembersInjector<X> xInjector) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideObjects(a, b, xInjector), " + NPE_LITERAL + ");",
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
        "import java.util.logging.Logger;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @IntoSet String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideStringFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideStringFactory implements Factory<String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideStringFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override public String get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static String provideInstance(TestModule module) {",
            "    return proxyProvideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String proxyProvideString(TestModule instance) {",
            "    return Preconditions.checkNotNull(instance.provideString(), " + NPE_LITERAL + ");",
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
        "import java.util.logging.Logger;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @IntoSet List<List<?>> provideWildcardList() {",
        "    return new ArrayList<>();",
        "  }",
        "}");
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideWildcardListFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.List;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideWildcardListFactory implements "
                + "Factory<List<List<?>>> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideWildcardListFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override public List<List<?>> get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static List<List<?>> provideInstance(TestModule module) {",
            "    return proxyProvideWildcardList(module);",
            "  }",
            "",
            "  public static TestModule_ProvideWildcardListFactory create(TestModule module) {",
            "    return new TestModule_ProvideWildcardListFactory(module);",
            "  }",
            "",
            "  public static List<List<?>> proxyProvideWildcardList(TestModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideWildcardList(), " + NPE_LITERAL + ");",
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
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @ElementsIntoSet Set<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProvideStringsFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProvideStringsFactory implements Factory<Set<String>> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProvideStringsFactory(TestModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override public Set<String> get() {",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static Set<String> provideInstance(TestModule module) {",
            "    return proxyProvideStrings(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringsFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringsFactory(module);",
            "  }",
            "",
            "  public static Set<String> proxyProvideStrings(TestModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideStrings(), " + NPE_LITERAL + ");",
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
  public void providesMethodThrowsChecked() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides int i() throws Exception {",
            "    return 0;",
            "  }",
            "",
            "  @Provides String s() throws Throwable {",
            "    return \"\";",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_THROWS_CHECKED))
        .in(moduleFile)
        .onLine(8)
        .and()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_THROWS_CHECKED))
        .in(moduleFile)
        .onLine(12);
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
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
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
            "",
            "  @Provides @IntoSet B provideBElement(B b) {",
            "    return b;",
            "  }",
            "",
            "  @Provides @IntoMap @StringKey(\"b\") B provideBEntry(B b) {",
            "    return b;",
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
    JavaFileObject listBFactory =
        JavaFileObjects.forSourceLines(
            "test.ParentModule_ProvideListBFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.List;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class ParentModule_ProvideListBFactory<A extends CharSequence,",
            "    B, C extends Number & Comparable<C>> implements Factory<List<B>> {",
            "  private final ParentModule<A, B, C> module;",
            "  private final Provider<B> bProvider;",
            "",
            "  public ParentModule_ProvideListBFactory(",
            "        ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    this.module = module;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  @Override",
            "  public List<B> get() {  ",
            "    return provideInstance(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      List<B> provideInstance(ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return proxyProvideListB(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideListBFactory<A, B, C>  create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideListBFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>> List<B>",
            "      proxyProvideListB(ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(instance.provideListB(b), " + NPE_LITERAL + ");",
            "  }",
            "}");
    JavaFileObject bElementFactory =
        JavaFileObjects.forSourceLines(
            "test.ParentModule_ProvideBElementFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class ParentModule_ProvideBElementFactory<A extends CharSequence,",
            "    B, C extends Number & Comparable<C>> implements Factory<B> {",
            "  private final ParentModule<A, B, C> module;",
            "  private final Provider<B> bProvider;",
            "",
            "  public ParentModule_ProvideBElementFactory(",
            "        ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    this.module = module;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  @Override",
            "  public B get() {  ",
            "    return provideInstance(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B provideInstance(ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return proxyProvideBElement(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideBElementFactory<A, B, C> create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideBElementFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B proxyProvideBElement(",
            "          ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideBElement(b), " + NPE_LITERAL + ");",
            "  }",
            "}");
    JavaFileObject bEntryFactory =
        JavaFileObjects.forSourceLines(
            "test.ParentModule_ProvideBEntryFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class ParentModule_ProvideBEntryFactory<A extends CharSequence,",
            "    B, C extends Number & Comparable<C>> implements Factory<B>> {",
            "  private final ParentModule<A, B, C> module;",
            "  private final Provider<B> bProvider;",
            "",
            "  public ParentModule_ProvideBEntryFactory(",
            "        ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    this.module = module;",
            "    this.bProvider = bProvider;",
            "  }",
            "",
            "  @Override",
            "  public B get() {  ",
            "    return provideInstance(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B provideInstance(ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return proxyProvideBEntry(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideBEntryFactory<A, B, C> create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideBEntryFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B proxyProvideBEntry(",
            "          ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideBEntry(b), " + NPE_LITERAL + ");",
            "  }",
            "}");
    JavaFileObject numberFactory =
        JavaFileObjects.forSourceLines(
            "test.ChildNumberModule_ProvideNumberFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class ChildNumberModule_ProvideNumberFactory",
            "    implements Factory<Number> {",
            "  private final ChildNumberModule module;",
            "",
            "  public ChildNumberModule_ProvideNumberFactory(ChildNumberModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override",
            "  public Number get() {  ",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static Number provideInstance(ChildNumberModule module) {",
            "    return proxyProvideNumber(module);",
            "  }",
            "",
            "  public static ChildNumberModule_ProvideNumberFactory create(",
            "      ChildNumberModule module) {",
            "    return new ChildNumberModule_ProvideNumberFactory(module);",
            "  }",
            "",
            "  public static Number proxyProvideNumber(ChildNumberModule instance) {",
            "    return Preconditions.checkNotNull(instance.provideNumber(), " + NPE_LITERAL + ");",
            "  }",
            "}");
    JavaFileObject integerFactory =
        JavaFileObjects.forSourceLines(
            "test.ChildIntegerModule_ProvideIntegerFactory",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class ChildIntegerModule_ProvideIntegerFactory",
            "    implements Factory<Integer> {",
            "  private final ChildIntegerModule module;",
            "",
            "  public ChildIntegerModule_ProvideIntegerFactory(ChildIntegerModule module) {",
            "    this.module = module;",
            "  }",
            "",
            "  @Override",
            "  public Integer get() {  ",
            "    return provideInstance(module);",
            "  }",
            "",
            "  public static Integer provideInstance(ChildIntegerModule module) {",
            "    return proxyProvideInteger(module);",
            "  }",
            "",
            "  public static ChildIntegerModule_ProvideIntegerFactory create(",
            "      ChildIntegerModule module) {",
            "    return new ChildIntegerModule_ProvideIntegerFactory(module);",
            "  }",
            "",
            "  public static Integer proxyProvideInteger(ChildIntegerModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideInteger(), " + NPE_LITERAL + ");",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(parent, numberChild, integerChild, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            listBFactory, bElementFactory, bEntryFactory, numberFactory, integerFactory);
  }

  @Test public void parameterizedModuleWithStaticProvidesMethodOfGenericType() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.List;",
            "import java.util.ArrayList;",
            "import java.util.Map;",
            "import java.util.HashMap;",
            "",
            "@Module abstract class ParameterizedModule<T> {",
            "  @Provides List<T> provideListT() {",
            "    return new ArrayList<>();",
            "  }",
            "",
            "  @Provides static Map<String, Number> provideMapStringNumber() {",
            "    return new HashMap<>();",
            "  }",
            "",
            "  @Provides static Object provideNonGenericType() {",
            "    return new Object();",
            "  }",
            "",
            "  @Provides static String provideNonGenericTypeWithDeps(Object o) {",
            "    return o.toString();",
            "  }",
            "}");

    JavaFileObject provideMapStringNumberFactory =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedModule_ProvideMapStringNumberFactory;",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class ParameterizedModule_ProvideMapStringNumberFactory",
            "    implements Factory<Map<String, Number>> {",
            "  private static final ParameterizedModule_ProvideMapStringNumberFactory INSTANCE =",
            "      new ParameterizedModule_ProvideMapStringNumberFactory();",
            "",
            "  @Override",
            "  public Map<String, Number> get() {",
            "    return provideInstance();",
            "  }",
            "",
            "  public static Map<String, Number> provideInstance() {",
            "    return proxyProvideMapStringNumber();",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideMapStringNumberFactory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static Map<String, Number> proxyProvideMapStringNumber() {",
            "    return Preconditions.checkNotNull(ParameterizedModule.provideMapStringNumber(),",
            "        " + NPE_LITERAL + ");",
            "  }",
            "}");

    JavaFileObject provideNonGenericTypeFactory =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedModule_ProvideNonGenericTypeFactory;",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class ParameterizedModule_ProvideNonGenericTypeFactory",
            "    implements Factory<Object> {",
            "  private static final ParameterizedModule_ProvideNonGenericTypeFactory INSTANCE = ",
            "      new ParameterizedModule_ProvideNonGenericTypeFactory();",
            "",
            "  @Override",
            "  public Object get() {",
            "    return provideInstance();",
            "  }",
            "",
            "  public static Object provideInstance() {",
            "    return proxyProvideNonGenericType();",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideNonGenericTypeFactory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static Object proxyProvideNonGenericType() {",
            "    return Preconditions.checkNotNull(ParameterizedModule.provideNonGenericType(),",
            "        " + NPE_LITERAL + ");",
            "  }",
            "}");

    JavaFileObject provideNonGenericTypeWithDepsFactory =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedModule_ProvideNonGenericTypeWithDepsFactory;",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class ParameterizedModule_ProvideNonGenericTypeWithDepsFactory",
            "    implements Factory<String> {",
            "  private final Provider<Object> oProvider;",
            "",
            "  public ParameterizedModule_ProvideNonGenericTypeWithDepsFactory(",
            "      Provider<Object> oProvider) {",
            "    this.oProvider = oProvider;",
            "  }",
            "",
            "  @Override",
            "  public String get() {",
            "    return provideInstance(oProvider);",
            "  }",
            "",
            "  public static String provideInstance(Provider<Object> oProvider) {",
            "    return proxyProvideNonGenericTypeWithDeps(oProvider.get());",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideNonGenericTypeWithDepsFactory create(",
            "      Provider<Object> oProvider) {",
            "    return new ParameterizedModule_ProvideNonGenericTypeWithDepsFactory(oProvider);",
            "  }",
            "",
            "  public static String proxyProvideNonGenericTypeWithDeps(Object o) {",
            "    return Preconditions.checkNotNull(",
            "        ParameterizedModule.provideNonGenericTypeWithDeps(o),",
            "        " + NPE_LITERAL + ");",
            "  }",
            "}");

    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            provideMapStringNumberFactory,
            provideNonGenericTypeFactory,
            provideNonGenericTypeWithDepsFactory);
  }

  @Test public void providesMethodMultipleQualifiers() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
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
        .withErrorContaining(BINDING_METHOD_MULTIPLE_QUALIFIERS);
  }

  @Test public void providerDependsOnProduced() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.Producer;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString(Producer<Integer> producer) {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Producer may only be injected in @Produces methods");
  }

  @Test public void providerDependsOnProducer() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.Produced;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString(Produced<Integer> produced) {",
        "    return \"foo\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Produced may only be injected in @Produces methods");
  }

  private static final String BINDS_METHOD = "@Binds abstract Foo bindFoo(FooImpl impl);";
  private static final String MULTIBINDS_METHOD = "@Multibinds abstract Set<Foo> foos();";
  private static final String STATIC_PROVIDES_METHOD =
      "@Provides static Bar provideBar() { return new Bar(); }";
  private static final String INSTANCE_PROVIDES_METHOD =
      "@Provides Baz provideBaz() { return new Baz(); }";
  private static final String SOME_ABSTRACT_METHOD = "abstract void blah();";

  @Test
  public void moduleMethodPermutations() {
    assertThatMethodCombination(BINDS_METHOD, INSTANCE_PROVIDES_METHOD)
        .failsToCompile()
        .withErrorContaining(
            "A @Module may not contain both non-static @Provides methods and "
                + "abstract @Binds or @Multibinds declarations");
    assertThatMethodCombination(MULTIBINDS_METHOD, INSTANCE_PROVIDES_METHOD)
        .failsToCompile()
        .withErrorContaining(
            "A @Module may not contain both non-static @Provides methods and "
                + "abstract @Binds or @Multibinds declarations");
    assertThatMethodCombination(BINDS_METHOD, STATIC_PROVIDES_METHOD).compilesWithoutError();
    assertThatMethodCombination(BINDS_METHOD, MULTIBINDS_METHOD).compilesWithoutError();
    assertThatMethodCombination(MULTIBINDS_METHOD, STATIC_PROVIDES_METHOD).compilesWithoutError();
    assertThatMethodCombination(INSTANCE_PROVIDES_METHOD, SOME_ABSTRACT_METHOD)
        .compilesWithoutError();
  }

  private CompileTester assertThatMethodCombination(String... methodLines) {
    JavaFileObject fooFile =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    JavaFileObject fooImplFile =
        JavaFileObjects.forSourceLines(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");
    JavaFileObject barFile =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "final class Bar {}");
    JavaFileObject bazFile =
        JavaFileObjects.forSourceLines(
            "test.Baz",
            "package test;",
            "",
            "final class Baz {}");

    ImmutableList<String> moduleLines =
        new ImmutableList.Builder<String>()
            .add(
                "package test;",
                "",
                "import dagger.Binds;",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.Multibinds;",
                "import java.util.Set;",
                "",
                "@Module abstract class TestModule {")
            .add(methodLines)
            .add("}")
            .build();

    JavaFileObject bindsMethodAndInstanceProvidesMethodModuleFile =
        JavaFileObjects.forSourceLines("test.TestModule", moduleLines);
    return assertThat(
            fooFile, fooImplFile, barFile, bazFile, bindsMethodAndInstanceProvidesMethodModuleFile)
        .processedWith(new ComponentProcessor());
  }
}
