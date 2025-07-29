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
import com.google.common.collect.ImmutableList;
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
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DaggerSuperficialValidationTest {
  enum SourceKind {
    JAVA,
    KOTLIN
  }

  @Parameters(name = "sourceKind={0}")
  public static ImmutableList<Object[]> parameters() {
    return ImmutableList.of(new Object[] {SourceKind.JAVA}, new Object[] {SourceKind.KOTLIN});
  }

  private final SourceKind sourceKind;

  public DaggerSuperficialValidationTest(SourceKind sourceKind) {
    this.sourceKind = sourceKind;
  }

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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "abstract class TestClass {",
            "  abstract fun blah(): MissingType",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.TestClass",
                      "  => element (METHOD): blah()",
                      "  => type (ERROR return type): MissingType"));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "abstract class TestClass {",
            "  abstract fun blah(): MissingType<*>",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          final String errorType;
          if (processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC) {
            // JDK 24 improves error type information.
            errorType =
                Runtime.version().feature() >= 24
                    ? isKAPT(processingEnv) ? "MissingType" : "MissingType<?>"
                    : "<any>";
          } else {
            errorType = "MissingType";
          }
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => element (METHOD): blah()",
                          "  => type (ERROR return type): %1$s"),
                      errorType));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "abstract class TestClass {",
            "  abstract fun blah(): Map<Set<*>, MissingType<*>>",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          final String errorType;
          if (processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC) {
            // JDK 24 improves error type information.
            errorType = Runtime.version().feature() >= 24 ? "MissingType<?>" : "<any>";
          } else {
            errorType = "MissingType";
          }
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
                      errorType));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt", //
            "package test",
            "",
            "class TestClass<T : MissingType>"),
        (processingEnv, superficialValidation) -> {
          if (isKAPT(processingEnv)) {
            // The KAPT java stub doesn't reference the MissingType symbol (b/268536260#comment2).
            return;
          }
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.TestClass",
                      "  => element (TYPE_PARAMETER): T",
                      "  => type (ERROR bound type): MissingType"));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "abstract class TestClass {",
            "  abstract fun foo(param: MissingType);",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.TestClass",
                      "  => element (METHOD): foo(MissingType)",
                      "  => element (PARAMETER): param",
                      "  => type (ERROR parameter type): MissingType"));
        });
  }

  @org.junit.Ignore // TODO(b/394093156): This is a known issue with JDK17.
  @Test
  public void missingAnnotation() {
    runTest(
        CompilerTests.javaSource(
            "test.TestClass", //
            "package test;",
            "",
            "@MissingAnnotation",
            "class TestClass {}"),
        CompilerTests.kotlinSource(
            "test.TestClass.kt", //
            "package test",
            "",
            "@MissingAnnotation",
            "class TestClass"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          String errorType =
              processingEnv.getBackend() == XProcessingEnv.Backend.KSP
                      && sourceKind == SourceKind.JAVA
                  ? "error.NonExistentClass"
                  : "MissingAnnotation";
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  String.format(
                      NEW_LINES.join(
                          "Validation trace:",
                          "  => element (CLASS): test.TestClass",
                          "  => annotation type: MissingAnnotation",
                          "  => type (ERROR annotation type): %s"),
                      errorType));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt", //
            "package test",
            "",
            "class TestClass<T : Comparable<T>>"),
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "abstract class TestClass {",
            "  abstract fun foo(x: TestClass): TestClass",
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
            "  static final class Foo<T> {}",
            "",
            "  Foo<? extends MissingType> extendsTest() {",
            "    return null;",
            "  }",
            "",
            "  Foo<? super MissingType> superTest() {",
            "    return null;",
            "  }",
            "}"),
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "class TestClass {",
            "  class Foo<T>",
            "",
            "  fun extendsTest(): Foo<out MissingType> = TODO()",
            "",
            "  fun superTest(): Foo<in MissingType> = TODO()",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.TestClass",
                      "  => element (METHOD): extendsTest()",
                      "  => type (DECLARED return type): test.TestClass.Foo<? extends MissingType>",
                      "  => type (WILDCARD type argument): ? extends MissingType",
                      "  => type (ERROR extends bound type): MissingType"));
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
        CompilerTests.kotlinSource(
            "test.TestClass.kt",
            "package test",
            "",
            "class TestClass<T> where T: Number, T: Missing"),
        (processingEnv, superficialValidation) -> {
          if (isKAPT(processingEnv)) {
            // The KAPT java stub doesn't reference the MissingType symbol (b/268536260#comment2).
            return;
          }
          XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.TestClass",
                      "  => element (TYPE_PARAMETER): T",
                      "  => type (ERROR bound type): Missing"));
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
        CompilerTests.kotlinSource(
            "test.Outer.kt",
            "package test",
            "",
            "class Outer {",
            "  annotation class TestAnnotation(",
            "    val classes: Array<kotlin.reflect.KClass<*>>",
            "  )",
            "",
            "  @TestAnnotation(classes = [MissingType::class])",
            "  class TestClass {}",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement testClassElement = processingEnv.findTypeElement("test.Outer.TestClass");
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(testClassElement));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          String expectedMessage =
              String.format(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.Outer.TestClass",
                      "  => annotation type: test.Outer.TestAnnotation",
                      "  => annotation: @test.Outer.TestAnnotation(classes={%1$s})",
                      "  => annotation value (TYPE_ARRAY): classes={%1$s}",
                      "  => annotation value (TYPE): classes=%1$s"),
                  isJavac ? "<error>" : "MissingType");
          if (!isJavac) {
            expectedMessage =
                NEW_LINES.join(
                    expectedMessage,
                    "  => type (ERROR annotation value type): MissingType");
          }
          assertThat(exception).hasMessageThat().contains(expectedMessage);
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
        CompilerTests.kotlinSource(
            "test.Outer.kt",
            "package test",
            "",
            "class Outer {",
            "  annotation class TestAnnotation(",
            "    val classes: Array<kotlin.reflect.KClass<*>>",
            "  )",
            "",
            "  class TestClass(",
            "      @TestAnnotation(classes = [MissingType::class]) strParam: String",
            "  )",
            "}"),
        (processingEnv, superficialValidation) -> {
          if (isKAPT(processingEnv)) {
            // The KAPT java stub doesn't reference the MissingType symbol (b/268536260#comment2).
            return;
          }
          XTypeElement testClassElement = processingEnv.findTypeElement("test.Outer.TestClass");
          XConstructorElement constructor = testClassElement.getConstructors().get(0);
          XVariableElement parameter = constructor.getParameters().get(0);
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () -> superficialValidation.validateElement(parameter));
          // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
          boolean isJavac = processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC;
          String expectedMessage =
              String.format(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.Outer.TestClass",
                      "  => element (CONSTRUCTOR): TestClass(java.lang.String)",
                      "  => element (PARAMETER): strParam",
                      "  => annotation type: test.Outer.TestAnnotation",
                      "  => annotation: @test.Outer.TestAnnotation(classes={%1$s})",
                      "  => annotation value (TYPE_ARRAY): classes={%1$s}",
                      "  => annotation value (TYPE): classes=%1$s"),
                  isJavac ? "<error>" : "MissingType");
          if (!isJavac) {
            expectedMessage =
                NEW_LINES.join(
                    expectedMessage,
                    "  => type (ERROR annotation value type): MissingType");
          }
          assertThat(exception).hasMessageThat().contains(expectedMessage);
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
        CompilerTests.kotlinSource(
            "test.Outer.kt",
            "package test",
            "",
            "class Outer {",
            "  fun getChild(): Child<Long> = TODO()",
            "  class Child<T> : Parent<T>",
            "  open class Parent<T> : MissingType<T>",
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
                      isJavac ? "MissingType<T>" : "MissingType"));
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
        CompilerTests.kotlinSource(
            "test.Outer.kt",
            "package test",
            "",
            "class Outer {",
            "  fun getChild(): Child = TODO()",
            "  class Child : Parent<MissingType>()",
            "  open class Parent<T>",
            "}"),
        (processingEnv, superficialValidation) -> {
          XTypeElement outerElement = processingEnv.findTypeElement("test.Outer");
          XMethodElement getChildMethod = outerElement.getDeclaredMethods().get(0);
          if (isKAPT(processingEnv)) {
            // https://youtrack.jetbrains.com/issue/KT-34193/Kapt-CorrectErrorTypes-doesnt-work-for-generics
            // There's no way to work around this bug in KAPT so validation doesn't catch this case.
            superficialValidation.validateTypeHierarchyOf(
                "return type", getChildMethod, getChildMethod.getReturnType());
            return;
          }
          ValidationException exception =
              assertThrows(
                  ValidationException.KnownErrorType.class,
                  () ->
                      superficialValidation.validateTypeHierarchyOf(
                          "return type", getChildMethod, getChildMethod.getReturnType()));
          assertThat(exception)
              .hasMessageThat()
              .contains(
                  NEW_LINES.join(
                      "Validation trace:",
                      "  => element (CLASS): test.Outer",
                      "  => element (METHOD): getChild()",
                      "  => type (DECLARED return type): test.Outer.Child",
                      "  => type (DECLARED supertype): test.Outer.Parent<MissingType>",
                      "  => type (ERROR type argument): MissingType"));
        });
  }

  private void runTest(
      Source.JavaSource javaSource,
      Source.KotlinSource kotlinSource,
      AssertionHandler assertionHandler) {
    CompilerTests.daggerCompiler(sourceKind == SourceKind.JAVA ? javaSource : kotlinSource)
        .withProcessingSteps(() -> new AssertingStep(assertionHandler))
        // We're expecting compiler errors that we assert on in the assertionHandler.
        .compile(subject -> subject.hasError());
  }

  private void runSuccessfulTest(
      Source.JavaSource javaSource,
      Source.KotlinSource kotlinSource,
      AssertionHandler assertionHandler) {
    CompilerTests.daggerCompiler(sourceKind == SourceKind.JAVA ? javaSource : kotlinSource)
        .withProcessingSteps(() -> new AssertingStep(assertionHandler))
        .compile(subject -> subject.hasErrorCount(0));
  }

  private boolean isKAPT(XProcessingEnv processingEnv) {
    return processingEnv.getBackend() == XProcessingEnv.Backend.JAVAC
        && sourceKind == SourceKind.KOTLIN;
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
