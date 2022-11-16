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
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XTypes.asArray;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isNoType;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XArrayType;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XExecutableType;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreElements;
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
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

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
  @SuppressWarnings("ReturnMissingNullable")
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
        return toXProcessing(
                toJavac(processingEnv).getTypeUtils() // ALLOW_TYPES_ELEMENTS
                    .erasure(toJavac(type)),
                processingEnv)
            .getTypeName();
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
    XProcessingEnv processingEnv = getProcessingEnv(type);
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return isDeclared(type)
            && type.getTypeArguments().isEmpty()
            && !type.getTypeElement().getType().getTypeArguments().isEmpty();
      case KSP:
        return isDeclared(type)
            // TODO(b/245619245): Due to the bug in XProcessing, the logic used for Javac won't work
            // since XType#getTypeArguments() does not return an empty list for java raw types.
            // However, the type name seems to get it correct, so we compare the typename to the raw
            // typename until this bug is fixed.
            && type.getRawType() != null
            && type.getTypeName().equals(type.getRawType().getTypeName())
            && !type.getTypeElement().getType().getTypeArguments().isEmpty();
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
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

  public static boolean isExecutable(XType type) {
    return type instanceof XExecutableType;
  }

  public static boolean isMethod(XExecutableType type) {
    return type instanceof XMethodType;
  }

  public static boolean isConstructor(XExecutableType type) {
    return type instanceof XConstructorType;
  }

  public static boolean isFloat(XType type) {
    return type.getTypeName().equals(TypeName.FLOAT)
        || type.getTypeName().equals(KnownTypeNames.BOXED_FLOAT);
  }

  public static boolean isShort(XType type) {
    return type.getTypeName().equals(TypeName.SHORT)
        || type.getTypeName().equals(KnownTypeNames.BOXED_SHORT);
  }

  public static boolean isChar(XType type) {
    return type.getTypeName().equals(TypeName.CHAR)
        || type.getTypeName().equals(KnownTypeNames.BOXED_CHAR);
  }

  public static boolean isDouble(XType type) {
    return type.getTypeName().equals(TypeName.DOUBLE)
        || type.getTypeName().equals(KnownTypeNames.BOXED_DOUBLE);
  }

  public static boolean isBoolean(XType type) {
    return type.getTypeName().equals(TypeName.BOOLEAN)
        || type.getTypeName().equals(KnownTypeNames.BOXED_BOOLEAN);
  }

  private static class KnownTypeNames {
    static final TypeName BOXED_SHORT = TypeName.SHORT.box();
    static final TypeName BOXED_DOUBLE = TypeName.DOUBLE.box();
    static final TypeName BOXED_FLOAT = TypeName.FLOAT.box();
    static final TypeName BOXED_CHAR = TypeName.CHAR.box();
    static final TypeName BOXED_BOOLEAN = TypeName.BOOLEAN.box();
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
   * Returns {@code type}'s single type argument wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<Number>}.
   *
   * <p>If {@code type} has no type parameters, returns a {@link XType} for {@code wrappingClass} as
   * a raw type.
   *
   * @throws IllegalArgumentException if {@code} has more than one type argument.
   */
  public static XType rewrapType(XType type, ClassName wrappingClassName) {
    XProcessingEnv processingEnv = getProcessingEnv(type);
    XTypeElement wrappingType = processingEnv.requireTypeElement(wrappingClassName.canonicalName());
    switch (type.getTypeArguments().size()) {
      case 0:
        return processingEnv.getDeclaredType(wrappingType);
      case 1:
        return processingEnv.getDeclaredType(wrappingType, getOnlyElement(type.getTypeArguments()));
      default:
        throw new IllegalArgumentException(type + " has more than 1 type argument");
    }
  }

  /**
   * Returns a string representation of {@link XType} that is independent of the backend
   * (javac/ksp).
   */
  // TODO(b/241141586): Replace this with TypeName.toString(). Technically, TypeName.toString()
  // should already be independent of the backend but we supply our own custom implementation to
  // remain backwards compatible with the previous implementation, which used TypeMirror#toString().
  public static String toStableString(XType type) {
    try {
      return toStableString(type.getTypeName());
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  private static String toStableString(TypeName typeName) {
    if (typeName instanceof ClassName) {
      return ((ClassName) typeName).canonicalName();
    } else if (typeName instanceof ArrayTypeName) {
      return String.format(
          "%s[]", toStableString(((ArrayTypeName) typeName).componentType));
    } else if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      return String.format(
          "%s<%s>",
          parameterizedTypeName.rawType,
          parameterizedTypeName.typeArguments.stream()
              .map(XTypes::toStableString)
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
        return String.format("? extends %s", toStableString(upperBound));
      }
      if (!wildcardTypeName.lowerBounds.isEmpty()) {
        // Wildcard types can have at most 1 lower bound.
        TypeName lowerBound = getOnlyElement(wildcardTypeName.lowerBounds);
        return String.format("? super %s", toStableString(lowerBound));
      }
      // If the upper bound is Object and there is no lower bound then just use "?".
      return "?";
    } else if (typeName instanceof TypeVariableName) {
      return ((TypeVariableName) typeName).name;
    } else {
      // For all other types (e.g. primitive types) just use the TypeName's toString()
      return typeName.toString();
    }
  }

  public static String getKindName(XType type) {
    if (isArray(type)) {
      return "ARRAY";
    } else if (isWildcard(type)) {
      return "WILDCARD";
    } else if (isTypeVariable(type)) {
      return "TYPEVAR";
    } else if (isVoid(type)) {
      return "VOID";
    } else if (isNullType(type)) {
      return "NULL";
    } else if (isNoType(type)) {
      return "NONE";
    } else if (isPrimitive(type)) {
      return LOWER_CAMEL.to(UPPER_UNDERSCORE, type.getTypeName().toString());
    } else if (type.isError()) {
      // TODO(b/249801446): For now, we must call XType.isError() after the other checks because
      // some types in KSP (e.g. Wildcard) are not disjoint from error types and may return true
      // until this bug is fixed.
      // Note: Most of these types are disjoint, so ordering doesn't matter. However, error type is
      // a subtype of declared type so make sure we check isError() before isDeclared() so that
      // error types are reported as ERROR rather than DECLARED.
      return "ERROR";
    } else if (isDeclared(type)) {
      return "DECLARED";
    } else {
      return "UNKNOWN";
    }
  }

  /**
   * Iterates through the various types referenced within the given {@code type} and resolves it
   * if needed.
   */
  public static void resolveIfNeeded(XType type) {
    if (getProcessingEnv(type).getBackend() == XProcessingEnv.Backend.JAVAC) {
      // TODO(b/242569252): Due to a bug in javac, a TypeName may incorrectly contain a "$" instead
      // of "." if the TypeName is requested before the type has been resolved. Thus, we try to
      // resolve the type by calling Element#getKind() to force the correct TypeName.
      toJavac(type).accept(TypeResolutionVisitor.INSTANCE, new HashSet<>());
    }
  }

  private static final class TypeResolutionVisitor extends SimpleTypeVisitor8<Void, Set<Element>> {
    static final TypeResolutionVisitor INSTANCE = new TypeResolutionVisitor();

    @Override
    public Void visitDeclared(DeclaredType t, Set<Element> visited) {
      if (!visited.add(t.asElement())) {
        return null;
      }
      if (MoreElements.asType(t.asElement()).getQualifiedName().toString().contains("$")) {
        // Force symbol completion/resolution on the type by calling Element#getKind().
        t.asElement().getKind();
      }
      t.getTypeArguments().forEach(arg -> arg.accept(this, visited));
      return null;
    }

    @Override
    public Void visitError(ErrorType t, Set<Element> visited) {
      visitDeclared(t, visited);
      return null;
    }

    @Override
    public Void visitArray(ArrayType t, Set<Element> visited) {
      t.getComponentType().accept(this, visited);
      return null;
    }

    @Override
    public Void visitWildcard(WildcardType t, Set<Element> visited) {
      if (t.getExtendsBound() != null) {
        t.getExtendsBound().accept(this, visited);
      }
      if (t.getSuperBound() != null) {
        t.getSuperBound().accept(this, visited);
      }
      return null;
    }

    @Override
    protected Void defaultAction(TypeMirror e, Set<Element> visited) {
      return null;
    }
  }

  private XTypes() {}
}
