/*
 * Copyright (C) 2015 The Dagger Authors.
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

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import dagger.model.Key;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a {@link Set} {@link TypeMirror}.
 */
@AutoValue
abstract class SetType {
  /**
   * The set type itself, wrapped using {@link MoreTypes#equivalence()}. Use
   * {@link #declaredSetType()} instead.
   */
  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType();
  
  /**
   * The set type itself.
   */
  DeclaredType declaredSetType() {
    return wrappedDeclaredSetType().get();
  }

  /**
   * {@code true} if the set type is the raw {@link Set} type.
   */
  boolean isRawType() {
    return declaredSetType().getTypeArguments().isEmpty();
  }

  /**
   * The element type.
   */
  TypeMirror elementType() {
    return declaredSetType().getTypeArguments().get(0);
  }

  /**
   * {@code true} if {@link #elementType()} is a {@code clazz}.
   */
  boolean elementsAreTypeOf(Class<?> clazz) {
    return MoreTypes.isType(elementType()) && MoreTypes.isTypeOf(clazz, elementType());
  }

  /**
   * {@code T} if {@link #elementType()} is a {@code WrappingClass<T>}.
   *
   * @throws IllegalStateException if {@link #elementType()} is not a {@code WrappingClass<T>}
   * @throws IllegalArgumentException if {@code wrappingClass} does not have exactly one type
   *     parameter
   */
  TypeMirror unwrappedElementType(Class<?> wrappingClass) {
    checkArgument(
        wrappingClass.getTypeParameters().length == 1,
        "%s must have exactly one type parameter",
        wrappingClass);
    checkArgument(
        elementsAreTypeOf(wrappingClass),
        "expected elements to be %s, but this type is %s",
        wrappingClass,
        declaredSetType());
    return MoreTypes.asDeclared(elementType()).getTypeArguments().get(0);
  }

  /**
   * {@code true} if {@code type} is a {@link Set} type.
   */
  static boolean isSet(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Set.class, type);
  }

  /**
   * {@code true} if {@code key.type()} is a {@link Set} type.
   */
  static boolean isSet(Key key) {
    return isSet(key.type());
  }

  /**
   * Returns a {@link SetType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link Set} type
   */
  static SetType from(TypeMirror type) {
    checkArgument(isSet(type), "%s must be a Set", type);
    return new AutoValue_SetType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }

  /**
   * Returns a {@link SetType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link Set} type
   */
  static SetType from(Key key) {
    return from (key.type());
  }
}
