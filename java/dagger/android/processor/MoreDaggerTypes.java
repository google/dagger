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

package dagger.android.processor;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

// TODO(bcorso): Dedupe with dagger/internal/codegen/langmodel/DaggerTypes.java?
// TODO(bcorso): Contribute upstream to auto common?
/** Similar to auto common, but uses {@link ClassName} rather than {@link Class}. */
final class MoreDaggerTypes {

  /**
   * Returns true if the raw type underlying the given {@link TypeMirror} represents the same raw
   * type as the given {@link Class} and throws an IllegalArgumentException if the {@link
   * TypeMirror} does not represent a type that can be referenced by a {@link Class}
   */
  public static boolean isTypeOf(final TypeName typeName, TypeMirror type) {
    checkNotNull(typeName);
    return type.accept(new IsTypeOf(typeName), null);
  }

  private static final class IsTypeOf extends SimpleTypeVisitor8<Boolean, Void> {
    private final TypeName typeName;

    IsTypeOf(TypeName typeName) {
      this.typeName = typeName;
    }

    @Override
    protected Boolean defaultAction(TypeMirror type, Void ignored) {
      throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
    }

    @Override
    public Boolean visitNoType(NoType noType, Void p) {
      if (noType.getKind().equals(TypeKind.VOID)) {
        return typeName.equals(TypeName.VOID);
      }
      throw new IllegalArgumentException(noType + " cannot be represented as a Class<?>.");
    }

    @Override
    public Boolean visitError(ErrorType errorType, Void p) {
      return false;
    }

    @Override
    public Boolean visitPrimitive(PrimitiveType type, Void p) {
      switch (type.getKind()) {
        case BOOLEAN:
          return typeName.equals(TypeName.BOOLEAN);
        case BYTE:
          return typeName.equals(TypeName.BYTE);
        case CHAR:
          return typeName.equals(TypeName.CHAR);
        case DOUBLE:
          return typeName.equals(TypeName.DOUBLE);
        case FLOAT:
          return typeName.equals(TypeName.FLOAT);
        case INT:
          return typeName.equals(TypeName.INT);
        case LONG:
          return typeName.equals(TypeName.LONG);
        case SHORT:
          return typeName.equals(TypeName.SHORT);
        default:
          throw new IllegalArgumentException(type + " cannot be represented as a Class<?>.");
      }
    }

    @Override
    public Boolean visitArray(ArrayType array, Void p) {
      return (typeName instanceof ArrayTypeName)
          && isTypeOf(((ArrayTypeName) typeName).componentType, array.getComponentType());
    }

    @Override
    public Boolean visitDeclared(DeclaredType type, Void ignored) {
      TypeElement typeElement = MoreElements.asType(type.asElement());
      return (typeName instanceof ClassName)
          && typeElement.getQualifiedName().contentEquals(((ClassName) typeName).canonicalName());
    }
  }

  private MoreDaggerTypes() {}
}
