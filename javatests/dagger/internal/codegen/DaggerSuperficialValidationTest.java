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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.binding.DaggerSuperficialValidation;
import dagger.internal.codegen.binding.DaggerSuperficialValidation.ValidationException;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => METHOD element: blah()",
                            "  => (EXECUTABLE) METHOD type: ()MissingType",
                            "  => (ERROR) return type: MissingType"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => METHOD element: blah()",
                            "  => (EXECUTABLE) METHOD type: ()<any>",
                            "  => (ERROR) return type: <any>"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => METHOD element: blah()",
                            "  => (EXECUTABLE) METHOD type:"
                                + " ()java.util.Map<java.util.Set<?>,<any>>",
                            "  => (DECLARED) return type: java.util.Map<java.util.Set<?>,<any>>",
                            "  => (ERROR) type argument: <any>"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => TYPE_PARAMETER element: T",
                            "  => (ERROR) bound type: MissingType"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => METHOD element: foo(MissingType)",
                            "  => (EXECUTABLE) METHOD type: (MissingType)void",
                            "  => (ERROR) parameter type: MissingType"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => annotation: @MissingAnnotation",
                            "  => (ERROR) annotation type: MissingAnnotation"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => METHOD element: extendsTest()",
                            "  => (EXECUTABLE) METHOD type: ()java.util.Set<? extends MissingType>",
                            "  => (DECLARED) return type: java.util.Set<? extends MissingType>",
                            "  => (WILDCARD) type argument: ? extends MissingType",
                            "  => (ERROR) extends bound type: MissingType"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.TestClass",
                            "  => TYPE_PARAMETER element: T",
                            "  => (ERROR) bound type: Missing"));
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
              void runAssertions() {
                TypeElement testClassElement =
                    processingEnv.getElementUtils().getTypeElement("test.Outer.TestClass");
                ValidationException exception =
                    assertThrows(
                        ValidationException.class,
                        () -> DaggerSuperficialValidation.validateElement(testClassElement));
                assertThat(exception.fromUnexpectedThrowable()).isFalse();
                assertThat(exception)
                    .hasMessageThat()
                    .contains(
                        NEW_LINES.join(
                            "Validation trace:",
                            "  => CLASS element: test.Outer.TestClass",
                            "  => annotation: @test.Outer.TestAnnotation(classes={<error>})",
                            "  => annotation value: classes",
                            "  => 'array' annotation value, <error>, with expected type:"
                                + " java.lang.Class[]",
                            "  => 'default' annotation value, <error>, with expected type:"
                                + " java.lang.Class"));
              }
            })
        .failsToCompile();
  }

  private abstract static class AssertingProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      try {
        runAssertions();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return false;
    }

    abstract void runAssertions() throws Exception;
  }
}
