/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen.writer;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class BlockWriter implements Writable, HasClassReferences {
  private final List<Snippet> snippets;

  BlockWriter() {
    this.snippets = Lists.newArrayList();
  }

  public BlockWriter addSnippet(String snippet, Object... args) {
    snippets.add(Snippet.format(snippet, args));
    return this;
  }

  public BlockWriter addSnippet(Snippet snippet) {
    snippets.add(snippet);
    return this;
  }

  boolean isEmpty() {
    return snippets.isEmpty();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    for (Snippet snippet : snippets) {
      appendable.append('\n');
      snippet.write(appendable, context);
    }
    return appendable.append('\n');
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return FluentIterable.from(snippets)
        .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
          @Override
          public Set<ClassName> apply(HasClassReferences input) {
            return input.referencedClasses();
          }
        })
        .toSet();
  }
}
