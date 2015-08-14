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
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Set;

public final class FieldWriter extends VariableWriter {
  private Optional<Snippet> initializer;

  FieldWriter(TypeName type, String name) {
    super(type, name);
    this.initializer = Optional.absent();
  }

  public void setInitializer(Snippet initializer) {
    this.initializer = Optional.of(initializer);
  }

  public void setInitializer(String initializer, Object... args) {
    this.initializer = Optional.of(Snippet.format(initializer, args));
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    super.write(appendable, context);
    if (initializer.isPresent()) {
      appendable.append(" = ");
      initializer.get().write(appendable, context);
    }
    appendable.append(';');
    return appendable;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    Iterable<? extends HasClassReferences> concat =
        Iterables.concat(ImmutableList.of(type()), initializer.asSet(), annotations);
    return FluentIterable.from(concat)
        .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
          @Override
          public Set<ClassName> apply(HasClassReferences input) {
            return input.referencedClasses();
          }
        })
        .toSet();
  }
}
