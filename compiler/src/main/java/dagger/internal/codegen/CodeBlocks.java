/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Iterator;
import javax.lang.model.type.TypeMirror;

final class CodeBlocks {

  /**
   * Returns a comma-separated version of {@code codeBlocks} as one unified {@link CodeBlock}.
   */
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

  static CodeBlock join(Iterable<CodeBlock> codeBlocks, String delimiter) {
    CodeBlock.Builder builder = CodeBlock.builder();
    Iterator<CodeBlock> iterator = codeBlocks.iterator();
    while (iterator.hasNext()) {
      builder.add(iterator.next());
      if (iterator.hasNext()) {
        builder.add(delimiter);
      }
    }
    return builder.build();
  }

  static FluentIterable<CodeBlock> toCodeBlocks(Iterable<? extends TypeMirror> typeMirrors) {
    return FluentIterable.from(typeMirrors).transform(TYPE_MIRROR_TO_CODE_BLOCK);
  }

  static CodeBlock stringLiteral(String toWrap) {
    return CodeBlock.of("$S", toWrap);
  }

  private static final Function<TypeMirror, CodeBlock> TYPE_MIRROR_TO_CODE_BLOCK =
      new Function<TypeMirror, CodeBlock>() {
        @Override
        public CodeBlock apply(TypeMirror typeMirror) {
          return CodeBlock.of("$T", typeMirror);
        }
      };

  static Function<ParameterSpec, CodeBlock> PARAMETER_NAME =
      new Function<ParameterSpec, CodeBlock>() {
          @Override
          public CodeBlock apply(ParameterSpec input) {
            return CodeBlock.of("$N", input);
          }
      };

  private CodeBlocks() {}
}
