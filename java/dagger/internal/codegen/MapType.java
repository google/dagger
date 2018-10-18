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
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import dagger.model.Key;
import java.util.Map;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Information about a {@link Map} {@link TypeMirror}.
 */
@AutoValue
abstract class MapType {
  /**
   * The map type itself, wrapped using {@link MoreTypes#equivalence()}. Use
   * {@link #declaredMapType()} instead.
   */
  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredMapType();

  /**
   * The map type itself.
   */
  DeclaredType declaredMapType() {
    return wrappedDeclaredMapType().get();
  }

  /**
   * {@code true} if the map type is the raw {@link Map} type.
   */
  boolean isRawType() {
    return declaredMapType().getTypeArguments().isEmpty();
  }

  /**
   * The map key type.
   * 
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  TypeMirror keyType() {
    checkState(!isRawType());
    return declaredMapType().getTypeArguments().get(0);
  }

  /**
   * The map value type.
   * 
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  TypeMirror valueType() {
    checkState(!isRawType());
    return declaredMapType().getTypeArguments().get(1);
  }

  /**
   * {@code true} if {@link #valueType()} is a {@code clazz}.
   * 
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  boolean valuesAreTypeOf(Class<?> clazz) {
    return MoreTypes.isType(valueType()) && MoreTypes.isTypeOf(clazz, valueType());
  }

  /**
   * Returns {@code true} if the {@linkplain #valueType() value type} of the {@link Map} is a
   * {@linkplain FrameworkTypes#isFrameworkType(TypeMirror) framework type}.
   */
  boolean valuesAreFrameworkType() {
    return FrameworkTypes.isFrameworkType(valueType());
  }

  /**
   * {@code V} if {@link #valueType()} is a framework type like {@code Provider<V>} or {@code
   * Producer<V>}.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true or {@link #valueType()} is not a
   *     framework type
   */
  TypeMirror unwrappedFrameworkValueType() {
    checkState(
        valuesAreFrameworkType(), "called unwrappedFrameworkValueType() on %s", declaredMapType());
    return uncheckedUnwrappedValueType();
  }

  /**
   * {@code V} if {@link #valueType()} is a {@code WrappingClass<V>}.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true or {@link #valueType()} is not a
   *     {@code WrappingClass<V>}
   * @throws IllegalArgumentException if {@code wrappingClass} does not have exactly one type
   *     parameter
   */
  TypeMirror unwrappedValueType(Class<?> wrappingClass) {
    checkArgument(
        wrappingClass.getTypeParameters().length == 1,
        "%s must have exactly one type parameter",
        wrappingClass);
    checkState(valuesAreTypeOf(wrappingClass), "expected values to be %s: %s", wrappingClass, this);
    return uncheckedUnwrappedValueType();
  }

  private TypeMirror uncheckedUnwrappedValueType() {
    return MoreTypes.asDeclared(valueType()).getTypeArguments().get(0);
  }

  /**
   * {@code true} if {@code type} is a {@link Map} type.
   */
  static boolean isMap(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Map.class, type);
  }

  /**
   * {@code true} if {@code key.type()} is a {@link Map} type.
   */
  static boolean isMap(Key key) {
    return isMap(key.type());
  }

  /**
   * Returns a {@link MapType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link Map} type
   */
  static MapType from(TypeMirror type) {
    checkArgument(isMap(type), "%s is not a Map", type);
    return new AutoValue_MapType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }

  /**
   * Returns a {@link MapType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link Map} type
   */
  static MapType from(Key key) {
    return from(key.type());
  }
}
