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

package dagger.internal.codegen.langmodel;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;

import androidx.room.compiler.processing.XType;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Traverser;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/** Extension of {@link Types} that adds Dagger-specific methods. */
public final class DaggerTypes implements Types {
  private final Types types;
  private final DaggerElements elements;

  public DaggerTypes(Types types, DaggerElements elements) {
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
  }

  // Note: This is similar to auto-common's MoreTypes except using ClassName rather than Class.
  // TODO(bcorso): Contribute a String version to auto-common's MoreTypes?
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

  /**
   * Returns the non-{@link Object} superclass of the type with the proper type parameters. An empty
   * {@link Optional} is returned if there is no non-{@link Object} superclass.
   */
  public Optional<DeclaredType> nonObjectSuperclass(DeclaredType type) {
    return Optional.ofNullable(MoreTypes.nonObjectSuperclass(types, elements, type).orNull());
  }

  /**
   * Returns the {@linkplain #directSupertypes(TypeMirror) supertype}s of a type in breadth-first
   * order.
   */
  public Iterable<TypeMirror> supertypes(TypeMirror type) {
    return Traverser.<TypeMirror>forGraph(this::directSupertypes).breadthFirst(type);
  }

  /**
   * Returns {@code type}'s single type argument.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has zero or more
   *     than one type arguments.
   */
  public static TypeMirror unwrapType(TypeMirror type) {
    TypeMirror unwrapped = unwrapTypeOrDefault(type, null);
    checkArgument(unwrapped != null, "%s is a raw type", type);
    return unwrapped;
  }

  private static TypeMirror unwrapTypeOrDefault(TypeMirror type, TypeMirror defaultType) {
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    checkArgument(
        !typeElement.getTypeParameters().isEmpty(),
        "%s does not have a type parameter",
        typeElement.getQualifiedName());
    return getOnlyElement(declaredType.getTypeArguments(), defaultType);
  }

  /**
   * Returns {@code type}'s single type argument, if one exists, or {@link Object} if not.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has more than one
   *     type argument.
   */
  public TypeMirror unwrapTypeOrObject(TypeMirror type) {
    return unwrapTypeOrDefault(type, elements.getTypeElement(TypeName.OBJECT).asType());
  }

  /**
   * Returns {@code type} wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<List<Number>>}.
   */
  public DeclaredType wrapType(XType type, ClassName wrappingClassName) {
    return wrapType(toJavac(type), wrappingClassName);
  }

  /**
   * Returns {@code type} wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<List<Number>>}.
   */
  public DeclaredType wrapType(TypeMirror type, ClassName wrappingClassName) {
    return types.getDeclaredType(elements.getTypeElement(wrappingClassName.canonicalName()), type);
  }

  /**
   * Returns {@code type}'s single type argument wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<Number>}.
   *
   * <p>If {@code type} has no type parameters, returns a {@link TypeMirror} for {@code
   * wrappingClass} as a raw type.
   *
   * @throws IllegalArgumentException if {@code} has more than one type argument.
   */
  public DeclaredType rewrapType(TypeMirror type, ClassName wrappingClassName) {
    List<? extends TypeMirror> typeArguments = MoreTypes.asDeclared(type).getTypeArguments();
    TypeElement wrappingType = elements.getTypeElement(wrappingClassName.canonicalName());
    switch (typeArguments.size()) {
      case 0:
        return getDeclaredType(wrappingType);
      case 1:
        return getDeclaredType(wrappingType, getOnlyElement(typeArguments));
      default:
        throw new IllegalArgumentException(type + " has more than 1 type argument");
    }
  }

  /**
   * Returns an accessible type in {@code requestingClass}'s package based on {@code type}:
   *
   * <ul>
   *   <li>If {@code type} is accessible from the package, returns it.
   *   <li>If not, but {@code type}'s raw type is accessible from the package, returns the raw type.
   *   <li>Otherwise returns {@link Object}.
   * </ul>
   */
  public TypeMirror accessibleType(XType type, ClassName requestingClass) {
    return accessibleType(toJavac(type), requestingClass);
  }

  /**
   * Returns an accessible type in {@code requestingClass}'s package based on {@code type}:
   *
   * <ul>
   *   <li>If {@code type} is accessible from the package, returns it.
   *   <li>If not, but {@code type}'s raw type is accessible from the package, returns the raw type.
   *   <li>Otherwise returns {@link Object}.
   * </ul>
   */
  public TypeMirror accessibleType(TypeMirror type, ClassName requestingClass) {
    return accessibleType(
        type,
        t -> Accessibility.isTypeAccessibleFrom(t, requestingClass.packageName()),
        t -> Accessibility.isRawTypeAccessible(t, requestingClass.packageName()));
  }

  private TypeMirror accessibleType(
      TypeMirror type,
      Predicate<TypeMirror> accessibilityPredicate,
      Predicate<TypeMirror> rawTypeAccessibilityPredicate) {
    if (accessibilityPredicate.test(type)) {
      return type;
    } else if (type.getKind().equals(TypeKind.DECLARED)
        && rawTypeAccessibilityPredicate.test(type)) {
      return getDeclaredType(MoreTypes.asTypeElement(type));
    } else {
      return elements.getTypeElement(TypeName.OBJECT).asType();
    }
  }

  /**
   * Throws {@link TypeNotPresentException} if {@code type} is an {@link
   * javax.lang.model.type.ErrorType}.
   */
  public static void checkTypePresent(TypeMirror type) {
    type.accept(
        // TODO(ronshapiro): Extract a base class that visits all components of a complex type
        // and put it in auto.common
        new SimpleTypeVisitor8<Void, Void>() {
          @Override
          public Void visitArray(ArrayType arrayType, Void p) {
            return arrayType.getComponentType().accept(this, p);
          }

          @Override
          public Void visitDeclared(DeclaredType declaredType, Void p) {
            declaredType.getTypeArguments().forEach(t -> t.accept(this, p));
            return null;
          }

          @Override
          public Void visitError(ErrorType errorType, Void p) {
            throw new TypeNotPresentException(type.toString(), null);
          }
        },
        null);
  }

  private static final ImmutableSet<Class<?>> FUTURE_TYPES =
      ImmutableSet.of(ListenableFuture.class, FluentFuture.class);

  public static boolean isFutureType(XType type) {
    return isFutureType(toJavac(type));
  }

  public static boolean isFutureType(TypeMirror type) {
    return FUTURE_TYPES.stream().anyMatch(t -> MoreTypes.isTypeOf(t, type));
  }

  // Implementation of Types methods, delegating to types.

  @Override
  public Element asElement(TypeMirror t) {
    return types.asElement(t);
  }

  @Override
  public boolean isSameType(TypeMirror t1, TypeMirror t2) {
    return types.isSameType(t1, t2);
  }

  public boolean isSubtype(XType t1, XType t2) {
    return isSubtype(toJavac(t1), toJavac(t2));
  }

  @Override
  public boolean isSubtype(TypeMirror t1, TypeMirror t2) {
    return types.isSubtype(t1, t2);
  }

  @Override
  public boolean isAssignable(TypeMirror t1, TypeMirror t2) {
    return types.isAssignable(t1, t2);
  }

  @Override
  public boolean contains(TypeMirror t1, TypeMirror t2) {
    return types.contains(t1, t2);
  }

  @Override
  public boolean isSubsignature(ExecutableType m1, ExecutableType m2) {
    return types.isSubsignature(m1, m2);
  }

  @Override
  public List<? extends TypeMirror> directSupertypes(TypeMirror t) {
    return types.directSupertypes(t);
  }

  @Override
  public TypeMirror erasure(TypeMirror t) {
    return types.erasure(t);
  }

  @Override
  public TypeElement boxedClass(PrimitiveType p) {
    return types.boxedClass(p);
  }

  @Override
  public PrimitiveType unboxedType(TypeMirror t) {
    return types.unboxedType(t);
  }

  @Override
  public TypeMirror capture(TypeMirror t) {
    return types.capture(t);
  }

  @Override
  public PrimitiveType getPrimitiveType(TypeKind kind) {
    return types.getPrimitiveType(kind);
  }

  @Override
  public NullType getNullType() {
    return types.getNullType();
  }

  @Override
  public NoType getNoType(TypeKind kind) {
    return types.getNoType(kind);
  }

  @Override
  public ArrayType getArrayType(TypeMirror componentType) {
    return types.getArrayType(componentType);
  }

  @Override
  public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {
    return types.getWildcardType(extendsBound, superBound);
  }

  @Override
  public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
    return types.getDeclaredType(typeElem, typeArgs);
  }

  @Override
  public DeclaredType getDeclaredType(
      DeclaredType containing, TypeElement typeElem, TypeMirror... typeArgs) {
    return types.getDeclaredType(containing, typeElem, typeArgs);
  }

  @Override
  public TypeMirror asMemberOf(DeclaredType containing, Element element) {
    return types.asMemberOf(containing, element);
  }
}
