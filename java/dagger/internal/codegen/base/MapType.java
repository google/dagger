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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Key;

/** Information about a {@link java.util.Map} type. */
@AutoValue
public abstract class MapType {
  private XType type;

  /** The map type itself. */
  abstract TypeName typeName();

  /** The map type itself. */
  private XType type() {
    return type;
  }

  /** {@code true} if the map type is the raw {@link java.util.Map} type. */
  public boolean isRawType() {
    return type().getTypeArguments().isEmpty();
  }

  /**
   * The map key type.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  public XType keyType() {
    checkState(!isRawType());
    return type().getTypeArguments().get(0);
  }

  /**
   * The map value type.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true.
   */
  public XType valueType() {
    checkState(!isRawType());
    return type().getTypeArguments().get(1);
  }

  /** Returns {@code true} if the raw type of {@link #valueType()} is {@code className}. */
  public boolean valuesAreTypeOf(ClassName className) {
    return !isRawType() && isTypeOf(valueType(), className);
  }

  /** Returns {@code true} if the raw type of {@link #valueType()} is a framework type. */
  public boolean valuesAreFrameworkType() {
    return FrameworkTypes.isFrameworkType(valueType());
  }

  /**
   * {@code V} if {@link #valueType()} is a framework type like {@code Provider<V>} or {@code
   * Producer<V>}.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true or {@link #valueType()} is not a
   *     framework type
   */
  public XType unwrappedFrameworkValueType() {
    checkState(valuesAreFrameworkType(), "called unwrappedFrameworkValueType() on %s", type());
    return uncheckedUnwrappedValueType();
  }

  /**
   * {@code V} if {@link #valueType()} is a {@code WrappingClass<V>}.
   *
   * @throws IllegalStateException if {@link #isRawType()} is true or {@link #valueType()} is not a
   *     {@code WrappingClass<V>}
   */
  // TODO(b/202033221): Consider using stricter input type, e.g. FrameworkType.
  public XType unwrappedValueType(ClassName wrappingClass) {
    checkState(valuesAreTypeOf(wrappingClass), "expected values to be %s: %s", wrappingClass, this);
    return uncheckedUnwrappedValueType();
  }

  private XType uncheckedUnwrappedValueType() {
    return unwrapType(valueType());
  }

  /** {@code true} if {@code type} is a {@link java.util.Map} type. */
  public static boolean isMap(XType type) {
    return isTypeOf(type, TypeNames.MAP);
  }

  /** {@code true} if {@code key.type()} is a {@link java.util.Map} type. */
  public static boolean isMap(Key key) {
    return isMap(key.type().xprocessing());
  }

  /**
   * Returns a {@link MapType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link java.util.Map} type
   */
  public static MapType from(XType type) {
    checkArgument(isMap(type), "%s is not a Map", type);
    MapType mapType = new AutoValue_MapType(type.getTypeName());
    mapType.type = type;
    return mapType;
  }

  /**
   * Returns a {@link MapType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link java.util.Map} type
   */
  public static MapType from(Key key) {
    return from(key.type().xprocessing());
  }
}
