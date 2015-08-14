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
package dagger2.internal.codegen;

import com.google.common.base.Function;
import java.util.Iterator;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

/**
 * Suggests a variable name for a type based on a {@link Key}. Prefer
 * {@link DependencyVariableNamer} for cases where a specific {@link DependencyRequest} is present.
 *
 * @author Gregory Kick
 * @since 2.0
 */
enum KeyVariableNamer implements Function<Key, String> {
  INSTANCE;

  @Override
  public String apply(Key key) {
    StringBuilder builder = new StringBuilder();

    if (key.qualifier().isPresent()) {
      if (!key.qualifier().get().getElementValues().isEmpty()) {
        // TODO(gak): obviously we need to support this
        throw new UnsupportedOperationException();
      }
      builder.append(key.qualifier().get().getAnnotationType().asElement().getSimpleName());
    }

    key.type().accept(new SimpleTypeVisitor6<Void, StringBuilder>() {
      @Override
      public Void visitDeclared(DeclaredType t, StringBuilder builder) {
        builder.append(t.asElement().getSimpleName());
        Iterator<? extends TypeMirror> argumentIterator = t.getTypeArguments().iterator();
        if (argumentIterator.hasNext()) {
          builder.append("Of");
          TypeMirror first = argumentIterator.next();
          first.accept(this, builder);
          while (argumentIterator.hasNext()) {
            builder.append("And");
            argumentIterator.next().accept(this, builder);
          }
        }
        return null;
      }
    }, builder);

    return UPPER_CAMEL.to(LOWER_CAMEL, builder.toString());
  }
}
