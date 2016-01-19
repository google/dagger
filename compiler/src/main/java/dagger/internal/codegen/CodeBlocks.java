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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Iterator;

final class CodeBlocks {

  /** Shorthand for a {@link CodeBlock} with a single format and an argument list. */
  static CodeBlock format(String format, Object... args) {
    return CodeBlock.builder().add(format, args).build();
  }

  static CodeBlock makeParametersCodeBlock(Iterable<CodeBlock> codeBlocks) {
    return join(codeBlocks, ", ");
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

  static Function<ParameterSpec, CodeBlock> PARAMETER_NAME =
      new Function<ParameterSpec, CodeBlock>() {
          @Override
          public CodeBlock apply(ParameterSpec input) {
            return CodeBlocks.format("$N", input);
          }
      };

  private CodeBlocks() {}
}
