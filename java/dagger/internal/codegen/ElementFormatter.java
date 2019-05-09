/*
 * Copyright (C) 2013 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.asExecutable;
import static java.util.stream.Collectors.joining;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * Formats elements into a useful string representation.
 *
 * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
 *
 * <p>Parameters are given with their enclosing executable, with other parameters elided.
 */
final class ElementFormatter extends Formatter<Element> {
  @Inject
  ElementFormatter() {}

  @Override
  public String format(Element element) {
    return elementToString(element);
  }

  /**
   * Returns a useful string form for an element.
   *
   * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
   *
   * <p>Parameters are given with their enclosing executable, with other parameters elided.
   */
  static String elementToString(Element element) {
    return element.accept(ELEMENT_TO_STRING, null);
  }

  private static final ElementVisitor<String, Void> ELEMENT_TO_STRING =
      new ElementKindVisitor8<String, Void>() {
        @Override
        public String visitExecutable(ExecutableElement executableElement, Void aVoid) {
          return enclosingTypeAndMemberName(executableElement)
              .append(
                  executableElement.getParameters().stream()
                      .map(parameter -> parameter.asType().toString())
                      .collect(joining(", ", "(", ")")))
              .toString();
        }

        @Override
        public String visitVariableAsParameter(VariableElement parameter, Void aVoid) {
          ExecutableElement methodOrConstructor = asExecutable(parameter.getEnclosingElement());
          return enclosingTypeAndMemberName(methodOrConstructor)
              .append('(')
              .append(
                  formatArgumentInList(
                      methodOrConstructor.getParameters().indexOf(parameter),
                      methodOrConstructor.getParameters().size(),
                      parameter.getSimpleName()))
              .append(')')
              .toString();
        }

        @Override
        public String visitVariableAsField(VariableElement field, Void aVoid) {
          return enclosingTypeAndMemberName(field).toString();
        }

        @Override
        public String visitType(TypeElement type, Void aVoid) {
          return type.getQualifiedName().toString();
        }

        @Override
        protected String defaultAction(Element element, Void aVoid) {
          throw new UnsupportedOperationException(
              "Can't determine string for " + element.getKind() + " element " + element);
        }

        private StringBuilder enclosingTypeAndMemberName(Element element) {
          StringBuilder name = new StringBuilder(element.getEnclosingElement().accept(this, null));
          if (!element.getSimpleName().contentEquals("<init>")) {
            name.append('.').append(element.getSimpleName());
          }
          return name;
        }
      };
}
