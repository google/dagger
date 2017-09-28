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

package dagger.internal.codegen;

import com.squareup.javapoet.CodeBlock;
import javax.lang.model.type.TypeMirror;

/**
 * Encapsulates a {@link CodeBlock} for an <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html">expression</a> and the
 * {@link TypeMirror} that it represents from the perspective of the compiler. Consider the
 * following example:
 *
 * <pre><code>
 *   {@literal @SuppressWarnings("rawtypes")}
 *   private Provider fooImplProvider = DoubleCheck.provider(FooImpl_Factory.create());
 * </code></pre>
 *
 * <p>An {@code Expression} for {@code fooImplProvider.get()} would have a {@link #type()} of {@code
 * java.lang.Object} and not {@code FooImpl}.
 */
final class Expression {
  private final TypeMirror type;
  private final CodeBlock codeBlock;

  private Expression(TypeMirror type, CodeBlock codeBlock) {
    this.type = type;
    this.codeBlock = codeBlock;
  }

  /** Creates a new {@link Expression} with a {@link TypeMirror} and {@link CodeBlock}. */
  static Expression create(TypeMirror type, CodeBlock expression) {
    return new Expression(type, expression);
  }

  /** Returns a new expression that casts the current expression to {@code newType}. */
  // TODO(ronshapiro): consider overloads that take a Types and Elements and only cast if necessary,
  // or just embedding a Types/Elements instance in an Expression.
  Expression castTo(TypeMirror newType) {
    return Expression.create(newType, CodeBlock.of("($T) $L", newType, codeBlock));
  }

  /** The {@link TypeMirror type} to which the expression evaluates. */
  TypeMirror type() {
    return type;
  }

  /** The code of the expression. */
  CodeBlock codeBlock() {
    return codeBlock;
  }

  @Override
  public String toString() {
    return String.format("[%s] %s", type, codeBlock);
  }
}
