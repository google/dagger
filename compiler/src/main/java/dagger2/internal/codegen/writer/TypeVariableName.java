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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public final class TypeVariableName implements TypeName {
  private final CharSequence name;
  private final Iterable<? extends TypeName> extendsBounds;

  TypeVariableName(CharSequence name, Iterable<? extends TypeName> extendsBounds) {
    this.name = name;
    this.extendsBounds = extendsBounds;
  }

  public CharSequence name() {
    return name;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    ImmutableSet.Builder<ClassName> builder = new ImmutableSet.Builder<ClassName>();
    for (TypeName bound : extendsBounds) {
      builder.addAll(bound.referencedClasses());
    }
    return builder.build();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append(name);
    if (!Iterables.isEmpty(extendsBounds)) {
      appendable.append(" extends ");
      Iterator<? extends TypeName> iter = extendsBounds.iterator();
      iter.next().write(appendable, context);
      while (iter.hasNext()) {
        appendable.append(" & ");
        iter.next().write(appendable, context);  
      }
    }
    return appendable;
  }

  @Override
  public String toString() {
    return Writables.writeToString(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TypeVariableName) {
      TypeVariableName that = (TypeVariableName) obj;
      return this.name.toString().equals(that.name.toString())
          && this.extendsBounds.equals(that.extendsBounds);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, extendsBounds);
  }

  static TypeVariableName named(CharSequence name) {
    return new TypeVariableName(name, ImmutableList.<TypeName>of());
  }
  
  public static TypeVariableName fromTypeVariable(TypeVariable variable) {
    // Note: We don't have any use right now for the bounds because these are references
    // to the type & not the specification of the type itself.  We never generate
    // code with type variables that include upper or lower bounds.
    return named(variable.asElement().getSimpleName());
  }

  // TODO(sameb): Consider making this a whole different thing: TypeParameterName since it
  // has different semantics than a TypeVariable (parameters only have upper bounds).
  public static TypeVariableName fromTypeParameterElement(TypeParameterElement element) {
    // We filter out bounds of type Object because those would just clutter the generated code.
    Iterable<? extends TypeName> bounds =
        FluentIterable.from(element.getBounds())
            .filter(new Predicate<TypeMirror>() {
              @Override public boolean apply(TypeMirror input) {
                return !MoreTypes.isType(input) || !MoreTypes.isTypeOf(Object.class, input);
              }
            })
            .transform(TypeNames.FOR_TYPE_MIRROR);
    return new TypeVariableName(element.getSimpleName(), bounds);
  }
}
