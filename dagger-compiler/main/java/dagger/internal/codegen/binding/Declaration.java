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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.extension.Optionals.emptiesLast;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.Comparator.comparing;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XTypeElement;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Comparator;
import java.util.Optional;

/** An object that declares or specifies a binding. */
public abstract class Declaration {
  /**
   * A comparator that compares binding declarations with elements.
   *
   * <p>Compares, in order:
   *
   * <ol>
   *   <li>Contributing module or enclosing type name
   *   <li>Binding element's simple name
   *   <li>Binding element's type
   * </ol>
   *
   * Any binding declarations without elements are last.
   */
  public static final Comparator<Declaration> COMPARATOR =
      comparing(
              (Declaration declaration) ->
                  declaration.contributingModule().isPresent()
                      ? declaration.contributingModule()
                      : declaration.bindingTypeElement(),
              emptiesLast(comparing(XTypeElement::getQualifiedName)))
          .thenComparing(
              Declaration::bindingElement,
              emptiesLast(
                  comparing((XElement element) -> getSimpleName(element))
                      .thenComparing(XElements::toStableString)));

  /** The {@link Key} of this declaration. */
  public abstract Key key();

  /**
   * The {@link XElement} that declares this binding. Absent for {@linkplain BindingKind binding
   * kinds} that are not always declared by exactly one element.
   *
   * <p>For example, consider {@link BindingKind#MULTIBOUND_SET}. A component with many
   * {@code @IntoSet} bindings for the same key will have a synthetic binding that depends on all
   * contributions, but with no identifying binding element. A {@code @Multibinds} method will also
   * contribute a synthetic binding, but since multiple {@code @Multibinds} methods can coexist in
   * the same component (and contribute to one single binding), it has no binding element.
   */
  public abstract Optional<XElement> bindingElement();

  /**
   * The type enclosing the {@link #bindingElement()}, or {@link Optional#empty()} if {@link
   * #bindingElement()} is empty.
   */
  public final Optional<XTypeElement> bindingTypeElement() {
    return bindingElement().map(XElements::closestEnclosingTypeElement);
  }

  /**
   * The installed module class that contributed the {@link #bindingElement()}. May be a subclass of
   * the class that contains {@link #bindingElement()}. Absent if {@link #bindingElement()} is
   * empty.
   */
  public abstract Optional<XTypeElement> contributingModule();
}
