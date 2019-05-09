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
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static java.util.stream.StreamSupport.stream;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import java.util.stream.Collector;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

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

  /** Adds an annotation to a method. */
  public static void addAnnotation(MethodSpec.Builder method, DeclaredType nullableType) {
    method.addAnnotation(ClassName.get(MoreTypes.asTypeElement(nullableType)));
  }

  /**
   * Returns an anonymous {@link javax.inject.Provider} class with the single {@link
   * javax.inject.Provider#get()} method that returns the given {@code expression}.
   */
  public static CodeBlock anonymousProvider(Expression expression) {
    // More of a precondition check that the type Provider is parameterized with is a DeclaredType
    DeclaredType type = MoreTypes.asDeclared(expression.type());
    return anonymousProvider(
        TypeName.get(type), CodeBlock.of("return $L;", expression.codeBlock()));
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
  public static CodeBlock cast(CodeBlock expression, Class<?> castTo) {
    return CodeBlock.of("($T) $L", castTo, expression);
  }

  public static CodeBlock type(TypeMirror type) {
    return CodeBlock.of("$T", type);
  }

  public static CodeBlock stringLiteral(String toWrap) {
    return CodeBlock.of("$S", toWrap);
  }

  /** Returns a javadoc {@literal @link} tag that poins to the given {@link ExecutableElement}. */
  public static CodeBlock javadocLinkTo(ExecutableElement executableElement) {
    CodeBlock.Builder builder =
        CodeBlock.builder()
            .add(
                "{@link $T#",
                rawTypeName(
                    ClassName.get(MoreElements.asType(executableElement.getEnclosingElement()))));
    switch (executableElement.getKind()) {
      case METHOD:
        builder.add("$L", executableElement.getSimpleName());
        break;
      case CONSTRUCTOR:
        builder.add("$L", executableElement.getEnclosingElement().getSimpleName());
        break;
      case STATIC_INIT:
      case INSTANCE_INIT:
        throw new IllegalArgumentException(
            "cannot create a javadoc link to an initializer: " + executableElement);
      default:
        throw new AssertionError(executableElement.toString());
    }
    builder.add("(");
    builder.add(
        executableElement.getParameters().stream()
            .map(parameter -> CodeBlock.of("$T", rawTypeName(TypeName.get(parameter.asType()))))
            .collect(toParametersCodeBlock()));
    return builder.add(")}").build();
  }

  private CodeBlocks() {}
}
