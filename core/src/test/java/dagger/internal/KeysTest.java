/**
 * Copyright (C) 2012 Square, Inc.
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
package dagger.internal;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static dagger.Provides.Type.SET;
import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class KeysTest {
  int primitive;
  @Test public void lonePrimitiveGetsBoxed() throws NoSuchFieldException {
    assertThat(fieldKey("primitive"))
        .isEqualTo("java.lang.Integer");
  }

  Map<String, List<Integer>> mapStringListInteger;
  @Test public void parameterizedTypes() throws NoSuchFieldException {
    assertThat(fieldKey("mapStringListInteger"))
        .isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
  }

  Map<String, int[]> mapStringArrayInt;
  @Test public void parameterizedTypeOfPrimitiveArray() throws NoSuchFieldException {
    assertThat(fieldKey("mapStringArrayInt"))
        .isEqualTo("java.util.Map<java.lang.String, int[]>");
  }

  @Named("foo") String annotatedType;
  @Test public void annotatedType() throws NoSuchFieldException {
    assertThat(fieldKey("annotatedType"))
        .isEqualTo("@javax.inject.Named(value=foo)/java.lang.String");
  }

  String className;
  @Test public void testGetClassName() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("className")))
        .isEqualTo("java.lang.String");
  }

  @Named("foo") String classNameWithAnnotation;
  @Test public void testGetClassNameWithoutAnnotation() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameWithAnnotation")))
        .isEqualTo("java.lang.String");
  }

  String[] classNameArray;
  @Test public void testGetClassNameArray() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameArray"))).isNull();
  }

  List<String> classNameParameterized;
  @Test public void testGetClassParameterized() throws NoSuchFieldException {
    assertThat(Keys.getClassName(fieldKey("classNameParameterized"))).isNull();
  }

  @Named("foo") String annotated;
  @Test public void testAnnotated() throws NoSuchFieldException {
    assertThat(fieldKey("annotated")).isEqualTo("@javax.inject.Named(value=foo)/java.lang.String");
    assertThat(Keys.isAnnotated(fieldKey("annotated"))).isTrue();
  }

  String notAnnotated;
  @Test public void testIsAnnotatedFalse() throws NoSuchFieldException {
    assertThat(Keys.isAnnotated(fieldKey("notAnnotated"))).isFalse();
  }

  Provider<String> providerOfType;
  String providedType;
  @Test public void testGetDelegateKey() throws NoSuchFieldException {
    assertThat(Keys.getBuiltInBindingsKey(fieldKey("providerOfType")))
        .isEqualTo(fieldKey("providedType"));
  }

  @Named("/@") Provider<String> providerOfTypeAnnotated;
  @Named("/@") String providedTypeAnnotated;
  @Test public void testGetDelegateKeyWithAnnotation() throws NoSuchFieldException {
    assertThat(Keys.getBuiltInBindingsKey(fieldKey("providerOfTypeAnnotated")))
        .isEqualTo(fieldKey("providedTypeAnnotated"));
  }

  @Named("/@") MembersInjector<String> membersInjectorOfType;
  @Named("/@") String injectedType;
  @Test public void testGetDelegateKeyWithMembersInjector() throws NoSuchFieldException {
    assertThat(Keys.getBuiltInBindingsKey(fieldKey("membersInjectorOfType")))
        .isEqualTo("members/java.lang.String");
  }

  @Named("/@") Lazy<String> lazyAnnotatedString;
  @Named("/@") String eagerAnnotatedString;
  @Test public void testAnnotatedGetLazyKey() throws NoSuchFieldException {
    assertThat(Keys.getLazyKey(fieldKey("lazyAnnotatedString")))
        .isEqualTo(fieldKey("eagerAnnotatedString"));
  }

  Lazy<String> lazyString;
  String eagerString;
  @Test public void testGetLazyKey() throws NoSuchFieldException {
    assertThat(Keys.getLazyKey(fieldKey("lazyString"))).isEqualTo(fieldKey("eagerString"));
  }

  @Test public void testGetLazyKey_WrongKeyType() throws NoSuchFieldException {
    assertThat(Keys.getLazyKey(fieldKey("providerOfTypeAnnotated"))).isNull();
  }

  @Provides(type=SET) String elementProvides() { return "foo"; }

  @Test public void testGetElementKey_NoQualifier() throws NoSuchMethodException {
    Method method = KeysTest.class.getDeclaredMethod("elementProvides", new Class<?>[]{});
    assertThat(Keys.getElementKey(method.getGenericReturnType(), method.getAnnotations(), method))
        .isEqualTo("java.util.Set<java.lang.String>");
  }

  @Named("foo")
  @Provides(type=SET) String qualifiedElementProvides() { return "foo"; }

  @Test public void testGetElementKey_WithQualifier() throws NoSuchMethodException {
    Method method = KeysTest.class.getDeclaredMethod("qualifiedElementProvides", new Class<?>[]{});
    assertThat(Keys.getElementKey(method.getGenericReturnType(), method.getAnnotations(), method))
        .isEqualTo("@javax.inject.Named(value=foo)/java.util.Set<java.lang.String>");
  }

  private String fieldKey(String fieldName) throws NoSuchFieldException {
    Field field = KeysTest.class.getDeclaredField(fieldName);
    return Keys.get(field.getGenericType(), field.getAnnotations(), field);
  }

}
