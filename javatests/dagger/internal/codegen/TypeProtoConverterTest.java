/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;
import static javax.lang.model.util.ElementFilter.fieldsIn;

import com.google.testing.compile.CompilationRule;
import dagger.internal.Factory;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.serialization.TypeProto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeProtoConverter}. */
@RunWith(JUnit4.class)
public class TypeProtoConverterTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private DaggerElements elements;
  private DaggerTypes types;
  private TypeProtoConverter typeProtoConverter;

  @Before
  public void setUp() {
    this.elements = new DaggerElements(compilationRule.getElements(), compilationRule.getTypes());
    this.types = new DaggerTypes(compilationRule.getTypes(), elements);
    this.typeProtoConverter = new TypeProtoConverter(types, elements);
  }

  static class Outer<O> {
    @SuppressWarnings("ClassCanBeStatic") // We want to specifically test inner classes
    class Inner<I> {}
  }

  @SuppressWarnings({"rawtypes", "unused"})
  static class TypeMirrorConversionSubjects {
    private Map rawMap;
    private List<String> listOfString;
    private List<HashMap<String, Integer>> listOfHashMapOfStringToInteger;
    private Map<HashMap<String, Integer>, Set<Factory>> mapOfHashMapOfStringToIntegerToSetOfFactory;
    private Map<HashMap<String, Integer>, Set<Factory>>[][]
        arrayOfArrayOfMapOfHashMapOfStringToIntegerToSetOfFactory;
    private Map<HashMap<?, Integer>, ?> mapOfHashMapOfWildcardToIntegerToWildcard;
    private List<? extends String> listOfWildcardExtendsString;
    private List<? extends Set<? super String>> listOfWildcardExtendsSetOfWildcardSuperString;
    private Outer<Object>.Inner<Integer> outerOfObjectDotInnerOfInteger;
    private List<int[]> listOfIntArray;
    private List<? extends CharSequence[]> listOfWildcardExtendsCharSequenceArray;
  }

  @Test
  public void typeMirrorProtoConversions() {
    assertProtoConversionEquality(fieldType("rawMap"));
    assertProtoConversionEquality(fieldType("listOfString"));
    assertProtoConversionEquality(fieldType("listOfHashMapOfStringToInteger"));
    assertProtoConversionEquality(fieldType("mapOfHashMapOfStringToIntegerToSetOfFactory"));
    assertProtoConversionEquality(
        fieldType("arrayOfArrayOfMapOfHashMapOfStringToIntegerToSetOfFactory"));
    assertProtoConversionEquality(fieldType("mapOfHashMapOfWildcardToIntegerToWildcard"));
    assertProtoConversionEquality(fieldType("listOfWildcardExtendsString"));
    assertProtoConversionEquality(fieldType("listOfWildcardExtendsSetOfWildcardSuperString"));
    assertProtoConversionEquality(fieldType("outerOfObjectDotInnerOfInteger"));
    assertProtoConversionEquality(fieldType("listOfIntArray"));
    assertProtoConversionEquality(fieldType("listOfWildcardExtendsCharSequenceArray"));
  }

  private TypeMirror fieldType(String fieldName) {
    return fieldsIn(
            elements.getTypeElement(TypeMirrorConversionSubjects.class).getEnclosedElements())
        .stream()
        .filter(field -> field.getSimpleName().contentEquals(fieldName))
        .findFirst()
        .get()
        .asType();
  }

  /**
   * Converts {@link TypeMirror} to a {@link dagger.internal.codegen.serialization.TypeProto} and
   * back to a {@link TypeMirror}. Asserts that the round-trip conversion is lossless.
   */
  private void assertProtoConversionEquality(TypeMirror typeMirror) {
    TypeProto toProto = TypeProtoConverter.toProto(typeMirror);
    TypeMirror fromProto = typeProtoConverter.fromProto(toProto);
    assertWithMessage("expected: %s\nactual  : %s", typeMirror, fromProto)
        .that(types.isSameType(typeMirror, fromProto))
        .isTrue();
  }
}
