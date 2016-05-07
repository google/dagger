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

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourcesSubject.assertThat;

@RunWith(JUnit4.class)
public class MultibindingTest {
  @Test
  public void providesTypeAndAnnotationOnSameMethod_failsToCompile() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MultibindingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "import static dagger.Provides.Type.SET;",
            "import static dagger.Provides.Type.UNIQUE;",
            "",
            "@Module",
            "class MultibindingModule {",
            "  @Provides(type = SET) @IntoSet Integer provideInt() { ",
            "    return 1;",
            "  }",
            "  @Provides(type = UNIQUE) @IntoSet Integer provideConflictingMultibindingTypes() { ",
            "    return 2;",
            "  }",
            "}");

    assertThat(module)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Provides.type cannot be used with multibinding annotations")
        .in(module)
        .onLine(12)
        .and()
        .withErrorContaining("@Provides.type cannot be used with multibinding annotations")
        .in(module)
        .onLine(15);
  }

  @Test
  public void providesWithTwoMultibindingAnnotations_failsToCompile() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MultibindingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "class MultibindingModule {",
            "  @Provides @IntoSet @IntoMap Integer provideInt() { ",
            "    return 1;",
            "  }",
            "}");

    assertThat(module)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Multiple multibinding annotations cannot be placed on the same Provides method")
        .in(module)
        .onLine(10);
  }

  @Test
  public void producesTypeAndAnnotationOnSameMethod_failsToCompile() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.MultibindingModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.multibindings.IntoSet;",
            "",
            "import static dagger.producers.Produces.Type.SET;",
            "",
            "@ProducerModule",
            "class MultibindingModule {",
            "  @Produces(type = SET) @IntoSet Integer produceInt() { ",
            "    return 1;",
            "  }",
            "}");

    assertThat(module)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Produces.type cannot be used with multibinding annotations")
        .in(module)
        .onLine(11);
  }

  @Test
  public void appliedOnInvalidMethods_failsToCompile() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.SomeType",
            "package test;",
            "",
            "import java.util.Set;",
            "import java.util.Map;",
            "",
            "import dagger.Component;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.multibindings.IntoMap;",
            "",
            "interface SomeType {",
            "  @IntoSet Set<Integer> ints();",
            "  @ElementsIntoSet Set<Double> doubles();",
            "  @IntoMap Map<Integer, Double> map();",
            "}");

    assertThat(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Multibinding annotations may only be on @Provides or @Produces methods")
        .in(component)
        .onLine(12)
        .and()
        .withErrorContaining(
            "Multibinding annotations may only be on @Provides or @Produces methods")
        .in(component)
        .onLine(13)
        .and()
        .withErrorContaining(
            "Multibinding annotations may only be on @Provides or @Produces methods")
        .in(component)
        .onLine(14);
  }
}
