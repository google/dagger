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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.valuesOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.providerTypeNames;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XTypeNames;

/**
 * Information about an {@code Optional} type.
 *
 * <p>{@link com.google.common.base.Optional} and {@link java.util.Optional} are supported.
 */
public final class OptionalType {
  /** A variant of {@code Optional}. */
  public enum OptionalKind {
    /** {@link com.google.common.base.Optional}. */
    GUAVA_OPTIONAL(XTypeNames.GUAVA_OPTIONAL, "absent"),

    /** {@link java.util.Optional}. */
    JDK_OPTIONAL(XTypeNames.JDK_OPTIONAL, "empty");

    // Keep a cache from class name to OptionalKind for quick look-up.
    private static final ImmutableMap<XClassName, OptionalKind> OPTIONAL_KIND_BY_CLASS_NAME =
        valuesOf(OptionalKind.class)
            .collect(toImmutableMap(value -> value.className, value -> value));

    @SuppressWarnings("ImmutableEnumChecker")
    private final XClassName className; // XClassName is immutable

    private final String absentMethodName;

    OptionalKind(XClassName className, String absentMethodName) {
      this.className = className;
      this.absentMethodName = absentMethodName;
    }

    private static boolean isOptionalKind(XTypeElement type) {
      return OPTIONAL_KIND_BY_CLASS_NAME.containsKey(type.asClassName());
    }

    private static OptionalKind of(XTypeElement type) {
      return OPTIONAL_KIND_BY_CLASS_NAME.get(type.asClassName());
    }

    /** Returns the {@link XClassName} of this optional kind. */
    public XClassName className() {
      return className;
    }

    /** Returns {@code valueType} wrapped in the correct class. */
    public XTypeName of(XTypeName valueType) {
      return className.parametrizedBy(valueType);
    }

    /** Returns an expression for the absent/empty value. */
    public XCodeBlock absentValueExpression() {
      return XCodeBlock.of("%T.%N()", className, absentMethodName);
    }

    /**
     * Returns an expression for the absent/empty value, parameterized with {@link #valueType()}.
     */
    public XCodeBlock parameterizedAbsentValueExpression(OptionalType optionalType) {
      return XCodeBlock.of(
          "%T.<%T>%N()", className, optionalType.valueType().asTypeName(), absentMethodName);
    }

    /** Returns an expression for the present {@code value}. */
    public XCodeBlock presentExpression(XCodeBlock value) {
      return XCodeBlock.of("%T.of(%L)", className, value);
    }

    /**
     * Returns an expression for the present {@code value}, returning {@code Optional<Object>} no
     * matter what type the value is.
     */
    public XCodeBlock presentObjectExpression(XCodeBlock value) {
      return XCodeBlock.of("%T.<%T>of(%L)", className, XTypeName.ANY_OBJECT, value);
    }
  }

  /**
   * Returns a {@link OptionalType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not an {@code Optional} type
   */
  public static OptionalType from(Key key) {
    return from(key.type().xprocessing());
  }

  /**
   * Returns a {@link OptionalType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not an {@code Optional} type
   */
  public static OptionalType from(XType type) {
    checkArgument(isOptional(type), "%s must be an Optional", type);
    return new OptionalType(type);
  }

  private final XType type;

  private OptionalType(XType type) {
    this.type = type;
  }

  /** The optional type itself. */
  XTypeName typeName() {
    return type.asTypeName();
  }

  /** Which {@code Optional} type is used. */
  public OptionalKind kind() {
    return OptionalKind.of(type.getTypeElement());
  }

  /** The value type. */
  public XType valueType() {
    return type.getTypeArguments().get(0);
  }

  /** Returns {@code true} if {@code type} is an {@code Optional} type. */
  private static boolean isOptional(XType type) {
    return isDeclared(type) && OptionalKind.isOptionalKind(type.getTypeElement());
  }

  /** Returns {@code true} if {@code key.type()} is an {@code Optional} type. */
  public static boolean isOptional(Key key) {
    return isOptional(key.type().xprocessing());
  }

  public static boolean isOptionalProviderType(XType type) {
    return OptionalType.isOptional(type)
        && isTypeOf(OptionalType.from(type).valueType(), providerTypeNames());
  }
}
