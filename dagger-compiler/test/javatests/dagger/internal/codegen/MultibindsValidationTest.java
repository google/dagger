/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;

import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.Collection;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultibindsValidationTest {

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{Module.class}, {ProducerModule.class}});
  }

  private final String moduleDeclaration;

  public MultibindsValidationTest(Class<? extends Annotation> moduleAnnotation) {
    moduleDeclaration = "@" + moduleAnnotation.getCanonicalName() + " abstract class %s { %s }";
  }

  @Test
  public void notWithinModule() {
    assertThatMethodInUnannotatedClass("@Multibinds abstract Set<Object> emptySet();")
        .hasError("@Multibinds methods can only be present within a @Module or @ProducerModule");
  }

  @Test
  public void voidMethod() {
    assertThatModuleMethod("@Multibinds abstract void voidMethod();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods return type must be either a Set or Map type.");
  }

  @Test
  public void primitiveMethod() {
    assertThatModuleMethod("@Multibinds abstract int primitive();")
        .withDeclaration(moduleDeclaration)
        .hasError("@Multibinds methods return type must be either a Set or Map type.");
  }

  @Test
  public void rawMap() {
    assertThatModuleMethod("@Multibinds abstract Map rawMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot be a raw Map type");
  }

  @Test
  public void wildcardMapValue() {
    assertThatModuleMethod("@Multibinds abstract Map<String, ?> wildcardMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Map value type.");
  }

  @Test
  public void wildcardMapValueWithBounds() {
    assertThatModuleMethod("@Multibinds abstract Map<String, ? extends Number> wildcardMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Map value type.");
  }

  @Test
  public void wildcardMapKey() {
    assertThatModuleMethod("@Multibinds abstract Map<?, String> wildcardMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Map key type.");
  }

  @Test
  public void wildcardMapKeyWithBounds() {
    assertThatModuleMethod("@Multibinds abstract Map<? extends Number, String> wildcardMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Map key type.");
  }

  @Test
  public void providerMap() {
    assertThatModuleMethod("@Multibinds abstract Map<String, Provider<Object>> providerMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use 'Provider' in the Map value type.");
  }

  @Test
  public void producerMap() {
    assertThatModuleMethod("@Multibinds abstract Map<String, Producer<Object>> producerMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use 'Producer' in the Map value type.");
  }

  @Test
  public void producedMap() {
    assertThatModuleMethod("@Multibinds abstract Map<String, Produced<Object>> producedMap();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use 'Produced' in the Map value type.");
  }

  @Test
  public void rawSet() {
    assertThatModuleMethod("@Multibinds abstract Set rawSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot be a raw Set type");
  }

  @Test
  public void wildcardSet() {
    assertThatModuleMethod("@Multibinds abstract Set<?> wildcardSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Set value type.");
  }

  @Test
  public void wildcardSetWithBounds() {
    assertThatModuleMethod("@Multibinds abstract Set<? extends Number> wildcardSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use a wildcard as the Set value type.");
  }

  @Test
  public void producedSet() {
    assertThatModuleMethod("@Multibinds abstract Set<Produced<Object>> producedSet();")
        .withDeclaration(moduleDeclaration)
        .hasError("return type cannot use 'Produced' in the Set value type.");
  }

  @Test
  public void overqualifiedSet() {
    assertThatModuleMethod(
            "@Multibinds @SomeQualifier @OtherQualifier "
                + "abstract Set<Object> tooManyQualifiersSet();")
        .withDeclaration(moduleDeclaration)
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  public void overqualifiedMap() {
    assertThatModuleMethod(
            "@Multibinds @SomeQualifier @OtherQualifier "
                + "abstract Map<String, Object> tooManyQualifiersMap();")
        .withDeclaration(moduleDeclaration)
        .importing(SomeQualifier.class, OtherQualifier.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  public void hasParameters() {
    assertThatModuleMethod("@Multibinds abstract Set<String> parameters(Object param);")
        .hasError("@Multibinds methods cannot have parameters");
  }

  @Qualifier
  @SuppressWarnings("MissingRuntimeRetention") // For testing.
  public @interface SomeQualifier {}

  @Qualifier
  @SuppressWarnings("MissingRuntimeRetention") // For testing.
  public @interface OtherQualifier {}
}
