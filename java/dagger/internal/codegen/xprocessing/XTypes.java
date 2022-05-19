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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.XTypeKt.isArray;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XArrayType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.type.TypeKind;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XType} helper methods. */
public final class XTypes {
  private static final Equivalence<XType> XTYPE_EQUIVALENCE =
      new Equivalence<XType>() {
        @Override
        protected boolean doEquivalent(XType left, XType right) {
          return left.getTypeName().equals(right.getTypeName());
        }

        @Override
        protected int doHash(XType type) {
          return type.getTypeName().hashCode();
        }

        @Override
        public String toString() {
          return "XTypes.equivalence()";
        }
      };

  /**
   * Returns an {@link Equivalence} for {@link XType}.
   *
   * <p>Currently, this equivalence does not take into account nullability, as it just relies on
   * JavaPoet's {@link TypeName}. Thus, two types with the same type name but different nullability
   * are equal with this equivalence.
   */
  public static Equivalence<XType> equivalence() {
    return XTYPE_EQUIVALENCE;
  }

  /** Returns {@code true} if and only if the {@code type1} is assignable to {@code type2}. */
  public static boolean isAssignableTo(XType type1, XType type2) {
    return type2.isAssignableFrom(type1);
  }

  /**
   * Throws {@link TypeNotPresentException} if {@code type} is an {@link
   * javax.lang.model.type.ErrorType}.
   */
  public static void checkTypePresent(XType type) {
    if (isArray(type)) {
      checkTypePresent(asArray(type).getComponentType());
    } else if (isDeclared(type)) {
      type.getTypeArguments().forEach(XTypes::checkTypePresent);
    } else if (type.isError()) {
      throw new TypeNotPresentException(type.toString(), null);
    }
  }

  /** Returns {@code true} if the given type is a raw type of a parameterized type. */
  public static boolean isRawParameterizedType(XType type) {
    return isDeclared(type)
        && type.getTypeArguments().isEmpty()
        && !type.getTypeElement().getType().getTypeArguments().isEmpty();
  }

  /** Returns the given {@code type} as an {@link XArrayType}. */
  public static XArrayType asArray(XType type) {
    return (XArrayType) type;
  }

  /** Returns {@code true} if the raw type of {@code type} is equal to {@code className}. */
  public static boolean isTypeOf(XType type, ClassName className) {
    return isDeclared(type) && type.getTypeElement().getClassName().equals(className);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isWildcard(XType type) {
    return toJavac(type).getKind().equals(TypeKind.WILDCARD);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isDeclared(XType type) {
    return type.getTypeElement() != null;
  }

  /** Returns {@code true} if the given type is a type variable. */
  public static boolean isTypeVariable(XType type) {
    return XConverters.toJavac(type).getKind() == TypeKind.TYPEVAR;
  }

  /** Returns {@code true} if {@code type1} is equivalent to {@code type2}. */
  public static boolean areEquivalentTypes(XType type1, XType type2) {
    return type1.getTypeName().equals(type2.getTypeName());
  }

  /** Returns {@code true} if the given type is a primitive type. */
  public static boolean isPrimitive(XType type) {
    return XConverters.toJavac(type).getKind().isPrimitive();
  }

  /** Returns {@code true} if the given type has type parameters. */
  public static boolean hasTypeParameters(XType type) {
    return !type.getTypeArguments().isEmpty();
  }

  /**
   * Returns the non-{@link Object} superclass of the type with the proper type parameters. An empty
   * {@link Optional} is returned if there is no non-{@link Object} superclass.
   */
  public static Optional<XType> nonObjectSuperclass(XType type) {
    return isDeclared(type)
        ? type.getSuperTypes().stream()
            .filter(supertype -> !supertype.getTypeName().equals(TypeName.OBJECT))
            .filter(supertype -> isDeclared(supertype) && supertype.getTypeElement().isClass())
            .collect(toOptional())
        : Optional.empty();
  }

  /**
   * Returns {@code type}'s single type argument.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has zero or more
   *     than one type arguments.
   */
  public static XType unwrapType(XType type) {
    XType unwrapped = unwrapTypeOrDefault(type, null);
    checkArgument(unwrapped != null, "%s is a raw type", type);
    return unwrapped;
  }

  private static XType unwrapTypeOrDefault(XType type, XType defaultType) {
    // Check the type parameters of the element's XType since the input XType could be raw.
    checkArgument(isDeclared(type));
    XTypeElement typeElement = type.getTypeElement();
    checkArgument(
        typeElement.getType().getTypeArguments().size() == 1,
        "%s does not have exactly 1 type parameter. Found: %s",
        typeElement.getQualifiedName(),
        typeElement.getType().getTypeArguments());
    return getOnlyElement(type.getTypeArguments(), defaultType);
  }

  private XTypes() {}
}
