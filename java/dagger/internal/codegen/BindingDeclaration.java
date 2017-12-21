/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.DaggerElements.ENCLOSING_TYPE_ELEMENT;

import dagger.model.Key;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** An object that declares or specifies a binding. */
abstract class BindingDeclaration {

  /** The {@link Key} of this declaration. */
  abstract Key key();

  /**
   * The {@link Element} that declares the binding. Absent for bindings without identifying
   * declarations.
   */
  abstract Optional<? extends Element> bindingElement();

  /**
   * The type enclosing the {@link #bindingElement()}, or {@link Optional#empty()} if {@link
   * #bindingElement()} is empty.
   */
  Optional<TypeElement> bindingTypeElement() {
    return bindingElement().map(element -> element.accept(ENCLOSING_TYPE_ELEMENT, null));
  }
  
  /**
   * The installed module class that contributed the {@link #bindingElement()}. May be a subclass of
   * the class that contains {@link #bindingElement()}. Absent if {@link #bindingElement()} is
   * empty.
   */
  abstract Optional<TypeElement> contributingModule();
}
