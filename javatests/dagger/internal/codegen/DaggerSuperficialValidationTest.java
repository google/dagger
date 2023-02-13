/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import androidx.room.compiler.processing.util.Source;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import dagger.BindsInstance;
import dagger.Component;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.DaggerSuperficialValidation.ValidationException;
import dagger.testing.compile.CompilerTests;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DaggerSuperficialValidationTest {
  private static final Joiner NEW_LINES = Joiner.on("\n  ");

  @Test
  public void missingReturnType() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract MissingType blah();",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): blah()",
                          "  => type (ERROR return type): %1$s"),
                      isJavac ? "MissingType" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingGenericReturnType() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract MissingType<?> blah();",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): blah()",
                          "  => type (ERROR return type): %1$s"),
                      isJavac ? "<any>" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingReturnTypeTypeParameter() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "abstract class TestClass {",
            "  abstract Map<Set<?>, MissingType<?>> blah();",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): blah()",
                          "  => type (DECLARED return type): "
                              + "java.util.Map<java.util.Set<?>,%1$s>",
                          "  => type (ERROR type argument): %1$s"),
                      isJavac ? "<any>" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingTypeParameter() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass", //
            "package test;",
            "",
            "class TestClass<T extends MissingType> {}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (TYPE_PARAMETER): T",
                          "  => type (ERROR bound type): %s"),
                      isJavac ? "MissingType" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingParameterType() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract void foo(MissingType param);",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): foo(%1$s)",
                          "  => element (PARAMETER): param",
                          "  => type (ERROR parameter type): %1$s"),
                      isJavac ? "MissingType" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingAnnotation() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass", //
            "package test;",
            "",
            "@MissingAnnotation",
            "class TestClass {}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => annotation: @MissingAnnotation",
                          "  => type (ERROR annotation type): %s"),
                      isJavac ? "MissingAnnotation" : "error.NonExistentClass"));
        });
  }

  @Test
  public void handlesRecursiveTypeParams() {
    runSuccessfulTest(
        CompilerTests.javaSource(
            "test.TestClass", //
            "package test;",
            "",
            "class TestClass<T extends Comparable<T>> {}"),
        (processingEnv, superficialValidation) ->
            superficialValidation.validateElement(processingEnv.findTypeElement("test.TestClass")));
  }

  @Test
  public void handlesRecursiveType() {
    runSuccessfulTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract TestClass foo(TestClass x);",
            "}"),
        (processingEnv, superficialValidation) ->
            superficialValidation.validateElement(processingEnv.findTypeElement("test.TestClass")));
  }

  @Test
  public void missingWildcardBound() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import java.util.Set;",
            "",
            "class TestClass {",
            "  Set<? extends MissingType> extendsTest() {",
            "    return null;",
            "  }",
            "",
            "  Set<? super MissingType> superTest() {",
            "    return null;",
            "  }",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): extendsTest()",
                          "  => type (DECLARED return type): java.util.Set<? extends %1$s>",
                          "  => type (WILDCARD type argument): ? extends %1$s",
                          "  => type (ERROR extends bound type): %1$s"),
                      isJavac ? "MissingType" : "error.NonExistentClass"));
        });
  }

  @Test
  public void missingIntersection() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "class TestClass<T extends Number & Missing> {}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (TYPE_PARAMETER): T",
                          "  => type (ERROR bound type): %s"),
                      isJavac ? "Missing" : "error.NonExistentClass"));
        });
  }

  @Test
  public void invalidAnnotationValue() {
    runTest(
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  @interface TestAnnotation {",
            "    Class[] classes();",
            "  }",
            "",
            "  @TestAnnotation(classes = MissingType.class)",
            "  static class TestClass {}",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.Outer.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.Outer.TestClass",
                          "  => annotation: @test.Outer.TestAnnotation(classes={<%1$s>})",
                          "  => annotation value (TYPE_ARRAY): classes={<%1$s>}",
                          "  => annotation value (TYPE): classes=<%1$s>"),
                      isJavac ? "error" : "Error"));
        });
  }

  @Test
  public void invalidAnnotationValueOnParameter() {
    runTest(
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  @interface TestAnnotation {",
            "    Class[] classes();",
            "  }",
            "",
            "  static class TestClass {",
            "    TestClass(@TestAnnotation(classes = MissingType.class) String strParam) {}",
            "  }",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.Outer.TestClass");
          XConstructorElement constructor = testClassElement.getConstructors().get(0);
          XVariableElement parameter = constructor.getParameters().get(0);
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(parameter));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.Outer.TestClass",
                          "  => element (CONSTRUCTOR): TestClass(java.lang.String)",
                          "  => element (PARAMETER): strParam",
                          "  => annotation: @test.Outer.TestAnnotation(classes={<%1$s>})",
                          "  => annotation value (TYPE_ARRAY): classes={<%1$s>}",
                          "  => annotation value (TYPE): classes=<%1$s>"),
                      isJavac ? "error" : "Error"));
        });
  }

  @Test
  public void invalidSuperclassInTypeHierarchy() {
    runTest(
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  Child<Long> getChild() { return null; }",
            "  static class Child<T> extends Parent<T> {}",
            "  static class Parent<T> extends MissingType<T> {}",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement outerElement = processingEnv.findTypeElement("test.Outer");
          XMethodElement getChildMethod = outerElement.getDeclaredMethods().get(0);
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () ->
                      superficialValidation.validateTypeHierarchyOf(
                          "return type", getChildMethod, getChildMethod.getReturnType()));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.Outer",
                          "  => element (METHOD): getChild()",
                          "  => type (DECLARED return type): test.Outer.Child<java.lang.Long>",
                          "  => type (DECLARED supertype): test.Outer.Parent<java.lang.Long>",
                          "  => type (ERROR supertype): %s"),
                      isJavac ? "MissingType<T>" : "error.NonExistentClass"));
        });
  }

  @Test
  public void invalidSuperclassTypeParameterInTypeHierarchy() {
    runTest(
        CompilerTests.javaSource(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  Child getChild() { return null; }",
            "  static class Child extends Parent<MissingType> {}",
            "  static class Parent<T> {}",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement outerElement = processingEnv.findTypeElement("test.Outer");
          XMethodElement getChildMethod = outerElement.getDeclaredMethods().get(0);
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () ->
                      superficialValidation.validateTypeHierarchyOf(
                          "return type", getChildMethod, getChildMethod.getReturnType()));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.Outer",
                          "  => element (METHOD): getChild()",
                          "  => type (DECLARED return type): test.Outer.Child",
                          "  => type (DECLARED supertype): test.Outer.Parent<%1$s>",
                          "  => type (ERROR type argument): %1$s"),
                      isJavac ? "MissingType" : "error.NonExistentClass"));
        });
  }

  private void runTest(Source source, AssertionHandler assertionHandler) {
    CompilerTests.daggerCompiler(source)
        .withProcessingSteps(() -> new AssertingStep(assertionHandler))
        .compile(subject -> subject.hasError());
  }

  private void runSuccessfulTest(Source source, AssertionHandler assertionHandler) {
    CompilerTests.daggerCompiler(source)
        .withProcessingSteps(() -> new AssertingStep(assertionHandler))
        .compile(subject -> subject.hasErrorCount(0));
  }

  private interface AssertionHandler {
    void runAssertions(
        XProcessingEnv processingEnv, DaggerSuperficialValidation superficialValidation);
  }

  private static final class AssertingStep implements XProcessingStep {
    private final AssertionHandler assertionHandler;
    private boolean processed = false;

    AssertingStep(AssertionHandler assertionHandler) {
      this.assertionHandler = assertionHandler;
    }

    @Override
    public final ImmutableSet<String> annotations() {
      return ImmutableSet.of("*");
    }

    @Override
    public ImmutableSet<XElement> process(
        XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
      if (!processed) {
        processed = true; // only process once.
        TestComponent component =
            DaggerDaggerSuperficialValidationTest_TestComponent.factory().create(env);
        assertionHandler.runAssertions(env, component.superficialValidation());
      }
      return ImmutableSet.of();
    }

    @Override
    public void processOver(
        XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {}
  }

  @Singleton
  @Component(modules = ProcessingEnvironmentModule.class)
  interface TestComponent {
    DaggerSuperficialValidation superficialValidation();

    @Component.Factory
    interface Factory {
      TestComponent create(@BindsInstance XProcessingEnv processingEnv);
    }
  }
}
