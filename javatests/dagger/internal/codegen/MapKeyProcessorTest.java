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
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.EXPERIMENTAL_ANDROID_MODE;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapKeyProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MapKeyProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void mapKeyCreatorFile() {
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject generatedKeyCreator =
        JavaFileObjects.forSourceLines(
            "test.PathKeyCreator",
            "package test;",
            "",
            "import com.google.auto.value.AutoAnnotation;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class PathKeyCreator {",
            "  private PathKeyCreator() {}",
            "",
            "  @AutoAnnotation",
            "  public static PathKey createPathKey(PathEnum value, String relativePath) {",
            "    return new AutoAnnotation_PathKeyCreator_createPathKey(value, relativePath);",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(enumKeyFile, pathEnumFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedKeyCreator);
  }

  @Test
  public void nestedMapKeyCreatorFile() {
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.Container",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "public interface Container {",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject generatedKeyCreator =
        JavaFileObjects.forSourceLines(
            "test.Container_PathKeyCreator",
            "package test;",
            "",
            "import com.google.auto.value.AutoAnnotation;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class Container_PathKeyCreator {",
            "  private Container_PathKeyCreator() {}",
            "",
            "  @AutoAnnotation",
            "  public static Container.PathKey createPathKey("
                + "PathEnum value, String relativePath) {",
            "    return new AutoAnnotation_Container_PathKeyCreator_createPathKey(",
            "        value, relativePath);",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(enumKeyFile, pathEnumFile))
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedKeyCreator);
  }

  @Test
  public void mapKeyComponentFileWithDisorderedKeyField() {
    JavaFileObject mapModuleOneFile = JavaFileObjects.forSourceLines("test.MapModuleOne",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleOne {",
        "  @Provides",
        "  @IntoMap",
        "  @PathKey(relativePath = \"AdminPath\", value = PathEnum.ADMIN)",
        "  Handler provideAdminHandler() {",
        "    return new AdminHandler();",
        "  }",
        "}");
    JavaFileObject mapModuleTwoFile =JavaFileObjects.forSourceLines("test.MapModuleTwo",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleTwo {",
        "  @Provides",
        "  @IntoMap",
        "  @PathKey(value = PathEnum.LOGIN, relativePath = \"LoginPath\")",
        "  Handler provideLoginHandler() {",
        "    return new LoginHandler();",
        "  }",
        "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"DefaultPath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject handlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject loginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject adminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<PathKey, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        new JavaFileBuilder(compilerMode, "test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {")
            .addLinesIn(
                EXPERIMENTAL_ANDROID_MODE,
                "  @Override",
                "  public Map<PathKey, Provider<Handler>> dispatcher() {",
                "    return ImmutableMap.<PathKey, Provider<Handler>>of(",
                "        PathKeyCreator.createPathKey(PathEnum.ADMIN, \"AdminPath\"),",
                "        getMapOfPathKeyAndProviderOfHandlerProvider(),",
                "        PathKeyCreator.createPathKey(PathEnum.LOGIN, \"LoginPath\"),",
                "        getMapOfPathKeyAndProviderOfHandlerProvider2());",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @Override",
                "  public Map<PathKey, Provider<Handler>> dispatcher() {",
                "    return ImmutableMap.<PathKey, Provider<Handler>>of(",
                "        PathKeyCreator.createPathKey(PathEnum.ADMIN, \"AdminPath\"),",
                "        provideAdminHandlerProvider,",
                "        PathKeyCreator.createPathKey(PathEnum.LOGIN, \"LoginPath\"),",
                "        provideLoginHandlerProvider);",
                "  }")
            .addLines("}")
            .build();
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
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
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void mapKeyComponentFileWithDefaultField() {
    JavaFileObject mapModuleOneFile = JavaFileObjects.forSourceLines("test.MapModuleOne",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleOne {",
        "  @Provides",
        "  @IntoMap",
        "  @PathKey(value = PathEnum.ADMIN) Handler provideAdminHandler() {",
        "    return new AdminHandler();",
        "  }",
        "}");
    JavaFileObject mapModuleTwoFile =JavaFileObjects.forSourceLines("test.MapModuleTwo",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoMap;",
        "",
        "@Module",
        "final class MapModuleTwo {",
        "  @Provides",
        "  @IntoMap",
        "  @PathKey(value = PathEnum.LOGIN, relativePath = \"LoginPath\")",
        "  Handler provideLoginHandler() {",
        "    return new LoginHandler();",
        "  }",
        "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"DefaultPath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject handlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject loginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject adminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<PathKey, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        new JavaFileBuilder(compilerMode, "test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {")
            .addLinesIn(
                EXPERIMENTAL_ANDROID_MODE,
                "  @Override",
                "  public Map<PathKey, Provider<Handler>> dispatcher() {",
                "    return ImmutableMap.<PathKey, Provider<Handler>>of(",
                "        PathKeyCreator.createPathKey(PathEnum.ADMIN, \"DefaultPath\"),",
                "        getMapOfPathKeyAndProviderOfHandlerProvider(),",
                "        PathKeyCreator.createPathKey(PathEnum.LOGIN, \"LoginPath\"),",
                "        getMapOfPathKeyAndProviderOfHandlerProvider2());",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @Override",
                "  public Map<PathKey, Provider<Handler>> dispatcher() {",
                "    return ImmutableMap.<PathKey, Provider<Handler>>of(",
                "        PathKeyCreator.createPathKey(PathEnum.ADMIN, \"DefaultPath\"),",
                "        provideAdminHandlerProvider,",
                "        PathKeyCreator.createPathKey(PathEnum.LOGIN, \"LoginPath\"),",
                "        provideLoginHandlerProvider);",
                "  }")
            .addLines("}")
            .build();
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
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
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void mapKeyWithDefaultValue() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "final class MapModule {",
            "  @Provides",
            "  @IntoMap",
            "  @BoolKey int provideFalseValue() {",
            "    return -1;",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @BoolKey(true) int provideTrueValue() {",
            "    return 1;",
            "  }",
            "}");
    JavaFileObject mapKey =
        JavaFileObjects.forSourceLines(
            "test.BoolKey",
            "package test;",
            "",
            "import dagger.MapKey;",
            "",
            "@MapKey",
            "@interface BoolKey {",
            "  boolean value() default false;",
            "}");
    assertThat(module, mapKey)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError();
  }
}
