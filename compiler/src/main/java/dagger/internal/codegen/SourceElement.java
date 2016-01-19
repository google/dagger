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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor6;

/**
 * An {@link Element}, optionally contributed by a subtype of the type that encloses it.
 */
@AutoValue
abstract class SourceElement {

  /** An object that has a {@link SourceElement}. */
  interface HasSourceElement {
    /** The source element associated with this object. */
    SourceElement sourceElement();
  }

  /** The {@link Element} instance.. */
  abstract Element element();

  /**
   * The concrete class that contributed the {@link #element()}, if different from
   * {@link #enclosingTypeElement()}.
   */
  abstract Optional<TypeElement> contributedBy();

  /** The type enclosing the {@link #element()}. */
  TypeElement enclosingTypeElement() {
    return BINDING_TYPE_ELEMENT.visit(element());
  }

  private static final ElementVisitor<TypeElement, Void> BINDING_TYPE_ELEMENT =
      new SimpleElementVisitor6<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element e, Void p) {
          return visit(e.getEnclosingElement());
        }

        @Override
        public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };

  static SourceElement forElement(Element element) {
    return new AutoValue_SourceElement(element, Optional.<TypeElement>absent());
  }

  static SourceElement forElement(Element element, TypeElement contributedBy) {
    return new AutoValue_SourceElement(element, Optional.of(contributedBy));
  }
}
