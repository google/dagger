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
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Utilities for working with {@link TypeMirror} objects. Each is a candidate to move to {@link
 * MoreTypes}.
 */
// TODO(dpb): Change this to an object that contains a Types.
final class DaggerTypes {
  /**
   * Returns the non-{@link Object} superclass of the type with the proper type parameters. An empty
   * {@link Optional} is returned if there is no non-{@link Object} superclass.
   */
  static Optional<DeclaredType> nonObjectSuperclass(
      Types types, Elements elements, DeclaredType type) {
    return Optional.ofNullable(MoreTypes.nonObjectSuperclass(types, elements, type).orNull());
  }

  /**
   * Returns {@code type}'s single type argument, if one exists, or {@link Object} if not.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has more than one
   *     type argument.
   */
  static TypeMirror unwrapTypeOrObject(TypeMirror type, Elements elements) {
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    checkArgument(
        !typeElement.getTypeParameters().isEmpty(),
        "%s does not have a type parameter",
        typeElement.getQualifiedName());
    return getOnlyElement(
        declaredType.getTypeArguments(),
        elements.getTypeElement(Object.class.getCanonicalName()).asType());
  }

  /**
   * Returns {@code type} wrapped in {@code wrappingClass}.
   *
   * <p>For example, if {@code type} is {@code List<Number>} and {@code wrappingClass} is {@code
   * Set.class}, this will return {@code Set<List<Number>>}.
   */
  static TypeMirror wrapType(
      TypeMirror type, Class<?> wrappingClass, Types types, Elements elements) {
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
  static DeclaredType rewrapType(
      TypeMirror type, Class<?> wrappingClass, Types types, Elements elements) {
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
}
