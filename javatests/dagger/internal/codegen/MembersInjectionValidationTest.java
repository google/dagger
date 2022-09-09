/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static dagger.internal.codegen.Compilers.daggerCompiler;

import androidx.room.compiler.processing.util.Source;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.compile.CompilerTests;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that errors are reported for invalid members injection methods and {@link
 * dagger.MembersInjector} dependency requests.
 */
@RunWith(JUnit4.class)
public class MembersInjectionValidationTest {
  @Test
  public void membersInjectDependsOnUnboundedType() {
    Source injectsUnboundedType =
        CompilerTests.javaSource(
            "test.InjectsUnboundedType",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import java.util.ArrayList;",
            "import javax.inject.Inject;",
            "",
            "class InjectsUnboundedType {",
            "  @Inject MembersInjector<ArrayList<?>> listInjector;",
            "}");

    CompilerTests.daggerCompiler(injectsUnboundedType)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Cannot inject members into types with unbounded type arguments: "
                          + "java.util.ArrayList<?>")
                  .onSource(injectsUnboundedType)
                  .onLineContaining("@Inject MembersInjector<ArrayList<?>> listInjector;");
            });
  }

  // TODO(b/246320661): Use XProcessing testing once this bug is fixed.
  @Test
  public void membersInjectPrimitive() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(int primitive);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into int")
        .inFile(component)
        .onLineContaining("void inject(int primitive);");
  }

  @Test
  public void membersInjectArray() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Object[] array);",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into java.lang.Object[]")
                  .onSource(component)
                  .onLineContaining("void inject(Object[] array);");
            });
  }

  @Test
  public void membersInjectorOfArray() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "",
            "@Component",
            "interface TestComponent {",
            "  MembersInjector<Object[]> objectArrayInjector();",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into java.lang.Object[]")
                  .onSource(component)
                  .onLineContaining("objectArrayInjector();");
            });
  }

  @Test
  public void membersInjectRawType() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Set rawSet);",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into raw type java.util.Set");
            });
  }

  @Test
  public void qualifiedMembersInjector() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Named(\"foo\") MembersInjector<Object> objectInjector();",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into qualified types")
                  .onSource(component)
                  .onLineContaining("objectInjector();");
            });
  }

  @Test
  public void qualifiedMembersInjectionMethod() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Named(\"foo\") void injectObject(Object object);",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into qualified types")
                  .onSource(component)
                  .onLineContaining("injectObject(Object object);");
            });
  }

  @Test
  public void qualifiedMembersInjectionMethodParameter() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void injectObject(@Named(\"foo\") Object object);",
            "}");
    CompilerTests.daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Cannot inject members into qualified types")
                  .onSource(component)
                  .onLineContaining("injectObject(@Named(\"foo\") Object object);");
            });
  }

  @Test
  public void staticFieldInjection() {
    Source injected =
        CompilerTests.javaSource(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Injected {",
            "  @Inject static Object object;",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Injected injected);",
            "}");
    CompilerTests.daggerCompiler(injected, component)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("static fields")
                  .onSource(injected)
                  .onLine(6);
              subject.hasErrorContaining(
                  "Injected cannot be provided without an @Inject constructor or an "
                      + "@Provides-annotated method.");
            });
  }

  // TODO(b/245934092): Update this test after this bug is fixed on XProcessing side.
  @Test
  public void missingMembersInjectorForKotlinProperty() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinInjectedQualifier;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinInjectedQualifier injected);",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides",
            "  @Named(\"TheString\")",
            "  String theString() { return \"\"; }",
            "}");
    Compilation compilation = daggerCompiler().compile(component, module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Unable to read annotations on an injected Kotlin property.");
  }

  // TODO(b/245934092): Update this test after this bug is fixed on XProcessing side.
  @Test
  public void memberInjectionForKotlinObjectFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinObjectWithMemberInjection;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinObjectWithMemberInjection injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(component, testModule.toJFO());
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into Kotlin objects");
  }

  @Test
  public void setterMemberInjectionForKotlinObjectFails() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinObjectWithSetterMemberInjection;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinObjectWithSetterMemberInjection injected);",
            "}");
    CompilerTests.daggerCompiler(component, testModule)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("Dagger does not support injection into Kotlin objects");
              subject.hasErrorContaining(
                  "KotlinObjectWithSetterMemberInjection cannot be provided without an "
                      + "@Provides-annotated method.");
            });
  }

  // TODO(b/245934092): Update this test after this bug is fixed on XProcessing side.
  @Test
  public void memberInjectionForKotlinClassWithCompanionObjectFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinClassWithMemberInjectedCompanion;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinClassWithMemberInjectedCompanion injected);",
            "  void injectCompanion(KotlinClassWithMemberInjectedCompanion.Companion injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(component, testModule.toJFO());
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into static fields");
  }

  // TODO(b/245792321): Update this test once this bug is fixed.
  @Test
  public void setterMemberInjectionForKotlinClassWithCompanionObjectFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinClassWithSetterMemberInjectedCompanion;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinClassWithSetterMemberInjectedCompanion.Companion injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(component, testModule.toJFO());
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into Kotlin objects");
  }

  // TODO(b/245934092): Update this test after this bug is fixed on XProcessing side.
  @Test
  public void memberInjectionForKotlinClassWithNamedCompanionObjectFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinClassWithMemberInjectedNamedCompanion;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(KotlinClassWithMemberInjectedNamedCompanion injected);",
            "  void injectCompanion(KotlinClassWithMemberInjectedNamedCompanion.TheCompanion"
                + " injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(component, testModule.toJFO());
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into static fields");
  }

  // TODO(b/245792321): Update this test once this bug is fixed.
  @Test
  public void setterMemberInjectionForKotlinClassWithNamedCompanionObjectFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.KotlinClassWithSetterMemberInjectedNamedCompanion;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(",
            "      KotlinClassWithSetterMemberInjectedNamedCompanion.TheCompanion injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(component, testModule.toJFO());
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into Kotlin objects");
  }

  private final Source testModule =
        CompilerTests.javaSource(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Module;",
          "import dagger.Provides;",
          "",
          "@Module",
          "class TestModule {",
          "  @Provides",
          "  String theString() { return \"\"; }",
          "}");
}
