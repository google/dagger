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


import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.compile.CompilerTests.DaggerCompiler;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ModuleFactoryGeneratorTest {

  private static final Source NON_TYPE_USE_NULLABLE =
      CompilerTests.javaSource(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ModuleFactoryGeneratorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private DaggerModuleMethodSubject assertThatMethodInUnannotatedClass(String method) {
    return DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass(method)
        .withProcessorOptions(compilerMode.processorOptions());
  }

  private DaggerModuleMethodSubject assertThatModuleMethod(String method) {
    return DaggerModuleMethodSubject.Factory.assertThatModuleMethod(method)
        .withProcessorOptions(compilerMode.processorOptions());
  }

  private DaggerCompiler daggerCompiler(Source... sources) {
    return CompilerTests.daggerCompiler(sources)
        .withProcessingOptions(compilerMode.processorOptions());
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  // TODO(gak): add tests for invalid combinations of scope and qualifier annotations like we have
  // for @Inject

  @Test public void providesMethodNotInModule() {
    assertThatMethodInUnannotatedClass("@Provides String provideString() { return null; }")
        .hasError("@Provides methods can only be present within a @Module or @ProducerModule");
  }

  @Test public void providesMethodAbstract() {
    assertThatModuleMethod("@Provides abstract String abstractMethod();")
        .hasError("@Provides methods cannot be abstract");
  }

  @Test public void providesMethodPrivate() {
    assertThatModuleMethod("@Provides private String privateMethod() { return null; }")
        .hasError("@Provides methods cannot be private");
  }

  @Test public void providesMethodReturnVoid() {
    assertThatModuleMethod("@Provides void voidMethod() {}")
        .hasError("@Provides methods must return a value (not void)");
  }

  @Test
  public void providesMethodReturnsProvider() {
    assertThatModuleMethod("@Provides Provider<String> provideProvider() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  public void providesMethodReturnsJakartaProvider() {
    assertThatModuleMethod("@Provides jakarta.inject.Provider<String> provideProvider() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  public void providesMethodReturnsDaggerInternalProvider() {
    assertThatModuleMethod("@Provides dagger.internal.Provider<String> provideProvider() {}")
        .hasError("@Provides methods must not return disallowed types");
  }

  @Test
  public void providesIntoSetMethodReturnsDaggerInternalProvider() {
    assertThatModuleMethod(
        "@Provides @IntoSet dagger.internal.Provider<String> provideProvider() {}")
        .hasError("@Provides methods must not return disallowed types");
  }

  @Test
  public void providesMethodReturnsLazy() {
    assertThatModuleMethod("@Provides Lazy<String> provideLazy() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  public void providesMethodReturnsMembersInjector() {
    assertThatModuleMethod("@Provides MembersInjector<String> provideMembersInjector() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  public void providesMethodReturnsProducer() {
    assertThatModuleMethod("@Provides Producer<String> provideProducer() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test
  public void providesMethodReturnsProduced() {
    assertThatModuleMethod("@Provides Produced<String> provideProduced() {}")
        .hasError("@Provides methods must not return framework types");
  }

  @Test public void providesMethodWithTypeParameter() {
    assertThatModuleMethod("@Provides <T> String typeParameter() { return null; }")
        .hasError("@Provides methods may not have type parameters");
  }

  @Test public void providesMethodSetValuesWildcard() {
    assertThatModuleMethod("@Provides @ElementsIntoSet Set<?> provideWildcard() { return null; }")
        .hasError(
            "@Provides methods must return a primitive, an array, a type variable, "
                + "or a declared type");
  }

  @Test public void providesMethodSetValuesRawSet() {
    assertThatModuleMethod("@Provides @ElementsIntoSet Set provideSomething() { return null; }")
        .hasError("@Provides methods annotated with @ElementsIntoSet cannot return a raw Set");
  }

  @Test public void providesElementsIntoSetMethodReturnsSetDaggerProvider() {
    assertThatModuleMethod(
        "@Provides @ElementsIntoSet Set<dagger.internal.Provider<String>> provideProvider() {}")
        .hasError("@Provides methods must not return disallowed types");
  }

  @Test public void providesMethodSetValuesNotASet() {
    assertThatModuleMethod(
            "@Provides @ElementsIntoSet List<String> provideStrings() { return null; }")
        .hasError("@Provides methods annotated with @ElementsIntoSet must return a Set");
  }

  @Test
  public void bindsMethodReturnsProvider() {
    assertThatModuleMethod("@Binds abstract Provider<Number> bindsProvider(Provider<Long> impl);")
        .hasError("@Binds methods must not return framework types");
  }

  @Test
  public void bindsMethodReturnsDaggerProvider() {
    assertThatModuleMethod("@Binds abstract dagger.internal.Provider<Number> "
        + "bindsProvider(dagger.internal.Provider<Long> impl);")
        .hasError("@Binds methods must not return disallowed types");
  }

  @Test public void modulesWithTypeParamsMustBeAbstract() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "final class TestModule<A> {}");
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Modules with type parameters must be abstract")
                  .onSource(moduleFile)
                  .onLine(6);
            });
  }

  @Test public void provideOverriddenByNoProvide() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
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
            "Binding methods may not be overridden in modules. Overrides: "
                + "@Provides String test.Parent.foo()");
  }

  @Test public void provideOverriddenByProvide() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
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
            "Binding methods may not override another method. Overrides: "
                + "@Provides String test.Parent.foo()");
  }

  @Test public void providesOverridesNonProvides() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
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
            "Binding methods may not override another method. Overrides: "
                + "String test.Parent.foo()");
  }

  @Test public void validatesIncludedModules() {
    Source module =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(",
            "    includes = {",
            "        Void.class,",
            "        String.class,",
            "    }",
            ")",
            "class TestModule {}");

    daggerCompiler(module)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              // We avoid asserting on the line number because ksp and javac report different lines.
              // The main issue here is that ksp doesn't allow reporting errors on individual
              // annotation values, it only allows reporting errors on annotations themselves.
              subject.hasErrorContaining(
                      "java.lang.Void is listed as a module, but is not annotated with @Module")
                  .onSource(module);
              subject.hasErrorContaining(
                      "java.lang.String is listed as a module, but is not annotated with @Module")
                  .onSource(module);
            });
  }

  @Test public void singleProvidesMethodNoArgs() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideStringFactory"));
            });
  }

  @Test public void singleProvidesMethodNoArgs_disableNullable() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .withProcessingOptions(ImmutableMap.of("dagger.nullableValidation", "WARNING"))
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideStringFactory"));
            });
  }

  @Test
  public void nonTypeUseNullableProvides() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides @Nullable String provideString() { return null; }",
            "}");
    daggerCompiler(moduleFile, NON_TYPE_USE_NULLABLE)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideStringFactory"));
            });
  }

  @Test
  public void kotlinNullableProvides() {
    Source moduleFile =
        CompilerTests.kotlinSource(
            "TestModule.kt",
            "package test",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides fun provideString(): String? { return null; }",
            "}");
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test.TestModule_ProvideStringFactory",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import dagger.internal.Factory;",
                      "import dagger.internal.QualifierMetadata;",
                      "import dagger.internal.ScopeMetadata;",
                      "import javax.annotation.processing.Generated;",
                      isJavac ? "import org.jetbrains.annotations.Nullable;\n" : "",
                      "@ScopeMetadata",
                      "@QualifierMetadata",
                      "@DaggerGenerated",
                      "@Generated(",
                      "    value = \"dagger.internal.codegen.ComponentProcessor\",",
                      "    comments = \"https://dagger.dev\"",
                      ")",
                      "@SuppressWarnings({",
                      "    \"unchecked\",",
                      "    \"rawtypes\",",
                      "    \"KotlinInternal\",",
                      "    \"KotlinInternalInJava\",",
                      "    \"cast\",",
                      "    \"deprecation\",",
                      "    \"nullness:initialization.field.uninitialized\"",
                      "})",
                      "public final class TestModule_ProvideStringFactory implements"
                          + " Factory<String> {",
                      "  private final TestModule module;",
                      "",
                      "  private TestModule_ProvideStringFactory(TestModule module) {",
                      "    this.module = module;",
                      "  }",
                      "",
                      // TODO(b/368129744): KSP should output the @Nullable annotation after this
                      // bug is fixed.
                      isJavac ? "  @Override\n  @Nullable" : "  @Override",
                      "  public String get() {",
                      "    return provideString(module);",
                      "  }",
                      "",
                      "  public static TestModule_ProvideStringFactory create(TestModule module) {",
                      "    return new TestModule_ProvideStringFactory(module);",
                      "  }",
                      // TODO(b/368129744): KSP should output the @Nullable annotation after this
                      // bug is fixed.
                      isJavac ? "\n  @Nullable" : "",
                      "  public static String provideString(TestModule instance) {",
                      "    return instance.provideString();",
                      "  }",
                      "}"));
            });
  }

  @Test public void multipleProvidesMethods() {
    Source classXFile =
        CompilerTests.javaSource("test.X",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class X {",
        "  @Inject public String s;",
        "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(classXFile, moduleFile, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideObjectsFactory"));
            });
  }

  @Test
  public void providesSetElement() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideStringFactory"));
            });
  }

  @Test public void providesSetElementWildcard() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideWildcardListFactory"));
            });
  }

  @Test public void providesSetValues() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_ProvideStringsFactory"));
            });
  }

  @Test public void multipleProvidesMethodsWithSameName() {
    Source moduleFile =
        CompilerTests.javaSource("test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "Cannot have more than one binding method with the same name in a single "
                          + "module")
                  .onSource(moduleFile)
                  .onLine(8);
              subject.hasErrorContaining(
                      "Cannot have more than one binding method with the same name in a single "
                          + "module")
                  .onSource(moduleFile)
                  .onLine(12);
            });
  }

  @Test
  public void providesMethodThrowsChecked() {
    Source moduleFile =
        CompilerTests.javaSource(
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("@Provides methods may only throw unchecked exceptions")
                  .onSource(moduleFile)
                  .onLine(8);
              subject.hasErrorContaining("@Provides methods may only throw unchecked exceptions")
                  .onSource(moduleFile)
                  .onLine(12);
            });
  }

  @Test
  public void providedTypes() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void privateModule() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.Enclosing",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "final class Enclosing {",
            "  @Module private static final class PrivateModule {",
            "  }",
            "}");
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Modules cannot be private")
                  .onSource(moduleFile)
                  .onLine(6);
            });
  }

  @Test
  public void privateModule_kotlin() {
    Source moduleFile =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Component",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "private class TestModule {",
            "  @Provides fun provideInt(): Int = 1",
            "}");

    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining("Modules cannot be private")
                  .onSource(moduleFile);
            });
  }

  @Test
  public void enclosedInPrivateModule() {
    Source moduleFile =
        CompilerTests.javaSource("test.Enclosing",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Modules cannot be enclosed in private types")
                  .onSource(moduleFile)
                  .onLine(7);
            });
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    Source publicModuleFile =
        CompilerTests.javaSource("test.PublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {",
        "    BadNonPublicModule.class, OtherPublicModule.class, OkNonPublicModule.class",
        "})",
        "public final class PublicModule {",
        "}");
    Source badNonPublicModuleFile =
        CompilerTests.javaSource(
            "test.BadNonPublicModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class BadNonPublicModule {",
            "  @Provides",
            "  int provideInt() {",
            "    return 42;",
            "  }",
            "}");
    Source okNonPublicModuleFile =
        CompilerTests.javaSource("test.OkNonPublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class OkNonPublicModule {",
        "  @Provides",
        "  static String provideString() {",
        "    return \"foo\";",
        "  }",
        "}");
    Source otherPublicModuleFile =
        CompilerTests.javaSource("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "public final class OtherPublicModule {",
        "}");
    daggerCompiler(
            publicModuleFile,
            badNonPublicModuleFile,
            okNonPublicModuleFile,
            otherPublicModuleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "This module is public, but it includes non-public (or effectively non-"
                          + "public) modules (test.BadNonPublicModule) that have non-static, non-"
                          + "abstract binding methods. Either reduce the visibility of this module"
                          + ", make the included modules public, or make all of the binding "
                          + "methods on the included modules abstract or static.")
                  .onSource(publicModuleFile)
                  .onLine(8);
            });
  }

  @Test
  public void genericSubclassedModule() {
    Source parent =
        CompilerTests.javaSource(
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
    Source numberChild =
        CompilerTests.javaSource("test.ChildNumberModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildNumberModule extends ParentModule<String, Number, Double> {",
        "  @Provides Number provideNumber() { return 1; }",
        "}");
    Source integerChild =
        CompilerTests.javaSource("test.ChildIntegerModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildIntegerModule extends ParentModule<StringBuilder, Integer, Float> {",
        "  @Provides Integer provideInteger() { return 2; }",
        "}");
    Source component =
        CompilerTests.javaSource("test.C",
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
    daggerCompiler(parent, numberChild, integerChild, component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/ParentModule_ProvideListBFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/ParentModule_ProvideBElementFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/ParentModule_ProvideBEntryFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/ChildNumberModule_ProvideNumberFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/ChildIntegerModule_ProvideIntegerFactory"));
            });
  }

  @Test public void parameterizedModuleWithStaticProvidesMethodOfGenericType() {
    Source moduleFile =
        CompilerTests.javaSource(
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "test/ParameterizedModule_ProvideMapStringNumberFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "test/ParameterizedModule_ProvideNonGenericTypeFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "test/ParameterizedModule_ProvideNonGenericTypeWithDepsFactory"));
            });
  }

  private static final Source QUALIFIER_A =
        CompilerTests.javaSource(
          "test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");

  private static final Source QUALIFIER_B =
        CompilerTests.javaSource(
          "test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");

  @Test
  public void providesMethodMultipleQualifiersOnMethod() {
    Source moduleFile =
        CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides",
        "  @QualifierA",
        "  @QualifierB",
        "  String provideString() {",
        "    return \"foo\";",
        "  }",
        "}");
    daggerCompiler(moduleFile, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              // There are 2 errors -- 1 per qualifier.
              subject.hasErrorCount(2);
              subject.hasErrorContaining("may not use more than one @Qualifier")
                  .onSource(moduleFile)
                  .onLine(9);
              subject.hasErrorContaining("may not use more than one @Qualifier")
                  .onSource(moduleFile)
                  .onLine(10);
            });
  }

  @Test
  public void providesMethodMultipleQualifiersOnParameter() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides",
            "  static String provideString(",
            "      @QualifierA",
            "      @QualifierB",
            "      Object object) {",
            "    return \"foo\";",
            "  }",
            "}");
    daggerCompiler(moduleFile, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              // There are two errors -- 1 per qualifier.
              subject.hasErrorCount(2);
              if (CompilerTests.backend(subject) == XProcessingEnv.Backend.KSP) {
                // TODO(b/381557487): KSP2 reports the error on the parameter instead of the
                // the annotation.
                subject.hasErrorContaining("may not use more than one @Qualifier")
                    .onSource(moduleFile)
                    .onLine(12);
              } else {
                subject.hasErrorContaining("may not use more than one @Qualifier")
                    .onSource(moduleFile)
                    .onLine(10);
                subject.hasErrorContaining("may not use more than one @Qualifier")
                    .onSource(moduleFile)
                    .onLine(11);
              }
            });
  }

  @Test
  public void providesMethodWildcardDependency() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides static String provideString(Provider<? extends Number> numberProvider) {",
            "    return \"foo\";",
            "  }",
            "}");
    daggerCompiler(moduleFile, QUALIFIER_A, QUALIFIER_B)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or "
                      + "Produced<T> when T is a wildcard type such as ? extends java.lang.Number");
            });
  }

  private static final Source SCOPE_A =
        CompilerTests.javaSource(
          "test.ScopeA",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");

  private static final Source SCOPE_B =
        CompilerTests.javaSource(
          "test.ScopeB",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test
  public void providesMethodMultipleScopes() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides",
            "  @ScopeA",
            "  @ScopeB",
            "  String provideString() {",
            "    return \"foo\";",
            "  }",
            "}");
    daggerCompiler(moduleFile, SCOPE_A, SCOPE_B)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("cannot use more than one @Scope")
                  .onSource(moduleFile)
                  .onLineContaining("@ScopeA");
              subject.hasErrorContaining("cannot use more than one @Scope")
                  .onSource(moduleFile)
                  .onLineContaining("@ScopeB");
            });
  }

  @Test public void providerDependsOnProduced() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Producer may only be injected in @Produces methods");
            });
  }

  @Test public void providerDependsOnProducer() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    daggerCompiler(moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Produced may only be injected in @Produces methods");
            });
  }

  @Test
  public void proxyMethodsConflictWithOtherFactoryMethods() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static int get() { return 1; }",
            "",
            "  @Provides",
            "  static boolean create() { return true; }",
            "}");

    daggerCompiler(module)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/TestModule_GetFactory"));
              subject.generatedSource(goldenFileRule.goldenSource("test/TestModule_CreateFactory"));
            });
  }

  @Test
  public void testScopedMetadataOnStaticProvides() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "interface MyModule {",
            "  @NonScope",
            "  @Singleton",
            "  @Provides",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "}");
    Source nonScope =
        CompilerTests.javaSource(
            "test.NonScope",
            "package test;",
            "",
            "@interface NonScope {}");

    daggerCompiler(module, nonScope)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MyModule_ProvideStringFactory"));
            });
  }

  @Test
  public void testScopedMetadataOnNonStaticProvides() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "class MyModule {",
            "  @NonScope",
            "  @Singleton",
            "  @Provides",
            "  String provideString() {",
            "    return \"\";",
            "  }",
            "}");
    Source nonScope =
        CompilerTests.javaSource(
            "test.NonScope",
            "package test;",
            "",
            "@interface NonScope {}");

    daggerCompiler(module, nonScope)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MyModule_ProvideStringFactory"));
            });
  }

  @Test
  public void testScopeMetadataWithCustomScope() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "interface MyModule {",
            "  @NonScope(\"someValue\")",
            "  @CustomScope(\"someOtherValue\")",
            "  @Provides",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "}");
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
    Source nonScope =
        CompilerTests.javaSource(
            "test.NonScope",
            "package test;",
            "",
            "@interface NonScope {",
            "  String value();",
            "}");

    daggerCompiler(module, customScope, nonScope)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MyModule_ProvideStringFactory"));
            });
  }

  @Test
  public void testQualifierMetadataOnProvides() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.ScopedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "interface MyModule {",
            "  @Provides",
            "  @NonQualifier",
            "  @MethodQualifier",
            "  static String provideString(@NonQualifier @ParamQualifier int i) {",
            "    return \"\";",
            "  }",
            "}");
    Source methodQualifier =
        CompilerTests.javaSource(
            "test.MethodQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface MethodQualifier {}");
    Source paramQualifier =
        CompilerTests.javaSource(
            "test.ParamQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface ParamQualifier {}");
    Source nonQualifier =
        CompilerTests.javaSource(
            "test.NonQualifier",
            "package test;",
            "",
            "@interface NonQualifier {}");

    daggerCompiler(module, methodQualifier, paramQualifier, nonQualifier)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/MyModule_ProvideStringFactory"));
            });
  }

  private static final String BINDS_METHOD = "@Binds abstract Foo bindFoo(FooImpl impl);";
  private static final String MULTIBINDS_METHOD = "@Multibinds abstract Set<Foo> foos();";
  private static final String STATIC_PROVIDES_METHOD =
      "@Provides static Bar provideBar() { return new Bar(); }";
  private static final String INSTANCE_PROVIDES_METHOD =
      "@Provides Baz provideBaz() { return new Baz(); }";
  private static final String SOME_ABSTRACT_METHOD = "abstract void blah();";

  @Test
  public void bindsWithInstanceProvides() {
    compileMethodCombination(BINDS_METHOD, INSTANCE_PROVIDES_METHOD)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "A @Module may not contain both non-static and abstract binding methods");
            });
  }

  @Test
  public void multibindsWithInstanceProvides() {
    compileMethodCombination(MULTIBINDS_METHOD, INSTANCE_PROVIDES_METHOD)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "A @Module may not contain both non-static and abstract binding methods");
            });
  }

  @Test
  public void bindsWithStaticProvides() {
    compileMethodCombination(BINDS_METHOD, STATIC_PROVIDES_METHOD)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void bindsWithMultibinds() {
    compileMethodCombination(BINDS_METHOD, MULTIBINDS_METHOD)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void multibindsWithStaticProvides() {
    compileMethodCombination(MULTIBINDS_METHOD, STATIC_PROVIDES_METHOD)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void instanceProvidesWithAbstractMethod() {
    compileMethodCombination(INSTANCE_PROVIDES_METHOD, SOME_ABSTRACT_METHOD)
        .compile(subject -> subject.hasErrorCount(0));
  }

  private CompilerTests.DaggerCompiler compileMethodCombination(String... methodLines) {
    Source fooFile =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source fooImplFile =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");
    Source barFile =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "final class Bar {}");
    Source bazFile =
        CompilerTests.javaSource(
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

    Source bindsMethodAndInstanceProvidesMethodModuleFile =
        CompilerTests.javaSource("test.TestModule", moduleLines);
    return daggerCompiler(
        fooFile, fooImplFile, barFile, bazFile, bindsMethodAndInstanceProvidesMethodModuleFile);
  }
}
