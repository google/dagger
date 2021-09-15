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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapBindingComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MapBindingComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapBindingsWithEnumKey() {
    JavaFileObject mapModuleOneFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleOne",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoMap;",
                "",
                "@Module",
                "final class MapModuleOne {",
                "  @Provides @IntoMap @PathKey(PathEnum.ADMIN) Handler provideAdminHandler() {",
                "    return new AdminHandler();",
                "  }",
                "}");
    JavaFileObject mapModuleTwoFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleTwo",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoMap;",
                "",
                "@Module",
                "final class MapModuleTwo {",
                "  @Provides @IntoMap @PathKey(PathEnum.LOGIN) Handler provideLoginHandler() {",
                "    return new LoginHandler();",
                "  }",
                "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = true)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");

    JavaFileObject handlerFile =
        JavaFileObjects.forSourceLines("test.Handler", "package test;", "", "interface Handler {}");
    JavaFileObject loginHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    JavaFileObject adminHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Provider<Map<PathEnum, Provider<Handler>>> dispatcher();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                mapModuleOneFile,
                mapModuleTwoFile,
                enumKeyFile,
                pathEnumFile,
                handlerFile,
                loginHandlerFile,
                adminHandlerFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  private final DaggerTestComponent testComponent = this;",
                    "  private Provider<Handler> provideAdminHandlerProvider;",
                    "  private Provider<Handler> provideLoginHandlerProvider;",
                    "  private Provider<Map<PathEnum, Provider<Handler>>>",
                    "      mapOfPathEnumAndProviderOfHandlerProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(",
                    "      final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        MapModuleOne_ProvideAdminHandlerFactory.create(mapModuleOneParam);",
                    "    this.provideLoginHandlerProvider =",
                    "        MapModuleTwo_ProvideLoginHandlerFactory.create(mapModuleTwoParam);",
                    "    this.mapOfPathEnumAndProviderOfHandlerProvider =",
                    "        MapProviderFactory.<PathEnum, Handler>builder(2)",
                    "            .put(PathEnum.ADMIN, provideAdminHandlerProvider)",
                    "            .put(PathEnum.LOGIN, provideLoginHandlerProvider)",
                    "            .build();",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Map<PathEnum, Provider<Handler>>",
                    "      mapOfPathEnumAndProviderOfHandler() {",
                    "    return ImmutableMap.<PathEnum, Provider<Handler>>of(",
                    "        PathEnum.ADMIN, provideAdminHandlerProvider,",
                    "        PathEnum.LOGIN, provideLoginHandlerProvider);",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 1);",
                    "    this.provideLoginHandlerProvider =",
                    "       new SwitchingProvider<>(testComponent, 2);",
                    "    this.mapOfPathEnumAndProviderOfHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 0);",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Provider<Map<PathEnum, Provider<Handler>>> dispatcher() {",
                    "    return mapOfPathEnumAndProviderOfHandlerProvider;",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) testComponent",
                    "            .mapOfPathEnumAndProviderOfHandler();",
                    "        case 1: return (T) MapModuleOne_ProvideAdminHandlerFactory",
                    "            .provideAdminHandler(testComponent.mapModuleOne);",
                    "        case 2: return (T) MapModuleTwo_ProvideLoginHandlerFactory",
                    "            .provideLoginHandler(testComponent.mapModuleTwo);",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void mapBindingsWithInaccessibleKeys() {
    JavaFileObject mapKeys =
        JavaFileObjects.forSourceLines(
            "mapkeys.MapKeys",
            "package mapkeys;",
            "",
            "import dagger.MapKey;",
            "import dagger.multibindings.ClassKey;",
            "",
            "public class MapKeys {",
            "  @MapKey(unwrapValue = false)",
            "  public @interface ComplexKey {",
            "    Class<?>[] manyClasses();",
            "    Class<?> oneClass();",
            "    ClassKey annotation();",
            "  }",
            "",
            "  @MapKey",
            "  @interface EnumKey {",
            "    PackagePrivateEnum value();",
            "  }",
            "",
            "  enum PackagePrivateEnum { INACCESSIBLE }",
            "",
            "  interface Inaccessible {}",
            "}");
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "mapkeys.MapModule",
            "package mapkeys;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ClassKey;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "public interface MapModule {",
            "  @Provides @IntoMap @ClassKey(MapKeys.Inaccessible.class)",
            "  static int classKey() { return 1; }",
            "",
            "  @Provides @IntoMap @MapKeys.EnumKey(MapKeys.PackagePrivateEnum.INACCESSIBLE)",
            "  static int enumKey() { return 1; }",
            "",
            "  @Binds Object bindInaccessibleEnumMapToAccessibleTypeForComponent(",
            "    Map<MapKeys.PackagePrivateEnum, Integer> map);",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {java.lang.Object.class, java.lang.String.class},",
            "    oneClass = MapKeys.Inaccessible.class,",
            "    annotation = @ClassKey(java.lang.Object.class)",
            "  )",
            "  static int complexKeyWithInaccessibleValue() { return 1; }",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {MapKeys.Inaccessible.class, java.lang.String.class},",
            "    oneClass = java.lang.String.class,",
            "    annotation = @ClassKey(java.lang.Object.class)",
            "  )",
            "  static int complexKeyWithInaccessibleArrayValue() { return 1; }",
            "",
            "  @Provides @IntoMap",
            "  @MapKeys.ComplexKey(",
            "    manyClasses = {java.lang.String.class},",
            "    oneClass = java.lang.String.class,",
            "    annotation = @ClassKey(MapKeys.Inaccessible.class)",
            "  )",
            "  static int complexKeyWithInaccessibleAnnotationValue() { return 1; }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "import mapkeys.MapKeys;",
            "import mapkeys.MapModule;",
            "",
            "@Component(modules = MapModule.class)",
            "interface TestComponent {",
            "  Map<Class<?>, Integer> classKey();",
            "  Provider<Map<Class<?>, Integer>> classKeyProvider();",
            "",
            "  Object inaccessibleEnum();",
            "  Provider<Object> inaccessibleEnumProvider();",
            "",
            "  Map<MapKeys.ComplexKey, Integer> complexKey();",
            "  Provider<Map<MapKeys.ComplexKey, Integer>> complexKeyProvider();",
            "}");
    Compilation compilation = daggerCompiler().compile(mapKeys, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GeneratedLines.generatedAnnotations(),
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<Map<Class<?>, Integer>> mapOfClassOfAndIntegerProvider;",
                "",
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider mapOfPackagePrivateEnumAndIntegerProvider;",
                "",
                "  private Provider<Map<MapKeys.ComplexKey, Integer>>",
                "      mapOfComplexKeyAndIntegerProvider;",
                "",
                "  private Map mapOfPackagePrivateEnumAndInteger() {",
                "    return ImmutableMap.of(",
                "        MapModule_EnumKeyMapKey.create(), MapModule.enumKey());",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.mapOfClassOfAndIntegerProvider =",
                "        MapFactory.<Class<?>, Integer>builder(1)",
                "            .put(MapModule_ClassKeyMapKey.create(),",
                "                 MapModule_ClassKeyFactory.create())",
                "            .build();",
                "    this.mapOfPackagePrivateEnumAndIntegerProvider =",
                "        MapFactory.builder(1)",
                "            .put(MapModule_EnumKeyMapKey.create(), ",
                "                 (Provider) MapModule_EnumKeyFactory.create())",
                "            .build();",
                "    this.mapOfComplexKeyAndIntegerProvider =",
                "       MapFactory.<MapKeys.ComplexKey, Integer>builder(3)",
                "          .put(",
                "             MapModule_ComplexKeyWithInaccessibleValueMapKey.create(),",
                "             MapModule_ComplexKeyWithInaccessibleValueFactory.create())",
                "          .put(",
                "             MapModule_ComplexKeyWithInaccessibleArrayValueMapKey.create(),",
                "             MapModule_ComplexKeyWithInaccessibleArrayValueFactory.create())",
                "          .put(",
                "             MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey.create(),",
                "             MapModule_ComplexKeyWithInaccessibleAnnotationValueFactory.create())",
                "          .build();",
                "  }",
                "",
                "  @Override",
                "  public Map<Class<?>, Integer> classKey() {",
                "    return ImmutableMap.<Class<?>, Integer>of(",
                "        MapModule_ClassKeyMapKey.create(), MapModule.classKey());",
                "  }",
                "",
                "  @Override",
                "  public Provider<Map<Class<?>, Integer>> classKeyProvider() {",
                "    return mapOfClassOfAndIntegerProvider;",
                "  }",
                "",
                "  @Override",
                "  public Object inaccessibleEnum() {",
                "    return mapOfPackagePrivateEnumAndInteger();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Object> inaccessibleEnumProvider() {",
                "    return mapOfPackagePrivateEnumAndIntegerProvider;",
                "  }",
                "",
                "  @Override",
                "  public Map<MapKeys.ComplexKey, Integer> complexKey() {",
                "    return ImmutableMap.<MapKeys.ComplexKey, Integer>of(",
                "        MapModule_ComplexKeyWithInaccessibleValueMapKey.create(),",
                "        MapModule.complexKeyWithInaccessibleValue(),",
                "        MapModule_ComplexKeyWithInaccessibleArrayValueMapKey.create(),",
                "        MapModule.complexKeyWithInaccessibleArrayValue(),",
                "        MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey.create(),",
                "        MapModule.complexKeyWithInaccessibleAnnotationValue());",
                "  }",
                "",
                "  @Override",
                "  public Provider<Map<MapKeys.ComplexKey, Integer>> complexKeyProvider() {",
                "    return mapOfComplexKeyAndIntegerProvider;",
                "  }",
                "}"));
    assertThat(compilation)
        .generatedSourceFile(
            "mapkeys.MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "mapkeys.MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey",
                "package mapkeys;",
                "",
                GeneratedLines.generatedAnnotations(),
                "public final class MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey {",
                "  public static MapKeys.ComplexKey create() {",
                "    return MapKeys_ComplexKeyCreator.createComplexKey(",
                "        new Class[] {String.class},",
                "        String.class,",
                "        MapKeys_ComplexKeyCreator.createClassKey(MapKeys.Inaccessible.class));",
                "  }",
                "}"));
    assertThat(compilation)
        .generatedSourceFile("mapkeys.MapModule_ClassKeyMapKey")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "mapkeys.MapModule_ClassKeyMapKey",
                "package mapkeys;",
                "",
                GeneratedLines.generatedAnnotations(),
                "public final class MapModule_ClassKeyMapKey {",
                "  public static Class<?> create() {",
                "    return MapKeys.Inaccessible.class;",
                "  }",
                "}"));
  }

  @Test
  public void mapBindingsWithStringKey() {
    JavaFileObject mapModuleOneFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleOne",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.StringKey;",
                "import dagger.multibindings.IntoMap;",
                "",
                "@Module",
                "final class MapModuleOne {",
                "  @Provides @IntoMap @StringKey(\"Admin\") Handler provideAdminHandler() {",
                "    return new AdminHandler();",
                "  }",
                "}");
    JavaFileObject mapModuleTwoFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleTwo",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoMap;",
                "import dagger.multibindings.StringKey;",
                "",
                "@Module",
                "final class MapModuleTwo {",
                "  @Provides @IntoMap @StringKey(\"Login\") Handler provideLoginHandler() {",
                "    return new LoginHandler();",
                "  }",
                "}");
    JavaFileObject handlerFile =
        JavaFileObjects.forSourceLines("test.Handler", "package test;", "", "interface Handler {}");
    JavaFileObject loginHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    JavaFileObject adminHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Provider<Map<String, Provider<Handler>>> dispatcher();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                mapModuleOneFile,
                mapModuleTwoFile,
                handlerFile,
                loginHandlerFile,
                adminHandlerFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  private final DaggerTestComponent testComponent = this;",
                    "  private Provider<Handler> provideAdminHandlerProvider;",
                    "  private Provider<Handler> provideLoginHandlerProvider;",
                    "  private Provider<Map<String, Provider<Handler>>>",
                    "      mapOfStringAndProviderOfHandlerProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(",
                    "      final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        MapModuleOne_ProvideAdminHandlerFactory.create(mapModuleOneParam);",
                    "    this.provideLoginHandlerProvider =",
                    "        MapModuleTwo_ProvideLoginHandlerFactory.create(mapModuleTwoParam);",
                    "    this.mapOfStringAndProviderOfHandlerProvider =",
                    "        MapProviderFactory.<String, Handler>builder(2)",
                    "            .put(\"Admin\", provideAdminHandlerProvider)",
                    "            .put(\"Login\", provideLoginHandlerProvider)",
                    "            .build();",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Map<String, Provider<Handler>> mapOfStringAndProviderOfHandler() {",
                    "    return ImmutableMap.<String, Provider<Handler>>of(",
                    "        \"Admin\", provideAdminHandlerProvider,",
                    "        \"Login\", provideLoginHandlerProvider);",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(",
                    "      final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 1);",
                    "    this.provideLoginHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 2);",
                    "    this.mapOfStringAndProviderOfHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 0);",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Provider<Map<String, Provider<Handler>>> dispatcher() {",
                    "    return mapOfStringAndProviderOfHandlerProvider;",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) testComponent",
                    "            .mapOfStringAndProviderOfHandler();",
                    "        case 1: return (T) MapModuleOne_ProvideAdminHandlerFactory",
                    "            .provideAdminHandler(testComponent.mapModuleOne);",
                    "        case 2: return (T) MapModuleTwo_ProvideLoginHandlerFactory",
                    "            .provideLoginHandler(testComponent.mapModuleTwo);",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void mapBindingsWithWrappedKey() {
    JavaFileObject mapModuleOneFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleOne",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoMap;",
                "",
                "@Module",
                "final class MapModuleOne {",
                "  @Provides @IntoMap",
                "  @WrappedClassKey(Integer.class) Handler provideAdminHandler() {",
                "    return new AdminHandler();",
                "  }",
                "}");
    JavaFileObject mapModuleTwoFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleTwo",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import dagger.multibindings.IntoMap;",
                "",
                "@Module",
                "final class MapModuleTwo {",
                "  @Provides @IntoMap",
                "  @WrappedClassKey(Long.class) Handler provideLoginHandler() {",
                "    return new LoginHandler();",
                "  }",
                "}");
    JavaFileObject wrappedClassKeyFile = JavaFileObjects.forSourceLines("test.WrappedClassKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface WrappedClassKey {",
        "  Class<?> value();",
        "}");
    JavaFileObject handlerFile =
        JavaFileObjects.forSourceLines("test.Handler", "package test;", "", "interface Handler {}");
    JavaFileObject loginHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    JavaFileObject adminHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Provider<Map<WrappedClassKey, Provider<Handler>>> dispatcher();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                mapModuleOneFile,
                mapModuleTwoFile,
                wrappedClassKeyFile,
                handlerFile,
                loginHandlerFile,
                adminHandlerFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  private final DaggerTestComponent testComponent = this;",
                    "  private Provider<Handler> provideAdminHandlerProvider;",
                    "  private Provider<Handler> provideLoginHandlerProvider;",
                    "  private Provider<Map<WrappedClassKey, Provider<Handler>>>",
                    "      mapOfWrappedClassKeyAndProviderOfHandlerProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(",
                    "      final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        MapModuleOne_ProvideAdminHandlerFactory.create(mapModuleOneParam);",
                    "    this.provideLoginHandlerProvider =",
                    "        MapModuleTwo_ProvideLoginHandlerFactory.create(mapModuleTwoParam);",
                    "    this.mapOfWrappedClassKeyAndProviderOfHandlerProvider =",
                    "        MapProviderFactory.<WrappedClassKey, Handler>builder(2)",
                    "            .put(WrappedClassKeyCreator.createWrappedClassKey(Integer.class),",
                    "                provideAdminHandlerProvider)",
                    "            .put(WrappedClassKeyCreator.createWrappedClassKey(Long.class),",
                    "                provideLoginHandlerProvider)",
                    "            .build();",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Map<WrappedClassKey, Provider<Handler>>",
                    "      mapOfWrappedClassKeyAndProviderOfHandler() {",
                    "    return ImmutableMap.<WrappedClassKey, Provider<Handler>>of(",
                    "        WrappedClassKeyCreator.createWrappedClassKey(Integer.class),",
                    "        provideAdminHandlerProvider,",
                    "        WrappedClassKeyCreator.createWrappedClassKey(Long.class),",
                    "        provideLoginHandlerProvider);",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 1);",
                    "    this.provideLoginHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 2);",
                    "    this.mapOfWrappedClassKeyAndProviderOfHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 0);",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Provider<Map<WrappedClassKey, Provider<Handler>>> dispatcher() {",
                    "    return mapOfWrappedClassKeyAndProviderOfHandlerProvider;",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) testComponent",
                    "            .mapOfWrappedClassKeyAndProviderOfHandler();",
                    "        case 1: return (T) MapModuleOne_ProvideAdminHandlerFactory",
                    "            .provideAdminHandler(testComponent.mapModuleOne);",
                    "        case 2: return (T) MapModuleTwo_ProvideLoginHandlerFactory",
                    "            .provideLoginHandler(testComponent.mapModuleTwo);",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void mapBindingsWithNonProviderValue() {
    JavaFileObject mapModuleOneFile = JavaFileObjects.forSourceLines("test.MapModuleOne",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleOne {",
        "  @Provides @IntoMap @PathKey(PathEnum.ADMIN) Handler provideAdminHandler() {",
        "    return new AdminHandler();",
        "  }",
        "}");
    JavaFileObject mapModuleTwoFile = JavaFileObjects.forSourceLines("test.MapModuleTwo",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleTwo {",
        "  @Provides @IntoMap @PathKey(PathEnum.LOGIN) Handler provideLoginHandler() {",
        "    return new LoginHandler();",
        "  }",
        "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = true)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject handlerFile =
        JavaFileObjects.forSourceLines("test.Handler", "package test;", "", "interface Handler {}");
    JavaFileObject loginHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    JavaFileObject adminHandlerFile =
        JavaFileObjects.forSourceLines(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Provider<Map<PathEnum, Handler>> dispatcher();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                mapModuleOneFile,
                mapModuleTwoFile,
                enumKeyFile,
                pathEnumFile,
                handlerFile,
                loginHandlerFile,
                adminHandlerFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  private Provider<Handler> provideAdminHandlerProvider;",
                    "  private Provider<Handler> provideLoginHandlerProvider;",
                    "  private Provider<Map<PathEnum, Handler>> mapOfPathEnumAndHandlerProvider;",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(",
                    "        final MapModuleOne mapModuleOneParam,",
                    "        final MapModuleTwo mapModuleTwoParam) {",
                    "    this.provideAdminHandlerProvider =",
                    "        MapModuleOne_ProvideAdminHandlerFactory.create(mapModuleOneParam);",
                    "    this.provideLoginHandlerProvider =",
                    "        MapModuleTwo_ProvideLoginHandlerFactory.create(mapModuleTwoParam);",
                    "    this.mapOfPathEnumAndHandlerProvider =",
                    "        MapFactory.<PathEnum, Handler>builder(2)",
                    "            .put(PathEnum.ADMIN, provideAdminHandlerProvider)",
                    "            .put(PathEnum.LOGIN, provideLoginHandlerProvider)",
                    "            .build();",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Provider<Map<PathEnum, Handler>> mapOfPathEnumAndHandlerProvider;",
                    "",
                    "  private Map<PathEnum, Handler> mapOfPathEnumAndHandler() {",
                    "    return ImmutableMap.<PathEnum, Handler>of(",
                    "        PathEnum.ADMIN,",
                    "        MapModuleOne_ProvideAdminHandlerFactory",
                    "            .provideAdminHandler(mapModuleOne),",
                    "        PathEnum.LOGIN,",
                    "        MapModuleTwo_ProvideLoginHandlerFactory",
                    "            .provideLoginHandler(mapModuleTwo));",
                    "  }",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final MapModuleOne mapModuleOneParam,",
                    "      final MapModuleTwo mapModuleTwoParam) {",
                    "    this.mapOfPathEnumAndHandlerProvider =",
                    "        new SwitchingProvider<>(testComponent, 0);",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Provider<Map<PathEnum, Handler>> dispatcher() {",
                    "    return mapOfPathEnumAndHandlerProvider;",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) testComponent.mapOfPathEnumAndHandler();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void injectMapWithoutMapBinding() {
    JavaFileObject mapModuleFile = JavaFileObjects.forSourceLines("test.MapModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.HashMap;",
        "import java.util.Map;",
        "",
        "@Module",
        "final class MapModule {",
        "  @Provides Map<String, String> provideAMap() {",
        "    Map<String, String> map = new HashMap<String, String>();",
        "    map.put(\"Hello\", \"World\");",
        "    return map;",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "",
        "@Component(modules = {MapModule.class})",
        "interface TestComponent {",
        "  Map<String, String> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GeneratedLines.generatedAnnotations(),
            "final class DaggerTestComponent implements TestComponent {",
            "  private final MapModule mapModule;",
            "",
            "  @Override",
            "  public Map<String, String> dispatcher() {",
            "    return MapModule_ProvideAMapFactory.provideAMap(mapModule);",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(mapModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }
}
