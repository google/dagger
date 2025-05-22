/*
 * Copyright (C) 2020 The Dagger Authors.
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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests to make sure that delegate bindings where the impl depends on a binding in a subcomponent
 * properly fail. These are regression tests for b/147020838.
 */
@RunWith(Parameterized.class)
public class BindsDependsOnSubcomponentValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public BindsDependsOnSubcomponentValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testBinds() {
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface ParentComponent {",
            "  ChildComponent getChild();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Binds Foo bindFoo(FooImpl impl);",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Foo getFoo();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Long providLong() {",
            "    return 0L;",
            "  }",
            "}");
    Source iface =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source impl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl(Long l) {}",
            "}");
    CompilerTests.daggerCompiler(
            parentComponent, parentModule, childComponent, childModule, iface, impl)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Long cannot be provided without an @Inject constructor")
                  .onSource(parentComponent)
                  .onLineContaining("interface ParentComponent");
            });
  }

  @Test
  public void testSetBindings() {
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface ParentComponent {",
            "  ChildComponent getChild();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Binds @IntoSet Foo bindFoo(FooImpl impl);",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Set<Foo> getFooSet();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Long providLong() {",
            "    return 0L;",
            "  }",
            "}");
    Source iface =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source impl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl(Long l) {}",
            "}");
    CompilerTests.daggerCompiler(
            parentComponent, parentModule, childComponent, childModule, iface, impl)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Long cannot be provided without an @Inject constructor")
                  .onSource(parentComponent)
                  .onLineContaining("interface ParentComponent");
            });
  }

  @Test
  public void testSetValueBindings() {
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface ParentComponent {",
            "  ChildComponent getChild();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Provides @ElementsIntoSet",
            "  static Set<Foo> provideFoo(FooImpl impl) {",
            "    return Collections.singleton(impl);",
            "  }",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Set<Foo> getFooSet();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Long providLong() {",
            "    return 0L;",
            "  }",
            "}");
    Source iface =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source impl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl(Long l) {}",
            "}");
    CompilerTests.daggerCompiler(
            parentComponent, parentModule, childComponent, childModule, iface, impl)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Long cannot be provided without an @Inject constructor")
                  .onSource(parentComponent)
                  .onLineContaining("interface ParentComponent");
            });
  }

  @Test
  public void testMapBindings() {
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface ParentComponent {",
            "  ChildComponent getChild();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Binds @IntoMap @StringKey(\"foo\") Foo bindFoo(FooImpl impl);",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Map<String, Foo> getFooSet();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides static Long providLong() {",
            "    return 0L;",
            "  }",
            "}");
    Source iface =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source impl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl(Long l) {}",
            "}");
    CompilerTests.daggerCompiler(
            parentComponent, parentModule, childComponent, childModule, iface, impl)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                // TODO(erichang): make this flag the default and remove this
                .put("dagger.strictMultibindingValidation", "enabled")
                .build())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Long cannot be provided without an @Inject constructor")
                  .onSource(parentComponent)
                  .onLineContaining("interface ParentComponent");
            });
  }
}
