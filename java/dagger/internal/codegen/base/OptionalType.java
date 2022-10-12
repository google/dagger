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
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Key;

/**
 * Information about an {@code Optional} type.
 *
 * <p>{@link com.google.common.base.Optional} and {@link java.util.Optional} are supported.
 */
@AutoValue
public abstract class OptionalType {
  private XType type;

  /** A variant of {@code Optional}. */
  public enum OptionalKind {
    /** {@link com.google.common.base.Optional}. */
    GUAVA_OPTIONAL(TypeNames.GUAVA_OPTIONAL, "absent"),

    /** {@link java.util.Optional}. */
    JDK_OPTIONAL(TypeNames.JDK_OPTIONAL, "empty");

    // Keep a cache from class name to OptionalKind for quick look-up.
    private static final ImmutableMap<ClassName, OptionalKind> OPTIONAL_KIND_BY_CLASS_NAME =
        valuesOf(OptionalKind.class)
            .collect(toImmutableMap(value -> value.className, value -> value));

    private final ClassName className;
    private final String absentMethodName;

    OptionalKind(ClassName className, String absentMethodName) {
      this.className = className;
      this.absentMethodName = absentMethodName;
    }

    private static boolean isOptionalKind(XTypeElement type) {
      return OPTIONAL_KIND_BY_CLASS_NAME.containsKey(type.getClassName());
    }

    private static OptionalKind of(XTypeElement type) {
      return OPTIONAL_KIND_BY_CLASS_NAME.get(type.getClassName());
    }

    /** Returns the {@link ClassName} of this optional kind. */
    public ClassName className() {
      return className;
    }

    /** Returns {@code valueType} wrapped in the correct class. */
    public ParameterizedTypeName of(TypeName valueType) {
      return ParameterizedTypeName.get(className, valueType);
    }

    /** Returns an expression for the absent/empty value. */
    public CodeBlock absentValueExpression() {
      return CodeBlock.of("$T.$L()", className, absentMethodName);
    }

    /**
     * Returns an expression for the absent/empty value, parameterized with {@link #valueType()}.
     */
    public CodeBlock parameterizedAbsentValueExpression(OptionalType optionalType) {
      return CodeBlock.of(
          "$T.<$T>$L()", className, optionalType.valueType().getTypeName(), absentMethodName);
    }

    /** Returns an expression for the present {@code value}. */
    public CodeBlock presentExpression(CodeBlock value) {
      return CodeBlock.of("$T.of($L)", className, value);
    }

    /**
     * Returns an expression for the present {@code value}, returning {@code Optional<Object>} no
     * matter what type the value is.
     */
    public CodeBlock presentObjectExpression(CodeBlock value) {
      return CodeBlock.of("$T.<$T>of($L)", className, TypeName.OBJECT, value);
    }
  }

  /** The optional type itself. */
  abstract TypeName typeName();

  /** The optional type itself. */
  private XType type() {
    return type;
  }

  /** Which {@code Optional} type is used. */
  public OptionalKind kind() {
    return OptionalKind.of(type().getTypeElement());
  }

  /** The value type. */
  public XType valueType() {
    return type().getTypeArguments().get(0);
  }

  /** Returns {@code true} if {@code type} is an {@code Optional} type. */
  private static boolean isOptional(XType type) {
    return isDeclared(type) && OptionalKind.isOptionalKind(type.getTypeElement());
  }

  /** Returns {@code true} if {@code key.type()} is an {@code Optional} type. */
  public static boolean isOptional(Key key) {
    return isOptional(key.type().xprocessing());
  }

  /**
   * Returns a {@link OptionalType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not an {@code Optional} type
   */
  public static OptionalType from(XType type) {
    checkArgument(isOptional(type), "%s must be an Optional", type);
    OptionalType optionalType = new AutoValue_OptionalType(type.getTypeName());
    optionalType.type = type;
    return optionalType;
  }

  /**
   * Returns a {@link OptionalType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not an {@code Optional} type
   */
  public static OptionalType from(Key key) {
    return from(key.type().xprocessing());
  }
}
