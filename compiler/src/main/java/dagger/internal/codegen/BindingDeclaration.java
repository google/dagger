/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import dagger.internal.codegen.Key.HasKey;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static dagger.internal.codegen.Util.AS_DECLARED_TYPE;
import static dagger.internal.codegen.Util.ENCLOSING_TYPE_ELEMENT;

/** An object that declares or specifies a binding. */
abstract class BindingDeclaration implements HasKey {

  /** The {@link Element} that declares the binding. */
  abstract Element bindingElement();

  /**
   * The {@link ExecutableElement} that declares the binding. Equivalent to
   * {@code MoreElements.asExecutable(bindingElement())}.
   *
   * @throws IllegalStateException if {@link #bindingElement()} is not an executable element
   */
  ExecutableElement bindingElementAsExecutable() {
    try {
      return MoreElements.asExecutable(bindingElement());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The type enclosing the {@link #bindingElement()}. */
  TypeElement bindingTypeElement() {
    return ENCLOSING_TYPE_ELEMENT.visit(bindingElement());
  }

  /**
   * The installed module class that contributed the {@link #bindingElement()}. May be a subclass
   * of the class that contains {@link #bindingElement()}.
   */
  abstract Optional<TypeElement> contributingModule();

  /**
   * The type of {@link #contributingModule()}.
   */
  Optional<DeclaredType> contributingModuleType() {
    return contributingModule().transform(AS_DECLARED_TYPE);
  }

  static final Function<BindingDeclaration, Set<TypeElement>> CONTRIBUTING_MODULE =
      new Function<BindingDeclaration, Set<TypeElement>>() {
        @Override
        public Set<TypeElement> apply(BindingDeclaration bindingDeclaration) {
          return bindingDeclaration.contributingModule().asSet();
        }
      };

  static Predicate<BindingDeclaration> bindingElementHasModifier(final Modifier modifier) {
    return new Predicate<BindingDeclaration>() {
      @Override
      public boolean apply(BindingDeclaration bindingDeclaration) {
        return bindingDeclaration.bindingElement().getModifiers().contains(modifier);
      }
    };
  }
}
