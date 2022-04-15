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

package dagger.internal.codegen.javapoet;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static java.util.stream.StreamSupport.stream;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import java.util.stream.Collector;

/** Convenience methods for creating {@link CodeBlock}s. */
public final class CodeBlocks {
  /**
   * Joins {@link CodeBlock} instances in a manner suitable for use as method parameters (or
   * arguments).
   */
  public static Collector<CodeBlock, ?, CodeBlock> toParametersCodeBlock() {
    // TODO(ronshapiro,jakew): consider adding zero-width spaces to help line breaking when the
    // formatter is off. If not, inline this
    return CodeBlock.joining(", ");
  }

  /** Concatenates {@link CodeBlock} instances separated by newlines for readability. */
  public static Collector<CodeBlock, ?, CodeBlock> toConcatenatedCodeBlock() {
    return CodeBlock.joining("\n", "", "\n");
  }

  /** Returns a comma-separated version of {@code codeBlocks} as one unified {@link CodeBlock}. */
  public static CodeBlock makeParametersCodeBlock(Iterable<CodeBlock> codeBlocks) {
    return stream(codeBlocks.spliterator(), false).collect(toParametersCodeBlock());
  }

  /**
   * Returns a comma-separated {@link CodeBlock} using the name of every parameter in {@code
   * parameters}.
   */
  public static CodeBlock parameterNames(Iterable<ParameterSpec> parameters) {
    // TODO(ronshapiro): Add DaggerStreams.stream(Iterable)
    return stream(parameters.spliterator(), false)
        .map(p -> CodeBlock.of("$N", p))
        .collect(toParametersCodeBlock());
  }

  /**
   * Returns one unified {@link CodeBlock} which joins each item in {@code codeBlocks} with a
   * newline.
   */
  public static CodeBlock concat(Iterable<CodeBlock> codeBlocks) {
    return stream(codeBlocks.spliterator(), false).collect(toConcatenatedCodeBlock());
  }

  /**
   * Returns an anonymous {@link javax.inject.Provider} class with the single {@link
   * javax.inject.Provider#get()} method that returns the given {@code expression}.
   */
  public static CodeBlock anonymousProvider(Expression expression) {
    return anonymousProvider(
        expression.type().getTypeName(), CodeBlock.of("return $L;", expression.codeBlock()));
  }

  /**
   * Returns an anonymous {@link javax.inject.Provider} class with the single {@link
   * javax.inject.Provider#get()} method implemented by {@code body}.
   */
  public static CodeBlock anonymousProvider(TypeName providedType, CodeBlock body) {
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .superclass(providerOf(providedType))
            .addMethod(
                methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(providedType)
                    .addCode(body)
                    .build())
            .build());
  }

  /** Returns {@code expression} cast to a type. */
  public static CodeBlock cast(CodeBlock expression, ClassName castTo) {
    return CodeBlock.of("($T) $L", castTo, expression);
  }

  /** Returns {@code expression} cast to a type. */
  public static CodeBlock cast(CodeBlock expression, Class<?> castTo) {
    return CodeBlock.of("($T) $L", castTo, expression);
  }

  public static CodeBlock type(XType type) {
    return CodeBlock.of("$T", type.getTypeName());
  }

  public static CodeBlock stringLiteral(String toWrap) {
    return CodeBlock.of("$S", toWrap);
  }

  private CodeBlocks() {}
}
