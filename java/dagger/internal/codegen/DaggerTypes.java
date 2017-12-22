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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Extension of {@link Types} that adds Dagger-specific methods. */
final class DaggerTypes implements Types {

  private final Types types;
  private final Elements elements;

  DaggerTypes(Types types, Elements elements) {
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
  }

  DaggerTypes(ProcessingEnvironment processingEnv) {
    this(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
  }

  /**
   * Returns the non-{@link Object} superclass of the type with the proper type parameters. An empty
   * {@link Optional} is returned if there is no non-{@link Object} superclass.
   */
  Optional<DeclaredType> nonObjectSuperclass(DeclaredType type) {
    return Optional.ofNullable(MoreTypes.nonObjectSuperclass(types, elements, type).orNull());
  }

  /**
   * Returns {@code type}'s single type argument.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has zero or more
   *     than one type arguments.
   */
  TypeMirror unwrapType(TypeMirror type) {
    TypeMirror unwrapped = unwrapTypeOrDefault(type, null);
    checkArgument(unwrapped != null, "%s is a raw type", type);
    return unwrapped;
  }

  /**
   * Returns {@code type}'s single type argument, if one exists, or {@link Object} if not.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has more than one
   *     type argument.
   */
  TypeMirror unwrapTypeOrObject(TypeMirror type) {
    return unwrapTypeOrDefault(
        type, elements.getTypeElement(Object.class.getCanonicalName()).asType());
  }

  private TypeMirror unwrapTypeOrDefault(TypeMirror type, TypeMirror defaultType) {
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    checkArgument(
        !typeElement.getTypeParameters().isEmpty(),
        "%s does not have a type parameter",
        typeElement.getQualifiedName());
    return getOnlyElement(declaredType.getTypeArguments(), defaultType);
  }

  /**
   * Returns {@code type} wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<List<Number>>}.
   */
  TypeMirror wrapType(TypeMirror type, Class<?> wrappingClass) {
    return types.getDeclaredType(elements.getTypeElement(wrappingClass.getCanonicalName()), type);
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
  DeclaredType rewrapType(TypeMirror type, Class<?> wrappingClass) {
    List<? extends TypeMirror> typeArguments = MoreTypes.asDeclared(type).getTypeArguments();
    TypeElement wrappingType = elements.getTypeElement(wrappingClass.getCanonicalName());
    switch (typeArguments.size()) {
      case 0:
        return types.getDeclaredType(wrappingType);
      case 1:
        return types.getDeclaredType(wrappingType, getOnlyElement(typeArguments));
      default:
        throw new IllegalArgumentException(type + " has more than 1 type argument");
    }
  }

  /**
   * Throws {@link TypeNotPresentException} if {@code type} is an {@link
   * javax.lang.model.type.ErrorType}.
   */
  static void checkTypePresent(TypeMirror type) {
    if (type.getKind().equals(TypeKind.ERROR)) {
      throw new TypeNotPresentException(type.toString(), null);
    }
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
