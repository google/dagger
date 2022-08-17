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
import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XTypes.asArray;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XArrayType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

  // TODO(bcorso): Support XType.getEnclosingType() properly in XProcessing.
  public static XType getEnclosingType(XType type) {
    checkArgument(isDeclared(type));
    XProcessingEnv.Backend backend = getProcessingEnv(type).getBackend();
    switch (backend) {
      case JAVAC:
        return toXProcessing(asDeclared(toJavac(type)).getEnclosingType(), getProcessingEnv(type));
      case KSP:
        // For now, just return the enclosing type of the XTypeElement, which for most cases is good
        // enough. This may be incorrect in some rare cases (not tested), e.g. if Outer.Inner<T>
        // inherits its type parameter from Outer<T> then the enclosing type of Outer.Inner<Foo>
        // should be Outer<Foo> rather than Outer<T>, as we would get from the code below.
        XTypeElement enclosingTypeElement = type.getTypeElement().getEnclosingTypeElement();
        return enclosingTypeElement == null ? null : enclosingTypeElement.getType();
    }
    throw new AssertionError("Unexpected backend: " + backend);
  }

  /** Returns {@code true} if and only if the {@code type1} is assignable to {@code type2}. */
  public static boolean isAssignableTo(XType type1, XType type2) {
    return type2.isAssignableFrom(type1);
  }

  /** Returns {@code true} if {@code type1} is a subtype of {@code type2}. */
  public static boolean isSubtype(XType type1, XType type2) {
    XProcessingEnv processingEnv = getProcessingEnv(type1);
    switch (processingEnv.getBackend()) {
      case JAVAC:
        // The implementation used for KSP should technically also work in Javac but we avoid it to
        // avoid any possible regressions in Javac.
        return toJavac(processingEnv)
            .getTypeUtils() // ALLOW_TYPES_ELEMENTS
            .isSubtype(toJavac(type1), toJavac(type2));
      case KSP:
        if (isPrimitive(type1) || isPrimitive(type2)) {
            // For primitive types we can't just check isAssignableTo since auto-boxing means boxed
            // types are assignable to primitive (and vice versa) though neither are subtypes.
            return type1.isSameType(type2);
        }
        return isAssignableTo(type1, type2);
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  /** Returns the erasure of the given {@link TypeName}. */
  public static TypeName erasedTypeName(XType type) {
    XProcessingEnv processingEnv = getProcessingEnv(type);
    switch (processingEnv.getBackend()) {
      case JAVAC:
        // The implementation used for KSP should technically also work in Javac but we avoid it to
        // avoid any possible regressions in Javac.
        return XProcessingEnvs.erasure(type, processingEnv).getTypeName();
      case KSP:
        // In KSP, we have to derive the erased TypeName ourselves.
        return erasedTypeName(type.getTypeName());
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  private static TypeName erasedTypeName(TypeName typeName) {
    // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.6
    if (typeName instanceof ArrayTypeName) {
      // Erasure of 'C[]' is '|C|[]'
      return ArrayTypeName.of(erasedTypeName(((ArrayTypeName) typeName).componentType));
    } else if (typeName instanceof ParameterizedTypeName) {
      // Erasure of 'C<T1, T2, ...>' is '|C|'
      // Erasure of nested type T.C is |T|.C
      // Nested types, e.g. Foo<String>.Bar, are also represented as ParameterizedTypeName and
      // calling ParameterizedTypeName.rawType gives the correct result, e.g. Foo.Bar.
      return ((ParameterizedTypeName) typeName).rawType;
    } else if (typeName instanceof TypeVariableName) {
      // Erasure of type variable is the erasure of its left-most bound
      return erasedTypeName(((TypeVariableName) typeName).bounds.get(0));
    }
    // For every other type, the erasure is the type itself.
    return typeName;
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

  /** Returns {@code true} if the given type represents the {@code null} type. */
  public static boolean isNullType(XType type) {
    XProcessingEnv.Backend backend = getProcessingEnv(type).getBackend();
    switch (backend) {
      case JAVAC: return toJavac(type).getKind().equals(TypeKind.NULL);
      // AFAICT, there's no way to actually get a "null" type in KSP's model
      case KSP:
        return false;
    }
    throw new AssertionError("Unexpected backend: " + backend);
  }

  /** Returns {@code true} if the given type has no actual type. */
  public static boolean isNoType(XType type) {
    return type.isNone() || isVoid(type);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isWildcard(XType type) {
    XProcessingEnv.Backend backend = getProcessingEnv(type).getBackend();
    switch (backend) {
      case JAVAC:
        // In Javac, check the TypeKind directly. This also avoids a Javac bug (b/242569252) where
        // calling XType.getTypeName() too early caches an incorrect type name.
        return toJavac(type).getKind().equals(TypeKind.WILDCARD);
      case KSP:
        // TODO(bcorso): Consider representing this as an actual type in XProcessing.
        return type.getTypeName() instanceof WildcardTypeName;
    }
    throw new AssertionError("Unexpected backend: " + backend);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isDeclared(XType type) {
    // TODO(b/241477426): Due to a bug in XProcessing, array types accidentally get assigned an
    // invalid XTypeElement, so we check explicitly until this is fixed.
    // TODO(b/242918001): Due to a bug in XProcessing, wildcard types accidentally get assigned an
    // invalid XTypeElement, so we check explicitly until this is fixed.
    return !isWildcard(type) && !isArray(type) && type.getTypeElement() != null;
  }

  /** Returns {@code true} if the given type is a type variable. */
  public static boolean isTypeVariable(XType type) {
    // TODO(bcorso): Consider representing this as an actual type in XProcessing.
    return type.getTypeName() instanceof TypeVariableName;
  }

  /** Returns {@code true} if {@code type1} is equivalent to {@code type2}. */
  public static boolean areEquivalentTypes(XType type1, XType type2) {
    return type1.getTypeName().equals(type2.getTypeName());
  }

  /** Returns {@code true} if the given type is a primitive type. */
  public static boolean isPrimitive(XType type) {
    // TODO(bcorso): Consider representing this as an actual type in XProcessing.
    return type.getTypeName().isPrimitive();
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

  /**
   * Returns a string representation of {@link XType} that is independent of the backend
   * (javac/ksp).
   */
  // TODO(b/241141586): Replace this with TypeName.toString(). Technically, TypeName.toString()
  // should already be independent of the backend but we supply our own custom implementation to
  // remain backwards compatible with the previous implementation, which used TypeMirror#toString().
  public static String toStableString(XType type) {
    return toStableString(type.getTypeName(), /* visitedTypeVariables= */ new HashSet<>());
  }

  // Note: This method keeps track of the already visited type variables to avoid infinite recursion
  // for types like Enum<E extends Enum<E>>. We avoid the recursion by only outputting the type
  // variable's name (without any bounds) on subsequent visits.
  private static String toStableString(
      TypeName typeName, Set<TypeVariableName> visitedTypeVariables) {
    if (typeName instanceof ClassName) {
      return ((ClassName) typeName).canonicalName();
    } else if (typeName instanceof ArrayTypeName) {
      return String.format(
          "%s[]", toStableString(((ArrayTypeName) typeName).componentType, visitedTypeVariables));
    } else if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      return String.format(
          "%s<%s>",
          parameterizedTypeName.rawType,
          parameterizedTypeName.typeArguments.stream()
              .map(typeArg -> toStableString(typeArg, visitedTypeVariables))
              // We purposely don't use a space after the comma to for backwards compatibility with
              // usages that depended on the previous TypeMirror#toString() implementation.
              .collect(joining(",")));
    } else if (typeName instanceof WildcardTypeName) {
      WildcardTypeName wildcardTypeName = (WildcardTypeName) typeName;
      // Wildcard types have exactly 1 upper bound.
      TypeName upperBound = getOnlyElement(wildcardTypeName.upperBounds);
      if (!upperBound.equals(TypeName.OBJECT)) {
        // Wildcards with non-Object upper bounds can't have lower bounds.
        checkState(wildcardTypeName.lowerBounds.isEmpty());
        return String.format("? extends %s", toStableString(upperBound, visitedTypeVariables));
      }
      if (!wildcardTypeName.lowerBounds.isEmpty()) {
        // Wildcard types can have at most 1 lower bound.
        TypeName lowerBound = getOnlyElement(wildcardTypeName.lowerBounds);
        return String.format("? super %s", toStableString(lowerBound, visitedTypeVariables));
      }
      // If the upper bound is Object and there is no lower bound then just use "?".
      return "?";
    } else if (typeName instanceof TypeVariableName) {
      TypeVariableName typeVariableName = (TypeVariableName) typeName;
      if (typeVariableName.bounds.isEmpty() || !visitedTypeVariables.add(typeVariableName)) {
        return typeVariableName.name;
      } else {
        return String.format(
            // Type variables can only have "extends" bounds (no "super" bounds).
            "%s extends %s",
            typeVariableName.name,
            // Multiple bounds must be an intersection type (using "&").
            // A union type (using "|") is not allowed here.
            typeVariableName.bounds.stream()
                .map(bound -> toStableString(bound, visitedTypeVariables))
                .collect(joining(" & ")));
      }
    } else {
      // For all other types (e.g. primitive types) just use the TypeName's toString()
      return typeName.toString();
    }
  }

  private XTypes() {}
}
