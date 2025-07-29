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
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;

/** Information about a {@link java.util.Set} type. */
public final class SetType {
  /**
   * Returns a {@link SetType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link java.util.Set} type
   */
  public static SetType from(Key key) {
    return from(key.type().xprocessing());
  }

  /**
   * Returns a {@link SetType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link java.util.Set} type
   */
  public static SetType from(XType type) {
    checkArgument(isSet(type), "%s must be a Set", type);
    return new SetType(type);
  }

  private final XType type;

  private SetType(XType type) {
    this.type = type;
  }

  /** The set type itself. */
  XTypeName typeName() {
    return type.asTypeName();
  }

  /** {@code true} if the set type is the raw {@link java.util.Set} type. */
  public boolean isRawType() {
    return XTypes.isRawParameterizedType(type);
  }

  /** Returns the element type. */
  public XType elementType() {
    return unwrapType(type);
  }

  /** Returns {@code true} if {@link #elementType()} is of type {@code className}. */
  public boolean elementsAreTypeOf(XClassName className) {
    return !isRawType() && isTypeOf(elementType(), className);
  }

  /**
   * {@code T} if {@link #elementType()} is a {@code WrappingClass<T>}.
   *
   * @throws IllegalStateException if {@link #elementType()} is not a {@code WrappingClass<T>}
   */
  // TODO(b/202033221): Consider using stricter input type, e.g. FrameworkType.
  public XType unwrappedElementType(XClassName wrappingClass) {
    checkArgument(
        elementsAreTypeOf(wrappingClass),
        "expected elements to be %s, but this type is %s",
        wrappingClass,
        type);
    return unwrapType(elementType());
  }

  /** {@code true} if {@code type} is a {@link java.util.Set} type. */
  public static boolean isSet(XType type) {
    // In general, Dagger ignores mutability so check for both kotlin.collection.(Set|MutableSet).
    return XTypes.isTypeOf(type, XTypeName.SET)
        || XTypes.isTypeOf(type, XTypeName.MUTABLE_SET)
        // This is for cases where java.util.Set is used directly in Kotlin sources.
        || XTypes.isTypeOf(type, XTypeNames.JAVA_UTIL_SET);
  }

  /** {@code true} if {@code key.type()} is a {@link java.util.Set} type. */
  public static boolean isSet(Key key) {
    return isSet(key.type().xprocessing());
  }
}
