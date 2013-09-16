/**
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.validation;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static java.util.Arrays.asList;
import static org.truth0.Truth.ASSERT;

/**
 * Integration tests for the validation processors related to the use
 * of Scoping Annotations.
 */
// TODO(cgruber): Audit this class when http://github.com/google/compile-testing
//                has error/warning counts and other warning predicates available.
@RunWith(JUnit4.class)
public class ScopeAnnotationUseTest {
  private static final String ABSTRACTION_SCOPING_TEXT =
      "Scoping annotations are only allowed on concrete types and @Provides methods:";

  // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
  //private static final String MISUSED_SCOPE_TEXT =
  //    "Dagger will ignore scoping annotations on methods that are not @Provides methods:";

  @Test public void compileSucceedsScopeOnConcreteType() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "@Singleton",
        "class Test {",
        "  @Inject public Test() { }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsScopeOnProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "@Module(library = true, injects = String.class)",
        "class Test {",
        "  @Provides @Singleton public String provideString() { return \"\"; }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsWithScopedSuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Singleton;",
        "class Test {",
        "  @SuppressWarnings(\"scoping\")",
        "  @Singleton void method() { }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileSucceedsWithScopedMultiplySuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Singleton;",
        "class Test {",
        "  @SuppressWarnings({\"blah\", \"scoping\", \"foo\"})",
        "  @Singleton void method() { }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing has hasNoWarnings()
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.and().hasNoWarnings();
  }

  @Test public void compileWarnsWithScopedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Singleton void method() { }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.withWarningContaining(MISUSED_SCOPE_TEXT).in(sourceFile).onLine(3).atColumn(49).and()
        //.withWarningContaining("Test.method()").in(sourceFile).onLine(3).atColumn(49);
  }

  @Test public void compileWarnsWithScopedIncorrectlySuppressedNonProvidesMethod() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Singleton;",
        "class Test {",
        "  @SuppressWarnings(\"some string other than 'scoping'\")",
        "  @Singleton void method() { }",
        "}"));

    // TODO(cgruber): uncomment when http://github.com/google/compile-testing supports warnings.
    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).compilesWithoutError();
        //.withWarningContaining(MISUSED_SCOPE_TEXT).in(sourceFile).onLine(4).atColumn(49).and()
        //.withWarningContaining("Test.method()").in(sourceFile).onLine(4).atColumn(49);
  }

  @Test public void compileFailsWithScopeOnInterface() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Module(injects = TestType.class) class TestModule { }",
        "  @Singleton interface TestType { }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(14).and()
        .withErrorContaining("Test.TestType").in(sourceFile).onLine(5).atColumn(14);
  }

  @Test public void compileFailsWithScopeOnAbstractClass() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Module(injects = TestType.class) class TestModule { }",
        "  @Singleton abstract class TestType { }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(23).and()
        .withErrorContaining("Test.TestType").in(sourceFile).onLine(5).atColumn(23);
  }

  @Test public void compileFailsWithScopeOnField() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Singleton String field;",
        "  @Inject public Test() { }",
        "  @Module(injects = Test.class) class TestModule { }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(21).and()
        .withErrorContaining("Test.field").in(sourceFile).onLine(5).atColumn(21);
  }

  @Test public void compileFailsWithScopeOnMethodParameter() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "@Module(library = true, injects = String.class)",
        "class Test {",
        "  @Provides int provideInteger() { return 0; }",
        "  @Provides String provideString(@Singleton int intParam) { return \"\"; }",
        "}"));

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(7).atColumn(49).and()
        .withErrorContaining("intParam").in(sourceFile).onLine(7).atColumn(49);
  }

  @Test public void compileFailsWithMultipleScopeAnnotations() {
    JavaFileObject annotation = JavaFileObjects.forSourceString("MyScope", Joiner.on("\n").join(
        "import java.lang.annotation.Retention;",
        "import javax.inject.Scope;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "@Scope @Retention(RUNTIME) public @interface MyScope { }"));

    JavaFileObject module = JavaFileObjects.forSourceString("MyModule", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "@Module(library = true, injects = Injectable.class)",
        "class MyModule {",
        "  @Provides @Singleton @MyScope String method() { return \"\"; }",
        "}"));

    JavaFileObject injectable = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "@Singleton @MyScope",
        "class Injectable {",
        "  @Inject String string;",
        "}"));

    String error = "Only one scoping annotation is allowed per element: ";

    ASSERT.about(javaSources()).that(asList(annotation, module, injectable))
        .processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(error + "MyModule.method()").in(module).onLine(6).atColumn(40).and()
        .withErrorContaining(error + "Injectable").in(injectable).onLine(4).atColumn(1);
  }

  @Test public void compileFailsWithScopeOnConstructor() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("Test", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "class Test {",
        "  @Singleton @Inject public Test() { }",
        "  @Module(injects = Test.class) class TestModule { }",
        "}"));

   String singletonErrorText = ""
        + "Singleton annotations have no effect on constructors. "
        + "Did you mean to annotate the class?";

    ASSERT.about(javaSource())
        .that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining(ABSTRACTION_SCOPING_TEXT).in(sourceFile).onLine(5).atColumn(29).and()
        .withErrorContaining("Test.Test()").in(sourceFile).onLine(5).atColumn(29).and()
        .withErrorContaining(singletonErrorText).in(sourceFile).onLine(6).atColumn(33);
  }
}

