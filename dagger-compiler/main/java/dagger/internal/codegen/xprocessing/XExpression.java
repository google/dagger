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

package dagger.internal.codegen.xprocessing;

import static dagger.internal.codegen.xprocessing.XCodeBlocks.toXPoet;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.processing.XRawType;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.CodeBlock;

/**
 * Encapsulates a {@link XCodeBlock} for an <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html">expression</a> and the
 * {@link XType} that it represents from the perspective of the compiler. Consider the following
 * example:
 *
 * <pre><code>
 *   {@literal @SuppressWarnings("rawtypes")}
 *   private Provider fooImplProvider = DoubleCheck.provider(FooImpl_Factory.create());
 * </code></pre>
 *
 * <p>An {@code XExpression} for {@code fooImplProvider.get()} would have a {@link #type()} of
 * {@code java.lang.Object} and not {@code FooImpl}.
 */
public final class XExpression {
  private final XExpressionType type;
  private final XCodeBlock codeBlock;

  private XExpression(XExpressionType type, XCodeBlock codeBlock) {
    this.type = type;
    this.codeBlock = codeBlock;
  }

  /** Creates a new {@link XExpression} with a {@link XType} and {@link XCodeBlock}. */
  public static XExpression create(XType type, CodeBlock expression) {
    return create(type, toXPoet(expression));
  }

  /** Creates a new {@link XExpression} with a {@link XType} and {@link XCodeBlock}. */
  public static XExpression create(XType type, XCodeBlock expression) {
    return new XExpression(XExpressionType.create(type), expression);
  }

  /** Creates a new {@link XExpression} with a {@link XExpressionType} and {@link XCodeBlock}. */
  public static XExpression create(XExpressionType type, CodeBlock expression) {
    return create(type, toXPoet(expression));
  }

  /** Creates a new {@link XExpression} with a {@link XExpressionType} and {@link XCodeBlock}. */
  public static XExpression create(XExpressionType type, XCodeBlock expression) {
    return new XExpression(type, expression);
  }

  /** Returns a new expression that casts the current expression to {@code newType}. */
  public XExpression castTo(XType newType) {
    return create(newType, XCodeBlock.ofCast(newType.asTypeName(), codeBlock));
  }

  /** Returns a new expression that casts the current expression to {@code newType}. */
  public XExpression castTo(XRawType newRawType) {
    return create(
        XExpressionType.create(newRawType, type.getProcessingEnv()),
        XCodeBlock.ofCast(newRawType.asTypeName(), codeBlock));
  }

  /**
   * Returns a new expression that {@link #castTo(XType)} casts the current expression to its boxed
   * type if this expression has a primitive type.
   */
  public XExpression box() {
    return type.asType().isPresent() && isPrimitive(type.asType().get())
        ? castTo(type.asType().get().boxed())
        : this;
  }

  /** The {@link XType type} to which the expression evaluates. */
  public XExpressionType type() {
    return type;
  }

  /** The code of the expression. */
  public XCodeBlock codeBlock() {
    return codeBlock;
  }

  @Override
  public String toString() {
    return String.format("[%s] %s", type.getTypeName(), codeBlock);
  }
}
