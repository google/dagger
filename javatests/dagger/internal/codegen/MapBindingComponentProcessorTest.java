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
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import androidx.room.compiler.processing.util.Source;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Rule;
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

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public MapBindingComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapBindingsWithEnumKey() throws Exception {
    Source mapModuleOneFile =
        CompilerTests.javaSource(
            "test.MapModuleOne",
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
    Source mapModuleTwoFile =
        CompilerTests.javaSource(
            "test.MapModuleTwo",
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
    Source enumKeyFile =
        CompilerTests.javaSource(
            "test.PathKey",
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
    Source pathEnumFile =
        CompilerTests.javaSource(
            "test.PathEnum",
            "package test;",
            "",
            "public enum PathEnum {",
            "    ADMIN,",
            "    LOGIN;",
            "}");

    Source handlerFile =
        CompilerTests.javaSource(
            "test.Handler",
            "package test;",
            "",
            "interface Handler {}");
    Source loginHandlerFile =
        CompilerTests.javaSource(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    Source adminHandlerFile =
        CompilerTests.javaSource(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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

    CompilerTests.daggerCompiler(
            mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            handlerFile,
            loginHandlerFile,
            adminHandlerFile,
            componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void mapBindingsWithInaccessibleKeys() throws Exception {
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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
    assertThat(compilation)
        .generatedSourceFile(
            "mapkeys.MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey")
        .hasSourceEquivalentTo(
            goldenFileRule.goldenFile(
                "mapkeys.MapModule_ComplexKeyWithInaccessibleAnnotationValueMapKey"));
    assertThat(compilation)
        .generatedSourceFile("mapkeys.MapModule_ClassKeyMapKey")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("mapkeys.MapModule_ClassKeyMapKey"));
  }

  @Test
  public void mapBindingsWithStringKey() throws Exception {
    Source mapModuleOneFile =
        CompilerTests.javaSource(
            "test.MapModuleOne",
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
    Source mapModuleTwoFile =
        CompilerTests.javaSource(
            "test.MapModuleTwo",
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
    Source handlerFile =
        CompilerTests.javaSource(
            "test.Handler",
            "package test;",
            "",
            "interface Handler {}");
    Source loginHandlerFile =
        CompilerTests.javaSource(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    Source adminHandlerFile =
        CompilerTests.javaSource(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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

    CompilerTests.daggerCompiler(
            mapModuleOneFile,
            mapModuleTwoFile,
            handlerFile,
            loginHandlerFile,
            adminHandlerFile,
            componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void mapBindingsWithWrappedKey() throws Exception {
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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void mapBindingsWithNonProviderValue() throws Exception {
    Source mapModuleOneFile =
        CompilerTests.javaSource(
            "test.MapModuleOne",
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
    Source mapModuleTwoFile =
        CompilerTests.javaSource(
            "test.MapModuleTwo",
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
    Source enumKeyFile =
        CompilerTests.javaSource(
            "test.PathKey",
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
    Source pathEnumFile =
        CompilerTests.javaSource(
            "test.PathEnum",
            "package test;",
            "",
            "public enum PathEnum {",
            "    ADMIN,",
            "    LOGIN;",
            "}");
    Source handlerFile =
        CompilerTests.javaSource(
            "test.Handler",
            "package test;",
            "",
            "interface Handler {}");
    Source loginHandlerFile =
        CompilerTests.javaSource(
            "test.LoginHandler",
            "package test;",
            "",
            "class LoginHandler implements Handler {",
            "  public LoginHandler() {}",
            "}");
    Source adminHandlerFile =
        CompilerTests.javaSource(
            "test.AdminHandler",
            "package test;",
            "",
            "class AdminHandler implements Handler {",
            "  public AdminHandler() {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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

    CompilerTests.daggerCompiler(
            mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            handlerFile,
            loginHandlerFile,
            adminHandlerFile,
            componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void injectMapWithoutMapBinding() throws Exception {
    Source mapModuleFile =
        CompilerTests.javaSource(
            "test.MapModule",
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
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "",
            "@Component(modules = {MapModule.class})",
            "interface TestComponent {",
            "  Map<String, String> dispatcher();",
            "}");

    CompilerTests.daggerCompiler(mapModuleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
