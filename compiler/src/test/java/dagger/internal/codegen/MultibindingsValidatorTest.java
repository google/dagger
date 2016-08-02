/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMultibindingsMethod;
import static java.util.Arrays.asList;

import com.google.testing.compile.JavaFileObjects;
import javax.inject.Qualifier;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultibindingsValidatorTest {

  private static final JavaFileObject SOME_QUALIFIER =
      JavaFileObjects.forSourceLines(
          "test.SomeQualifier",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier",
          "@interface SomeQualifier {}");

  private static final JavaFileObject OTHER_QUALIFIER =
      JavaFileObjects.forSourceLines(
          "test.OtherQualifier",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier",
          "@interface OtherQualifier {}");

  @Test
  public void abstractClass() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "class TestModule {",
            "  @Multibindings",
            "  static abstract class Empties {",
            "    abstract Set<Object> emptySet();",
            "    @SomeQualifier abstract Set<Object> emptyQualifiedSet();",
            "    abstract Map<String, Object> emptyMap();",
            "    @SomeQualifier abstract Map<String, Object> emptyQualifiedMap();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Multibindings can be applied only to interfaces")
        .in(testModule)
        .onLine(11);
  }

  @Test
  public void concreteClass() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "class TestModule {",
            "  @Multibindings",
            "  static class Empties {",
            "    Set<Object> emptySet() { return null; }",
            "    @SomeQualifier Set<Object> emptyQualifiedSet() { return null; }",
            "    Map<String, Object> emptyMap() { return null; }",
            "    @SomeQualifier Map<String, Object> emptyQualifiedMap() { return null; }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Multibindings can be applied only to interfaces")
        .in(testModule)
        .onLine(11);
  }

  @Test
  public void interfaceHasTypeParameters() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "class TestModule {",
            "  @Multibindings",
            "  interface Empties<T> {",
            "    Set<T> emptySet();",
            "    @SomeQualifier Set<T> emptyQualifiedSet();",
            "    Map<String, T> emptyMap();",
            "    @SomeQualifier Map<String, T> emptyQualifiedMap();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Multibindings types must not have type parameters")
        .in(testModule)
        .onLine(11);
  }

  @Test
  public void topLevel() {
    JavaFileObject testInterface =
        JavaFileObjects.forSourceLines(
            "test.TestInterface",
            "package test;",
            "",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Multibindings",
            "interface Empties {",
            "  Set<Object> emptySet();",
            "  @SomeQualifier Set<Object> emptyQualifiedSet();",
            "  Map<String, Object> emptyMap();",
            "  @SomeQualifier Map<String, Object> emptyQualifiedMap();",
            "}");
    assertAbout(javaSources())
        .that(asList(testInterface, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Multibindings types must be nested within a @Module or @ProducerModule")
        .in(testInterface)
        .onLine(8);
  }

  @Test
  public void notWithinModule() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "class TestModule {",
            "  @Multibindings",
            "  interface Empties {",
            "    Set<Object> emptySet();",
            "    @SomeQualifier Set<Object> emptyQualifiedSet();",
            "    Map<String, Object> emptyMap();",
            "    @SomeQualifier Map<String, Object> emptyQualifiedMap();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@Multibindings types must be nested within a @Module or @ProducerModule")
        .in(testModule)
        .onLine(9);
  }

  @Test
  public void voidMethod() {
    assertThatMultibindingsMethod("void voidMethod();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void primitiveMethod() {
    assertThatMultibindingsMethod("int primitive();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void rawMap() {
    assertThatMultibindingsMethod("Map rawMap();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void wildcardMap() {
    assertThatMultibindingsMethod("Map<?, ?> wildcardMap();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void providerMap() {
    assertThatMultibindingsMethod("Map<String, Provider<Object>> providerMap();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void producerMap() {
    assertThatMultibindingsMethod("Map<String, Producer<Object>> producerMap();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void producedMap() {
    assertThatMultibindingsMethod("Map<String, Produced<Object>> producedMap();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void rawSet() {
    assertThatMultibindingsMethod("Set rawSet();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void wildcardSet() {
    assertThatMultibindingsMethod("Set<?> wildcardSet();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void providerSet() {
    assertThatMultibindingsMethod("Set<Provider<Object>> providerSet();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void producerSet() {
    assertThatMultibindingsMethod("Set<Producer<Object>> producerSet();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void producedSet() {
    assertThatMultibindingsMethod("Set<Produced<Object>> producedSet();")
        .hasError("@Multibindings methods must return Map<K, V> or Set<T>");
  }

  @Test
  public void overqualifiedSet() {
    assertThatMultibindingsMethod(
            "@SomeQualifier @OtherQualifier Set<Object> tooManyQualifiersSet();")
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("Cannot use more than one @Qualifier");
  }

  @Test
  public void overqualifiedMap() {
    assertThatMultibindingsMethod(
            "@SomeQualifier @OtherQualifier Map<String, Object> tooManyQualifiersMap();")
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("Cannot use more than one @Qualifier");
  }

  @Test
  public void hasParameters() {
    assertThatMultibindingsMethod("Set<String> parameters(Object param);")
        .hasError("@Multibindings methods cannot have parameters");
  }

  @Test
  public void badMethodsOnSupertype() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import dagger.producers.Produced;",
            "import dagger.producers.Producer;",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "class TestModule {",
            "  interface BaseEmpties {",
            "    void voidMethod();",
            "  }",
            "",
            "  @Multibindings",
            "  interface Empties extends BaseEmpties {}",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER, OTHER_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "[test.TestModule.BaseEmpties.voidMethod()] "
                + "@Multibindings methods must return Map<K, V> or Set<T>")
        .in(testModule)
        .onLine(18);
  }

  @Test
  public void duplicateKeys() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "class TestModule {",
            "  @Multibindings",
            "  interface EmptySets {",
            "    Set<Object> emptySet();",
            "    Set<Object> emptySet2();",
            "  }",
            "",
            "  @Multibindings",
            "  interface EmptyQualifiedSets {",
            "    @SomeQualifier Set<Object> emptyQualifiedSet();",
            "    @SomeQualifier Set<Object> emptyQualifiedSet2();",
            "  }",
            "",
            "  @Multibindings",
            "  interface EmptyMaps {",
            "    Map<String, Object> emptyMap();",
            "    Map<String, Object> emptyMap2();",
            "  }",
            "",
            "  @Multibindings",
            "  interface EmptyQualifiedMaps {",
            "    @SomeQualifier Map<String, Object> emptyQualifiedMap();",
            "    @SomeQualifier Map<String, Object> emptyQualifiedMap2();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(asList(testModule, SOME_QUALIFIER))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Too many @Multibindings methods for Set<Object>:")
        .in(testModule)
        .onLine(11)
        .and()
        .withErrorContaining("Too many @Multibindings methods for @test.SomeQualifier Set<Object>:")
        .in(testModule)
        .onLine(17)
        .and()
        .withErrorContaining("Too many @Multibindings methods for Map<String,Provider<Object>>:")
        .in(testModule)
        .onLine(23)
        .and()
        .withErrorContaining(
            "Too many @Multibindings methods for @test.SomeQualifier Map<String,Provider<Object>>:")
        .in(testModule)
        .onLine(29);
  }

  @Qualifier
  public @interface SomeQualifier {}

  @Qualifier
  public @interface OtherQualifier {}
}
