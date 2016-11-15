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

import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import java.util.Iterator;
import java.util.Spliterator;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

final class CodeBlocks {

  /** Returns a comma-separated version of {@code codeBlocks} as one unified {@link CodeBlock}. */
  static CodeBlock makeParametersCodeBlock(Iterable<CodeBlock> codeBlocks) {
    return join(codeBlocks, ", ");
  }

  /**
   * Returns one unified {@link CodeBlock} which joins each item in {@code codeBlocks} with a
   * newline.
   */
  static CodeBlock concat(Iterable<CodeBlock> codeBlocks) {
    return join(codeBlocks, "\n");
  }

  static CodeBlock.Builder join(
      CodeBlock.Builder builder, Iterable<CodeBlock> codeBlocks, String delimiter) {
    Iterator<CodeBlock> iterator = codeBlocks.iterator();
    while (iterator.hasNext()) {
      builder.add(iterator.next());
      if (iterator.hasNext()) {
        builder.add(delimiter);
      }
    }
    return builder;
  }

  static CodeBlock join(Iterable<CodeBlock> codeBlocks, String delimiter) {
    return join(CodeBlock.builder(), codeBlocks, delimiter).build();
  }

  static FluentIterable<CodeBlock> toCodeBlocks(Iterable<? extends TypeMirror> typeMirrors) {
    return FluentIterable.from(typeMirrors).transform(typeMirror -> CodeBlock.of("$T", typeMirror));
  }

  static CodeBlock stringLiteral(String toWrap) {
    return CodeBlock.of("$S", toWrap);
  }

  /** Returns a javadoc {@literal @link} tag that poins to the given {@link ExecutableElement}. */
  static CodeBlock javadocLinkTo(ExecutableElement executableElement) {
    CodeBlock.Builder builder =
        CodeBlock.builder().add("{@link $T#", executableElement.getEnclosingElement());
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
    Spliterator<TypeName> rawTypesSpliterator =
        executableElement
            .getParameters()
            .stream()
            .map(VariableElement::asType)
            .map(TypeName::get)
            .map(TypeNames::rawTypeName)
            .spliterator();
    rawTypesSpliterator.tryAdvance(first -> builder.add("$T", first));
    rawTypesSpliterator.forEachRemaining(remaining -> builder.add(", $T", remaining));
    return builder.add(")}").build();
  }

  private CodeBlocks() {}
}
