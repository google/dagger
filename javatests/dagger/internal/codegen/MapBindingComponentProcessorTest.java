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
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapBindingComponentProcessorTest {

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

    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<PathEnum, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Handler> provideAdminHandlerProvider;",
            "  private Provider<Handler> provideLoginHandlerProvider;",
            "  private Provider<Map<PathEnum, Provider<Handler>>>",
            "      mapOfPathEnumAndProviderOfHandlerProvider;",
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
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.provideAdminHandlerProvider =",
            "        MapModuleOne_ProvideAdminHandlerFactory.create(builder.mapModuleOne);",
            "    this.provideLoginHandlerProvider =",
            "        MapModuleTwo_ProvideLoginHandlerFactory.create(builder.mapModuleTwo);",
            "    this.mapOfPathEnumAndProviderOfHandlerProvider =",
            "        MapProviderFactory.<PathEnum, Handler>builder(2)",
            "            .put(PathEnum.ADMIN, provideAdminHandlerProvider)",
            "            .put(PathEnum.LOGIN, provideLoginHandlerProvider)",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Map<PathEnum, Provider<Handler>> dispatcher() {",
            "    return mapOfPathEnumAndProviderOfHandlerProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private MapModuleOne mapModuleOne;",
            "    private MapModuleTwo mapModuleTwo;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (mapModuleOne == null) {",
            "        this.mapModuleOne = new MapModuleOne();",
            "      }",
            "      if (mapModuleTwo == null) {",
            "        this.mapModuleTwo = new MapModuleTwo();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
            "      this.mapModuleOne = Preconditions.checkNotNull(mapModuleOne);",
            "      return this;",
            "    }",
            "",
            "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
            "      this.mapModuleTwo = Preconditions.checkNotNull(mapModuleTwo);",
            "      return this;",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
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
    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<String, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Handler> provideAdminHandlerProvider;",
            "  private Provider<Handler> provideLoginHandlerProvider;",
            "  private Provider<Map<String, Provider<Handler>>>",
            "      mapOfStringAndProviderOfHandlerProvider;",
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
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.provideAdminHandlerProvider =",
            "        MapModuleOne_ProvideAdminHandlerFactory.create(builder.mapModuleOne);",
            "    this.provideLoginHandlerProvider =",
            "        MapModuleTwo_ProvideLoginHandlerFactory.create(builder.mapModuleTwo);",
            "    this.mapOfStringAndProviderOfHandlerProvider =",
            "        MapProviderFactory.<String, Handler>builder(2)",
            "            .put(\"Admin\", provideAdminHandlerProvider)",
            "            .put(\"Login\", provideLoginHandlerProvider)",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Map<String, Provider<Handler>> dispatcher() {",
            "    return mapOfStringAndProviderOfHandlerProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private MapModuleOne mapModuleOne;",
            "    private MapModuleTwo mapModuleTwo;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (mapModuleOne == null) {",
            "        this.mapModuleOne = new MapModuleOne();",
            "      }",
            "      if (mapModuleTwo == null) {",
            "        this.mapModuleTwo = new MapModuleTwo();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
            "      this.mapModuleOne = Preconditions.checkNotNull(mapModuleOne);",
            "      return this;",
            "    }",
            "",
            "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
            "      this.mapModuleTwo = Preconditions.checkNotNull(mapModuleTwo);",
            "      return this;",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
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
    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<WrappedClassKey, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Handler> provideAdminHandlerProvider;",
            "  private Provider<Handler> provideLoginHandlerProvider;",
            "  private Provider<Map<WrappedClassKey, Provider<Handler>>>",
            "      mapOfWrappedClassKeyAndProviderOfHandlerProvider;",
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
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.provideAdminHandlerProvider =",
            "        MapModuleOne_ProvideAdminHandlerFactory.create(builder.mapModuleOne);",
            "    this.provideLoginHandlerProvider =",
            "        MapModuleTwo_ProvideLoginHandlerFactory.create(builder.mapModuleTwo);",
            "    this.mapOfWrappedClassKeyAndProviderOfHandlerProvider =",
            "        MapProviderFactory.<WrappedClassKey, Handler>builder(2)",
            "            .put(WrappedClassKeyCreator.createWrappedClassKey(Integer.class),",
            "                provideAdminHandlerProvider)",
            "            .put(WrappedClassKeyCreator.createWrappedClassKey(Long.class),",
            "                provideLoginHandlerProvider)",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Map<WrappedClassKey, Provider<Handler>> dispatcher() {",
            "    return mapOfWrappedClassKeyAndProviderOfHandlerProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private MapModuleOne mapModuleOne;",
            "    private MapModuleTwo mapModuleTwo;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (mapModuleOne == null) {",
            "        this.mapModuleOne = new MapModuleOne();",
            "      }",
            "      if (mapModuleTwo == null) {",
            "        this.mapModuleTwo = new MapModuleTwo();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
            "      this.mapModuleOne = Preconditions.checkNotNull(mapModuleOne);",
            "      return this;",
            "    }",
            "",
            "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
            "      this.mapModuleTwo = Preconditions.checkNotNull(mapModuleTwo);",
            "      return this;",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            wrappedClassKeyFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
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
    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<PathEnum, Handler> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import java.util.Map;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Handler> provideAdminHandlerProvider;",
            "  private Provider<Handler> provideLoginHandlerProvider;",
            "  private Provider<Map<PathEnum, Provider<Handler>>>",
            "      mapOfPathEnumAndProviderOfHandlerProvider;",
            "  private Provider<Map<PathEnum, Handler>> mapOfPathEnumAndHandlerProvider;",
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
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.provideAdminHandlerProvider =",
            "        MapModuleOne_ProvideAdminHandlerFactory.create(builder.mapModuleOne);",
            "    this.provideLoginHandlerProvider =",
            "        MapModuleTwo_ProvideLoginHandlerFactory.create(builder.mapModuleTwo);",
            "    this.mapOfPathEnumAndProviderOfHandlerProvider =",
            "        MapProviderFactory.<PathEnum, Handler>builder(2)",
            "            .put(PathEnum.ADMIN, provideAdminHandlerProvider)",
            "            .put(PathEnum.LOGIN, provideLoginHandlerProvider)",
            "            .build();",
            "    this.mapOfPathEnumAndHandlerProvider =",
            "        MapFactory.create(mapOfPathEnumAndProviderOfHandlerProvider);",
            "  }",
            "",
            "  @Override",
            "  public Map<PathEnum, Handler> dispatcher() {",
            "    return mapOfPathEnumAndHandlerProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private MapModuleOne mapModuleOne;",
            "    private MapModuleTwo mapModuleTwo;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (mapModuleOne == null) {",
            "        this.mapModuleOne = new MapModuleOne();",
            "      }",
            "      if (mapModuleTwo == null) {",
            "        this.mapModuleTwo = new MapModuleTwo();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
            "      this.mapModuleOne = Preconditions.checkNotNull(mapModuleOne);",
            "      return this;",
            "    }",
            "",
            "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
            "      this.mapModuleTwo = Preconditions.checkNotNull(mapModuleTwo);",
            "      return this;",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile)).
        processedWith(new ComponentProcessor())
            .compilesWithoutError()
            .and().generatesSources(generatedComponent);
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
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines("test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.Preconditions;",
        "import java.util.Map;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<Map<String, String>> provideAMapProvider;",
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
        "    return new Builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.provideAMapProvider = MapModule_ProvideAMapFactory.create(builder.mapModule);",
        "  }",
        "",
        "  @Override",
        "  public Map<String, String> dispatcher() {",
        "    return provideAMapProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private MapModule mapModule;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (mapModule == null) {",
        "        this.mapModule = new MapModule();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    public Builder mapModule(MapModule mapModule) {",
        "      this.mapModule = Preconditions.checkNotNull(mapModule);",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(mapModuleFile,componentFile))
        .processedWith(new ComponentProcessor()).compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test
  public void mapBindingsWithDuplicateKeys() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.StringKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "final class MapModule {",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKey() {",
            "    return \"one\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKeyAgain() {",
            "    return \"one again\";",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = {MapModule.class})",
            "interface TestComponent {",
            "  Map<String, Object> objects();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(module, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("The same map key is bound more than once")
        .and()
        .withErrorContaining("provideObjectForAKey()")
        .and()
        .withErrorContaining("provideObjectForAKeyAgain()")
        .and()
        .withErrorCount(1);
  }

  @Test
  public void mapBindingsWithInconsistentKeyAnnotations() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MapModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.StringKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "final class MapModule {",
            "  @Provides @IntoMap @StringKey(\"AKey\") Object provideObjectForAKey() {",
            "    return \"one\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKeyTwo(\"BKey\") Object provideObjectForBKey() {",
            "    return \"two\";",
            "  }",
            "}");
    JavaFileObject stringKeyTwoFile =
        JavaFileObjects.forSourceLines(
            "test.StringKeyTwo",
            "package test;",
            "",
            "import dagger.MapKey;",
            "",
            "@MapKey(unwrapValue = true)",
            "public @interface StringKeyTwo {",
            "  String value();",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "",
            "@Component(modules = {MapModule.class})",
            "interface TestComponent {",
            "  Map<String, Object> objects();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(module, stringKeyTwoFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("uses more than one @MapKey annotation type")
        .and()
        .withErrorContaining("provideObjectForAKey()")
        .and()
        .withErrorContaining("provideObjectForBKey()")
        .and()
        .withErrorCount(1);
  }
}
