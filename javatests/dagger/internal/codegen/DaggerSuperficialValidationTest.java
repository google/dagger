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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.junit.Assert.assertThrows;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import dagger.Component;
import dagger.internal.codegen.binding.DaggerSuperficialValidation;
import dagger.internal.codegen.binding.DaggerSuperficialValidation.ValidationException;
import dagger.internal.codegen.javac.JavacPluginModule;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Singleton;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DaggerSuperficialValidationTest {
  private static final Joiner NEW_LINES = Joiner.on("\n  ");

  @Test
  public void missingReturnType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract MissingType blah();",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (METHOD): blah()",
                            "  => type (EXECUTABLE method): ()MissingType",
                            "  => type (ERROR return type): MissingType"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingGenericReturnType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract MissingType<?> blah();",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (METHOD): blah()",
                            "  => type (EXECUTABLE method): ()<any>",
                            "  => type (ERROR return type): <any>"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingReturnTypeTypeParameter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "abstract class TestClass {",
            "  abstract Map<Set<?>, MissingType<?>> blah();",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (METHOD): blah()",
                            "  => type (EXECUTABLE method): "
                                + "()java.util.Map<java.util.Set<?>,<any>>",
                            "  => type (DECLARED return type): "
                                + "java.util.Map<java.util.Set<?>,<any>>",
                            "  => type (ERROR type argument): <any>"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingTypeParameter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass", //
            "package test;",
            "",
            "class TestClass<T extends MissingType> {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (TYPE_PARAMETER): T",
                            "  => type (ERROR bound type): MissingType"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingParameterType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract void foo(MissingType x);",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (METHOD): foo(MissingType)",
                            "  => type (EXECUTABLE method): (MissingType)void",
                            "  => type (ERROR parameter type): MissingType"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingAnnotation() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass", //
            "package test;",
            "",
            "@MissingAnnotation",
            "class TestClass {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => annotation: @MissingAnnotation",
                            "  => type (ERROR annotation type): MissingAnnotation"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void handlesRecursiveTypeParams() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass", //
            "package test;",
            "",
            "class TestClass<T extends Comparable<T>> {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                DaggerSuperficialValidation.validateElement(testClassElement);
              }
            })
        .compilesWithoutError();
  }

  @Test
  public void handlesRecursiveType() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "abstract class TestClass {",
            "  abstract TestClass foo(TestClass x);",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                DaggerSuperficialValidation.validateElement(testClassElement);
              }
            })
        .compilesWithoutError();
  }

  @Test
  public void missingWildcardBound() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
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
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (METHOD): extendsTest()",
                            "  => type (EXECUTABLE method): ()java.util.Set<? extends MissingType>",
                            "  => type (DECLARED return type): "
                                + "java.util.Set<? extends MissingType>",
                            "  => type (WILDCARD type argument): ? extends MissingType",
                            "  => type (ERROR extends bound type): MissingType"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void missingIntersection() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "class TestClass<T extends Number & Missing> {}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement = processingEnv.findTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.TestClass",
                            "  => element (TYPE_PARAMETER): T",
                            "  => type (ERROR bound type): Missing"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void invalidAnnotationValue() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  @interface TestAnnotation {",
            "    Class[] classes();",
            "  }",
            "",
            "  @TestAnnotation(classes = Foo)",
            "  static class TestClass {}",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement =
                    processingEnv.findTypeElement("test.Outer.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.Outer.TestClass",
                            "  => annotation: @test.Outer.TestAnnotation(classes = \"<error>\")",
                            "  => annotation method: java.lang.Class[] classes()",
                            "  => annotation value (ARRAY): value '<error>' with expected type"
                                + " java.lang.Class[]",
                            "  => annotation value (STRING): value '<error>' with expected type"
                                + " java.lang.Class"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void invalidAnnotationValueOnParameter() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  @interface TestAnnotation {",
            "    Class[] classes();",
            "  }",
            "",
            "  static class TestClass {",
            "    TestClass(@TestAnnotation(classes = Foo) String strParam) {}",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement testClassElement =
                    processingEnv.findTypeElement("test.Outer.TestClass");
                XConstructorElement constructor = testClassElement.getConstructors().get(0);
                XVariableElement parameter = constructor.getParameters().get(0);
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () -> DaggerSuperficialValidation.validateElement(parameter));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.Outer.TestClass",
                            "  => element (CONSTRUCTOR): TestClass(java.lang.String)",
                            "  => element (PARAMETER): strParam",
                            "  => annotation: @test.Outer.TestAnnotation(classes = \"<error>\")",
                            "  => annotation method: java.lang.Class[] classes()",
                            "  => annotation value (ARRAY): value '<error>' with expected type"
                                + " java.lang.Class[]",
                            "  => annotation value (STRING): value '<error>' with expected type"
                                + " java.lang.Class"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void invalidSuperclassInTypeHierarchy() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  Child<Long> getChild() { return null; }",
            "",
            "  static class Child<T> extends Parent<T> {}",
            "",
            "  static class Parent<T> extends MissingType<T> {}",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement outerElement = processingEnv.findTypeElement("test.Outer");
                XMethodElement getChildMethod = outerElement.getDeclaredMethods().get(0);
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () ->
                            DaggerSuperficialValidation.validateTypeHierarchyOf(
                                "return type", getChildMethod, getChildMethod.getReturnType()));
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => element (CLASS): test.Outer",
                            "  => element (METHOD): getChild()",
                            "  => type (DECLARED return type): test.Outer.Child<java.lang.Long>",
                            "  => type (DECLARED supertype): test.Outer.Parent<java.lang.Long>",
                            "  => type (ERROR supertype): MissingType<T>"));
              }
            })
        .failsToCompile();
  }

  @Test
  public void invalidSuperclassTypeParameterInTypeHierarchy() {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "final class Outer {",
            "  Child getChild() { return null; }",
            "",
            "  static class Child extends Parent<MissingType> {}",
            "",
            "  static class Parent<T> {}",
            "}");
    assertAbout(javaSource())
        .that(javaFileObject)
        .processedWith(
            new AssertingProcessor() {
              @Override
              void runAssertions(XProcessingEnv processingEnv) {
                XTypeElement outerElement = processingEnv.findTypeElement("test.Outer");
                XMethodElement getChildMethod = outerElement.getDeclaredMethods().get(0);
                ValidationException exception =
                    assertThrows(
                        ValidationException.KnownErrorType.class,
                        () ->
                            DaggerSuperficialValidation.validateTypeHierarchyOf(
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
              }
            })
        .failsToCompile();
  }

  private abstract static class AssertingProcessor extends AbstractProcessor {
    private boolean processed = false;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!processed) {
        processed = true; // only process once.
        XProcessingEnv xProcessingEnv =
            DaggerDaggerSuperficialValidationTest_TestComponent.builder()
                .javacPluginModule(
                    new JavacPluginModule(
                        processingEnv.getElementUtils(), processingEnv.getTypeUtils()))
                .build()
                .processingEnv();
        try {
          runAssertions(xProcessingEnv);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    }

    abstract void runAssertions(XProcessingEnv processingEnv) throws Exception;
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    XProcessingEnv processingEnv();
  }
}
