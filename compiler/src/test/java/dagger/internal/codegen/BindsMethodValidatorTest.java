/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

@RunWith(Parameterized.class)
public class BindsMethodValidatorTest {
  @Parameters
  public static Collection<Object[]> data() {
    return ImmutableList.copyOf(new Object[][] {{Module.class}, {ProducerModule.class}});
  }

  private final Class<? extends Annotation> moduleAnnotation;

  public BindsMethodValidatorTest(Class<? extends Annotation> moduleAnnotation) {
    this.moduleAnnotation = moduleAnnotation;
  }

  @Test
  public void nonAbstract() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            "@" + moduleAnnotation.getCanonicalName(),
            "abstract class TestModule {",
            "  @Binds Object bindObject(String impl) { return null; }",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("must be abstract")
        .in(moduleFile)
        .onLine(7);
  }

  @Test
  public void notAssignable() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            "@" + moduleAnnotation.getCanonicalName(),
            "abstract class TestModule {",
            "  @Binds abstract String bindString(Object impl);",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("assignable")
        .in(moduleFile)
        .onLine(7);
  }

  @Test
  public void moreThanOneParamter() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            "@" + moduleAnnotation.getCanonicalName(),
            "abstract class TestModule {",
            "  @Binds abstract Object bindObject(String s1, String s2);",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("one parameter")
        .in(moduleFile)
        .onLine(7);
  }

  @Test
  public void typeParameters() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            "@" + moduleAnnotation.getCanonicalName(),
            "abstract class TestModule {",
            "  @Binds abstract <S, T extends S> S bindS(T t);",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("type parameters")
        .in(moduleFile)
        .onLine(7);
  }

  @Test
  public void notInModule() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            "abstract class TestModule {",
            "  @Binds abstract Object bindObject(String s);",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("within a @Module or @ProducerModule")
        .in(moduleFile)
        .onLine(6);
  }

  @Test
  public void throwsException() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import java.io.IOException;",
            "",
            "@" + moduleAnnotation.getCanonicalName(),
            "abstract class TestModule {",
            "  @Binds abstract Object bindObject(String s1) throws IOException;",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("only throw unchecked")
        .in(moduleFile)
        .onLine(8);
  }
}
