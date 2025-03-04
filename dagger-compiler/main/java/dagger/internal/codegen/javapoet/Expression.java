/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.javapoet;

import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import androidx.room.compiler.processing.XRawType;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.CodeBlock;

/**
 * Encapsulates a {@link CodeBlock} for an <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html">expression</a> and the
 * {@link XType} that it represents from the perspective of the compiler. Consider the following
 * example:
 *
 * <pre><code>
 *   {@literal @SuppressWarnings("rawtypes")}
 *   private Provider fooImplProvider = DoubleCheck.provider(FooImpl_Factory.create());
 * </code></pre>
 *
 * <p>An {@code Expression} for {@code fooImplProvider.get()} would have a {@link #type()} of {@code
 * java.lang.Object} and not {@code FooImpl}.
 */
public final class Expression {
  private final ExpressionType type;
  private final CodeBlock codeBlock;

  private Expression(ExpressionType type, CodeBlock codeBlock) {
    this.type = type;
    this.codeBlock = codeBlock;
  }

  /** Creates a new {@link Expression} with a {@link XType} and {@link CodeBlock}. */
  public static Expression create(XType type, CodeBlock expression) {
    return new Expression(ExpressionType.create(type), expression);
  }

  /** Creates a new {@link Expression} with a {@link ExpressionType} and {@link CodeBlock}. */
  public static Expression create(ExpressionType type, CodeBlock expression) {
    return new Expression(type, expression);
  }

  /**
   * Creates a new {@link Expression} with a {@link XType}, {@linkplain CodeBlock#of(String,
   * Object[]) format, and arguments}.
   */
  public static Expression create(XType type, String format, Object... args) {
    return create(type, CodeBlock.of(format, args));
  }

  /** Returns a new expression that casts the current expression to {@code newType}. */
  public Expression castTo(XType newType) {
    return create(newType, CodeBlock.of("($T) $L", newType.getTypeName(), codeBlock));
  }

  /** Returns a new expression that casts the current expression to {@code newType}. */
  public Expression castTo(XRawType newRawType) {
    return create(
        ExpressionType.create(newRawType, type.getProcessingEnv()),
        CodeBlock.of("($T) $L", newRawType.getTypeName(), codeBlock));
  }

  /**
   * Returns a new expression that {@link #castTo(XType)} casts the current expression to its boxed
   * type if this expression has a primitive type.
   */
  public Expression box() {
    return type.asType().isPresent() && isPrimitive(type.asType().get())
        ? castTo(type.asType().get().boxed())
        : this;
  }

  /** The {@link XType type} to which the expression evaluates. */
  public ExpressionType type() {
    return type;
  }

  /** The code of the expression. */
  public CodeBlock codeBlock() {
    return codeBlock;
  }

  @Override
  public String toString() {
    return String.format("[%s] %s", type.getTypeName(), codeBlock);
  }
}
