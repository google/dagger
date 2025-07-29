/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KspComponentProcessorTest {
  @Test
  public void emptyComponentTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface MyComponent {}");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      return new MyComponentImpl();",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    MyComponentImpl() {",
                      "",
                      "",
                      "    }",
                      "  }",
                      "}"));
            });
  }

  @Test
  public void
      testComponentReferencingGeneratedTypeInCompanionObject_successfullyGeneratedComponent()
          throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.BindsInstance",
            "import dagger.Component",
            "",
            "@Component",
            "interface MyComponent {",
            " @Component.Builder",
            " interface Builder {",
            "   @BindsInstance fun text(text: String): Builder",
            "   fun build(): MyComponent",
            " }",
            "",
            " companion object {",
            "   fun getComponent(text: String) = DaggerMyComponent.builder().text(text).build()",
            " }",
            "}");

    CompilerTests.daggerCompiler(componentSrc).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void injectBindingComponentTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import javax.inject.Inject",
            "",
            "@Component",
            "interface MyComponent {",
            "  fun foo(): Foo",
            "}",
            "",
            "class Foo @Inject constructor(bar: Bar)",
            "",
            "class Bar @Inject constructor()");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      return new MyComponentImpl();",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    MyComponentImpl() {",
                      "",
                      "",
                      "    }",
                      "",
                      "    @Override",
                      "    public Foo foo() {",
                      "      return new Foo(new Bar());",
                      "    }",
                      "  }",
                      "}"));
            });
  }

  @Test
  public void injectBindingWithProvidersComponentTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import javax.inject.Inject",
            "import javax.inject.Provider",
            "",
            "@Component",
            "interface MyComponent {",
            "  fun foo(): Provider<Foo>",
            "}",
            "",
            "class Foo @Inject constructor(bar: Bar, barProvider: Provider<Bar>)",
            "",
            "class Bar @Inject constructor()");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import dagger.internal.Provider;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      return new MyComponentImpl();",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    Provider<Foo> fooProvider;",
                      "",
                      "    MyComponentImpl() {",
                      "",
                      "      initialize();",
                      "",
                      "    }",
                      "",
                      "    @SuppressWarnings(\"unchecked\")",
                      "    private void initialize() {",
                      "      this.fooProvider = "
                          + "Foo_Factory.create(Bar_Factory.create(), Bar_Factory.create());",
                      "    }",
                      "",
                      "    @Override",
                      "    public javax.inject.Provider<Foo> foo() {",
                      "      return fooProvider;",
                      "    }",
                      "  }",
                      "}"));
            });
  }

  @Test
  public void moduleProvidesBindingTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import dagger.Module",
            "import dagger.Provides",
            "import javax.inject.Inject",
            "import javax.inject.Named",
            "import javax.inject.Provider",
            "",
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  @Named(\"key\") fun foo(): Foo",
            "}",
            "",
            "@Module",
            "class MyModule {",
            "  @Provides @Named(\"key\") fun provideFoo(@Named(\"key\") bar: Bar) = Foo(bar)",
            "  @Provides @Named(\"key\") fun provideBar() = Bar()",
            "}",
            "",
            "class Foo constructor(bar: Bar)",
            "",
            "class Bar");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import dagger.internal.Preconditions;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private MyModule myModule;",
                      "",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public Builder myModule(MyModule myModule) {",
                      "      this.myModule = Preconditions.checkNotNull(myModule);",
                      "      return this;",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      if (myModule == null) {",
                      "        this.myModule = new MyModule();",
                      "      }",
                      "      return new MyComponentImpl(myModule);",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyModule myModule;",
                      "",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    MyComponentImpl(MyModule myModuleParam) {",
                      "      this.myModule = myModuleParam;",
                      "",
                      "    }",
                      "",
                      "    @Override",
                      "    public Foo foo() {",
                      "      return MyModule_ProvideFooFactory.provideFoo("
                          + "myModule, MyModule_ProvideBarFactory.provideBar(myModule));",
                      "    }",
                      "  }",
                      "}"));
            });
  }

  @Test
  public void membersInjectionMethodTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import dagger.Module",
            "import dagger.Provides",
            "import javax.inject.Inject",
            "import javax.inject.Named",
            "import javax.inject.Provider",
            "",
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun injectFoo(foo: Foo)",
            "}",
            "",
            "@Module",
            "class MyModule {",
            "  @Provides @Named(\"key\") fun provideBar() = Bar()",
            "}",
            "",
            "class Foo {",
            "  @Inject @Named(\"key\") lateinit var bar: Bar",
            "}",
            "",
            "class Bar");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                      "import dagger.internal.DaggerGenerated;",
                      "import dagger.internal.Preconditions;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private MyModule myModule;",
                      "",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public Builder myModule(MyModule myModule) {",
                      "      this.myModule = Preconditions.checkNotNull(myModule);",
                      "      return this;",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      if (myModule == null) {",
                      "        this.myModule = new MyModule();",
                      "      }",
                      "      return new MyComponentImpl(myModule);",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyModule myModule;",
                      "",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    MyComponentImpl(MyModule myModuleParam) {",
                      "      this.myModule = myModuleParam;",
                      "",
                      "    }",
                      "",
                      "    @Override",
                      "    public void injectFoo(Foo foo) {",
                      "      injectFoo2(foo);",
                      "    }",
                      "",
                      "    @CanIgnoreReturnValue",
                      "    private Foo injectFoo2(Foo instance) {",
                      "      Foo_MembersInjector.injectBar("
                          + "instance, MyModule_ProvideBarFactory.provideBar(myModule));",
                      "      return instance;",
                      "    }",
                      "  }",
                      "}"));
            });
  }

  @Test
  public void membersInjectionTest() throws Exception {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import dagger.Module",
            "import dagger.Provides",
            "import javax.inject.Inject",
            "import javax.inject.Named",
            "import javax.inject.Provider",
            "",
            "@Component(modules = [MyModule::class])",
            "interface MyComponent {",
            "  fun foo(): Foo",
            "}",
            "",
            "@Module",
            "class MyModule {",
            "  @Provides @Named(\"key\") fun provideBar() = Bar()",
            "}",
            "",
            "class Foo @Inject constructor() {",
            "  @Inject @Named(\"key\") lateinit var bar: Bar",
            "}",
            "",
            "class Bar");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  CompilerTests.javaSource(
                      "test/DaggerMyComponent",
                      "package test;",
                      "",
                      "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                      "import dagger.internal.DaggerGenerated;",
                      "import dagger.internal.Preconditions;",
                      "import javax.annotation.processing.Generated;",
                      "",
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
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private MyModule myModule;",
                      "",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public Builder myModule(MyModule myModule) {",
                      "      this.myModule = Preconditions.checkNotNull(myModule);",
                      "      return this;",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      if (myModule == null) {",
                      "        this.myModule = new MyModule();",
                      "      }",
                      "      return new MyComponentImpl(myModule);",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyModule myModule;",
                      "",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    MyComponentImpl(MyModule myModuleParam) {",
                      "      this.myModule = myModuleParam;",
                      "",
                      "    }",
                      "",
                      "    @Override",
                      "    public Foo foo() {",
                      "      return injectFoo(Foo_Factory.newInstance());",
                      "    }",
                      "",
                      "    @CanIgnoreReturnValue",
                      "    private Foo injectFoo(Foo instance) {",
                      "      Foo_MembersInjector.injectBar("
                          + "instance, MyModule_ProvideBarFactory.provideBar(myModule));",
                      "      return instance;",
                      "    }",
                      "  }",
                      "}"));
            });
  }
}
