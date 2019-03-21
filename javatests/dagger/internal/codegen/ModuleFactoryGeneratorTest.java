
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
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_PROVIDES_METHOD;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleFactoryGeneratorTest {

  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", "package test;", "public @interface Nullable {}");

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

  @Test public void providesMethodSetValuesNotASet() {
    assertThatModuleMethod(
            "@Provides @ElementsIntoSet List<String> provideStrings() { return null; }")
        .hasError("@Provides methods annotated with @ElementsIntoSet must return a Set");
  }

  @Test public void modulesWithTypeParamsMustBeAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule<A> {}");
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules with type parameters must be abstract")
        .inFile(moduleFile)
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
            "Binding methods may not be overridden in modules. Overrides: "
                + "@Provides String test.Parent.foo()");
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
            "Binding methods may not override another method. Overrides: "
                + "@Provides String test.Parent.foo()");
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
            "Binding methods may not override another method. Overrides: "
                + "String test.Parent.foo()");
  }

  @Test public void validatesIncludedModules() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = Void.class)",
        "class TestModule {}");

    Compilation compilation = daggerCompiler().compile(module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Void is listed as a module, but is not annotated with @Module");
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
            "    return provideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String provideString(TestModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideString(), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String provideString(TestModule instance) {",
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
            "    return provideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  @Nullable",
            "  public static String provideString(TestModule instance) {",
            "    return instance.provideString();",
            "  }",
            "}");
    assertAbout(javaSources()).that(ImmutableList.of(moduleFile, NULLABLE))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

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
            "    return provideObjects(",
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
            "  public static List<Object> provideObjects(",
            "      TestModule instance, Object a, Object b, MembersInjector<X> xInjector) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideObjects(a, b, xInjector), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideString(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringFactory(module);",
            "  }",
            "",
            "  public static String provideString(TestModule instance) {",
            "    return Preconditions.checkNotNull(instance.provideString(), "
                + NPE_FROM_PROVIDES_METHOD
                + ");",
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
            "    return provideWildcardList(module);",
            "  }",
            "",
            "  public static TestModule_ProvideWildcardListFactory create(TestModule module) {",
            "    return new TestModule_ProvideWildcardListFactory(module);",
            "  }",
            "",
            "  public static List<List<?>> provideWildcardList(TestModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideWildcardList(), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideStrings(module);",
            "  }",
            "",
            "  public static TestModule_ProvideStringsFactory create(TestModule module) {",
            "    return new TestModule_ProvideStringsFactory(module);",
            "  }",
            "",
            "  public static Set<String> provideStrings(TestModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideStrings(), " + NPE_FROM_PROVIDES_METHOD + ");",
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot have more than one binding method with the same name in a single module")
        .inFile(moduleFile)
        .onLine(8);
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot have more than one binding method with the same name in a single module")
        .inFile(moduleFile)
        .onLine(12);
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Provides methods may only throw unchecked exceptions")
        .inFile(moduleFile)
        .onLine(8);
    assertThat(compilation)
        .hadErrorContaining("@Provides methods may only throw unchecked exceptions")
        .inFile(moduleFile)
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).succeeded();
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be private")
        .inFile(moduleFile)
        .onLine(6);
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be enclosed in private types")
        .inFile(moduleFile)
        .onLine(7);
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {",
        "    BadNonPublicModule.class, OtherPublicModule.class, OkNonPublicModule.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject badNonPublicModuleFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject okNonPublicModuleFile = JavaFileObjects.forSourceLines("test.OkNonPublicModule",
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
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "public final class OtherPublicModule {",
        "}");
    Compilation compilation =
        daggerCompiler()
            .compile(
                publicModuleFile,
                badNonPublicModuleFile,
                okNonPublicModuleFile,
                otherPublicModuleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "This module is public, but it includes non-public (or effectively non-public) modules "
                + "(test.BadNonPublicModule) that have non-static, non-abstract binding methods. "
                + "Either reduce the visibility of this module, make the included modules public, "
                + "or make all of the binding methods on the included modules abstract or static.")
        .inFile(publicModuleFile)
        .onLine(8);
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
            "    return provideListB(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideListBFactory<A, B, C>  create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideListBFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>> List<B>",
            "      provideListB(ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideListB(b), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideBElement(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideBElementFactory<A, B, C> create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideBElementFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B provideBElement(",
            "          ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideBElement(b), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideBEntry(module, bProvider.get());",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      ParentModule_ProvideBEntryFactory<A, B, C> create(",
            "          ParentModule<A, B, C> module, Provider<B> bProvider) {",
            "    return new ParentModule_ProvideBEntryFactory<A, B, C>(module, bProvider);",
            "  }",
            "",
            "  public static <A extends CharSequence, B, C extends Number & Comparable<C>>",
            "      B provideBEntry(",
            "          ParentModule<A, B, C> instance, B b) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideBEntry(b), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideNumber(module);",
            "  }",
            "",
            "  public static ChildNumberModule_ProvideNumberFactory create(",
            "      ChildNumberModule module) {",
            "    return new ChildNumberModule_ProvideNumberFactory(module);",
            "  }",
            "",
            "  public static Number provideNumber(ChildNumberModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideNumber(), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideInteger(module);",
            "  }",
            "",
            "  public static ChildIntegerModule_ProvideIntegerFactory create(",
            "      ChildIntegerModule module) {",
            "    return new ChildIntegerModule_ProvideIntegerFactory(module);",
            "  }",
            "",
            "  public static Integer provideInteger(ChildIntegerModule instance) {",
            "    return Preconditions.checkNotNull(",
            "        instance.provideInteger(), " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideMapStringNumber();",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideMapStringNumberFactory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static Map<String, Number> provideMapStringNumber() {",
            "    return Preconditions.checkNotNull(ParameterizedModule.provideMapStringNumber(),",
            "        " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideNonGenericType();",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideNonGenericTypeFactory create() {",
            "    return INSTANCE;",
            "  }",
            "",
            "  public static Object provideNonGenericType() {",
            "    return Preconditions.checkNotNull(ParameterizedModule.provideNonGenericType(),",
            "        " + NPE_FROM_PROVIDES_METHOD + ");",
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
            "    return provideNonGenericTypeWithDeps(oProvider.get());",
            "  }",
            "",
            "  public static ParameterizedModule_ProvideNonGenericTypeWithDepsFactory create(",
            "      Provider<Object> oProvider) {",
            "    return new ParameterizedModule_ProvideNonGenericTypeWithDepsFactory(oProvider);",
            "  }",
            "",
            "  public static String provideNonGenericTypeWithDeps(Object o) {",
            "    return Preconditions.checkNotNull(",
            "        ParameterizedModule.provideNonGenericTypeWithDeps(o),",
            "        " + NPE_FROM_PROVIDES_METHOD + ");",
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

  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines(
          "test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");

  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines(
          "test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");

  @Test
  public void providesMethodMultipleQualifiersOnMethod() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides @QualifierA @QualifierB String provideString() {",
        "    return \"foo\";",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("may not use more than one @Qualifier");
  }

  @Test
  public void providesMethodMultipleQualifiersOnParameter() {
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
            "  @Provides static String provideString(@QualifierA @QualifierB Object object) {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("may not use more than one @Qualifier");
  }

  @Test
  public void providesMethodWildcardDependency() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(moduleFile, QUALIFIER_A, QUALIFIER_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> "
                + "when T is a wildcard type such as ? extends java.lang.Number");
  }

  private static final JavaFileObject SCOPE_A =
      JavaFileObjects.forSourceLines(
          "test.ScopeA",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeA {}");

  private static final JavaFileObject SCOPE_B =
      JavaFileObjects.forSourceLines(
          "test.ScopeB",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "",
          "@Scope @interface ScopeB {}");

  @Test
  public void providesMethodMultipleScopes() {
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
            "  @Provides",
            "  @ScopeA",
            "  @ScopeB",
            "  String provideString() {",
            "    return \"foo\";",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(moduleFile, SCOPE_A, SCOPE_B);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cannot use more than one @Scope")
        .inFile(moduleFile)
        .onLineContaining("@ScopeA");
    assertThat(compilation)
        .hadErrorContaining("cannot use more than one @Scope")
        .inFile(moduleFile)
        .onLineContaining("@ScopeB");
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Producer may only be injected in @Produces methods");
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Produced may only be injected in @Produces methods");
  }

  @Test
  public void proxyMethodsConflictWithOtherFactoryMethods() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(module);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_GetFactory")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.TestModule_GetFactory",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class TestModule_GetFactory implements Factory<Integer> {",
                "  @Override",
                "  public Integer get() {",
                "    return proxyGet();",
                "  }",
                "",
                "  public static TestModule_GetFactory create() {",
                "    return INSTANCE;",
                "  }",
                "",
                "  public static int proxyGet() {",
                "    return TestModule.get();",
                "  }",
                "}"));

    assertThat(compilation)
        .generatedSourceFile("test.TestModule_CreateFactory")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.TestModule_CreateFactory",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class TestModule_CreateFactory implements Factory<Boolean> {",
                "  @Override",
                "  public Boolean get() {",
                "    return proxyCreate();",
                "  }",
                "",
                "  public static TestModule_CreateFactory create() {",
                "    return INSTANCE;",
                "  }",
                "",
                "  public static boolean proxyCreate() {",
                "    return TestModule.create();",
                "  }",
                "}"));
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
    Compilation compilation = compileMethodCombination(BINDS_METHOD, INSTANCE_PROVIDES_METHOD);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "A @Module may not contain both non-static and abstract binding methods");
  }

  @Test
  public void multibindsWithInstanceProvides() {
    Compilation compilation = compileMethodCombination(MULTIBINDS_METHOD, INSTANCE_PROVIDES_METHOD);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "A @Module may not contain both non-static and abstract binding methods");
  }

  @Test
  public void bindsWithStaticProvides() {
    assertThat(compileMethodCombination(BINDS_METHOD, STATIC_PROVIDES_METHOD)).succeeded();
  }

  @Test
  public void bindsWithMultibinds() {
    assertThat(compileMethodCombination(BINDS_METHOD, MULTIBINDS_METHOD)).succeeded();
  }

  @Test
  public void multibindsWithStaticProvides() {
    assertThat(compileMethodCombination(MULTIBINDS_METHOD, STATIC_PROVIDES_METHOD)).succeeded();
  }

  @Test
  public void instanceProvidesWithAbstractMethod() {
    assertThat(compileMethodCombination(INSTANCE_PROVIDES_METHOD, SOME_ABSTRACT_METHOD))
        .succeeded();
  }

  private Compilation compileMethodCombination(String... methodLines) {
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
    return daggerCompiler()
        .compile(
            fooFile, fooImplFile, barFile, bazFile, bindsMethodAndInstanceProvidesMethodModuleFile);
  }
}
