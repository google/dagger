/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen.writer;

import com.google.testing.compile.CompilationRule;
import java.nio.charset.Charset;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TypeNamesTest {
  @Rule public final CompilationRule compilation = new CompilationRule();

  private TypeElement getElement(Class<?> clazz) {
    return compilation.getElements().getTypeElement(clazz.getCanonicalName());
  }

  private TypeMirror getType(Class<?> clazz) {
    return getElement(clazz).asType();
  }

  @Test
  public void forTypeMirror_basicTypes() {
    assertThat(TypeNames.forTypeMirror(getType(Object.class)))
        .isEqualTo(ClassName.fromClass(Object.class));
    assertThat(TypeNames.forTypeMirror(getType(Charset.class)))
        .isEqualTo(ClassName.fromClass(Charset.class));
    assertThat(TypeNames.forTypeMirror(getType(TypeNamesTest.class)))
        .isEqualTo(ClassName.fromClass(TypeNamesTest.class));
  }

  @Test
  public void forTypeMirror_parameterizedType() {
    DeclaredType setType =
        compilation.getTypes().getDeclaredType(getElement(Set.class), getType(Object.class));
    assertThat(TypeNames.forTypeMirror(setType))
        .isEqualTo(ParameterizedTypeName.create(Set.class, ClassName.fromClass(Object.class)));
  }

  @Test
  public void forTypeMirror_typeVariables() {
    TypeMirror setType = getType(Set.class);
    assertThat(TypeNames.forTypeMirror(setType))
        .isEqualTo(ParameterizedTypeName.create(Set.class, TypeVariableName.named("E")));
  }

  @Test
  public void forTypeMirror_primitive() {
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.BOOLEAN)))
        .isEqualTo(PrimitiveName.BOOLEAN);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.BYTE)))
        .isEqualTo(PrimitiveName.BYTE);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.SHORT)))
        .isEqualTo(PrimitiveName.SHORT);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.INT)))
        .isEqualTo(PrimitiveName.INT);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.LONG)))
        .isEqualTo(PrimitiveName.LONG);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.CHAR)))
        .isEqualTo(PrimitiveName.CHAR);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.FLOAT)))
        .isEqualTo(PrimitiveName.FLOAT);
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getPrimitiveType(TypeKind.DOUBLE)))
        .isEqualTo(PrimitiveName.DOUBLE);
  }

  @Test
  public void forTypeMirror_arrays() {
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getArrayType(getType(Object.class))))
        .isEqualTo(new ArrayTypeName(ClassName.fromClass(Object.class)));
  }

  @Test
  public void forTypeMirror_void() {
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getNoType(TypeKind.VOID)))
        .isEqualTo(VoidName.VOID);
  }

  @Test
  public void forTypeMirror_null() {
    assertThat(TypeNames.forTypeMirror(compilation.getTypes().getNullType()))
        .isEqualTo(NullName.NULL);
  }
}
