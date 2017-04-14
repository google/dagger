/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponentBuilder;

import java.util.Iterator;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Suggests a variable name for a type based on a {@link Binding}. Prefer
 * {@link DependencyVariableNamer} for cases where a specific {@link DependencyRequest} is present.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class BindingVariableNamer {

  private BindingVariableNamer() {}

  static String name(Binding binding) {
    StringBuilder builder = new StringBuilder();

    Key key = binding.key();
    if (key.qualifier().isPresent()) {
      // TODO(gak): Use a better name for fields with qualifiers with members.
      builder.append(key.qualifier().get().getAnnotationType().asElement().getSimpleName());
    }

    TypeMirror type = typeToName(binding);
    type.accept(
        new SimpleTypeVisitor6<Void, StringBuilder>() {
          @Override
          public Void visitDeclared(DeclaredType declaredType, StringBuilder builder) {
            Element element = declaredType.asElement();
            if (isSubcomponentBuilder(element)) {
              // Most Subcomponent builders are named "Builder", so add their associated
              // Subcomponent type so that they're not all "builderProvider{N}"
              builder.append(element.getEnclosingElement().getSimpleName());
            }
            builder.append(element.getSimpleName());
            Iterator<? extends TypeMirror> argumentIterator =
                declaredType.getTypeArguments().iterator();
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
        },
        builder);

    return UPPER_CAMEL.to(LOWER_CAMEL, builder.toString());
  }

  private static TypeMirror typeToName(Binding binding) {
    if (binding instanceof ContributionBinding
        && ((ContributionBinding) binding).contributionType().equals(ContributionType.SET)) {
      return SetType.from(binding.key().type()).elementType();
    }
    return binding.key().type();
  }
}
