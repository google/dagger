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

package dagger.internal.codegen;

import static dagger.internal.codegen.TypeNames.rawTypeName;
import static java.util.stream.StreamSupport.stream;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import java.util.stream.Collector;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;

final class CodeBlocks {
  /**
   * A {@link Collector} implementation that joins {@link CodeBlock} instances together into one
   * separated by {@code delimiter}. For example, joining {@code String s}, {@code Object o} and
   * {@code int i} using {@code ", "} would produce {@code String s, Object o, int i}.
   */
  static Collector<CodeBlock, ?, CodeBlock> joiningCodeBlocks(String delimiter) {
    return Collector.of(
        () -> new CodeBlockJoiner(delimiter, CodeBlock.builder()),
        CodeBlockJoiner::add,
        CodeBlockJoiner::merge,
        CodeBlockJoiner::join);
  }

  /**
   * Joins {@link CodeBlock} instances in a manner suitable for use as method parameters (or
   * arguments). This is equivalent to {@code joiningCodeBlocks(", ")}.
   */
  static Collector<CodeBlock, ?, CodeBlock> toParametersCodeBlock() {
    return joiningCodeBlocks(", ");
  }

  /**
   * Joins {@link TypeName} instances into a {@link CodeBlock} that is a comma-separated list for
   * use as type parameters or javadoc method arguments.
   */
  static Collector<TypeName, ?, CodeBlock> toTypeNamesCodeBlock() {
    return Collector.of(
        () -> new CodeBlockJoiner(", ", CodeBlock.builder()),
        CodeBlockJoiner::addTypeName,
        CodeBlockJoiner::merge,
        CodeBlockJoiner::join);
  }

  /**
   * Concatenates {@link CodeBlock} instances separated by newlines for readability. This is
   * equivalent to {@code joiningCodeBlocks("\n")}.
   */
  static Collector<CodeBlock, ?, CodeBlock> toConcatenatedCodeBlock() {
    return joiningCodeBlocks("\n");
  }

  /** Returns a comma-separated version of {@code codeBlocks} as one unified {@link CodeBlock}. */
  static CodeBlock makeParametersCodeBlock(Iterable<CodeBlock> codeBlocks) {
    return stream(codeBlocks.spliterator(), false).collect(toParametersCodeBlock());
  }

  /** Adds an annotation to a method. */
  static void addAnnotation(MethodSpec.Builder method, DeclaredType nullableType) {
    method.addAnnotation(ClassName.get(MoreTypes.asTypeElement(nullableType)));
  }

  private static final class CodeBlockJoiner {
    private final String delimiter;
    private final CodeBlock.Builder builder;
    private boolean first = true;

    CodeBlockJoiner(String delimiter, Builder builder) {
      this.delimiter = delimiter;
      this.builder = builder;
    }

    @CanIgnoreReturnValue
    CodeBlockJoiner add(CodeBlock codeBlock) {
      maybeAddDelimiter();
      builder.add(codeBlock);
      return this;
    }

    @CanIgnoreReturnValue
    CodeBlockJoiner addTypeName(TypeName typeName) {
      maybeAddDelimiter();
      builder.add("$T", typeName);
      return this;
    }

    private void maybeAddDelimiter() {
      if (!first) {
        builder.add(delimiter);
      }
      first = false;
    }

    @CanIgnoreReturnValue
    CodeBlockJoiner merge(CodeBlockJoiner other) {
      CodeBlock otherBlock = other.builder.build();
      if (!otherBlock.isEmpty()) {
        add(otherBlock);
      }
      return this;
    }

    CodeBlock join() {
      return builder.build();
    }
  }

  /**
   * Returns one unified {@link CodeBlock} which joins each item in {@code codeBlocks} with a
   * newline.
   */
  static CodeBlock concat(Iterable<CodeBlock> codeBlocks) {
    return stream(codeBlocks.spliterator(), false).collect(toConcatenatedCodeBlock());
  }

  static CodeBlock stringLiteral(String toWrap) {
    return CodeBlock.of("$S", toWrap);
  }

  /** Returns a javadoc {@literal @link} tag that poins to the given {@link ExecutableElement}. */
  static CodeBlock javadocLinkTo(ExecutableElement executableElement) {
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
        executableElement
            .getParameters()
            .stream()
            .map(VariableElement::asType)
            .map(TypeName::get)
            .map(TypeNames::rawTypeName)
            .collect(toTypeNamesCodeBlock()));
    return builder.add(")}").build();
  }

  private CodeBlocks() {}
}
