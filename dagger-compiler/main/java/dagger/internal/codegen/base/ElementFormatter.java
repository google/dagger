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

package dagger.internal.codegen.base;

import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.inject.Inject;

/**
 * Formats elements into a useful string representation.
 *
 * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
 *
 * <p>If the element is a parameter, the returned string will include the enclosing executable,
 * with other parameters elided.
 */
public final class ElementFormatter extends Formatter<XElement> {
  @Inject
  ElementFormatter() {}

  @Override
  public String format(XElement element) {
    return elementToString(element);
  }

  /**
   * Returns a useful string form for an element.
   *
   * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
   *
   * <p>If the element is a parameter, the returned string will include the enclosing executable,
   * with other parameters elided.
   */
  public static String elementToString(XElement element) {
    return elementToString(element, /* elideMethodParameterTypes= */ false);
  }

  /**
   * Returns a useful string form for an element.
   *
   * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
   *
   * <p>Parameters are given with their enclosing executable, with other parameters elided.
   */
  public static String elementToString(XElement element, boolean elideMethodParameterTypes) {
    if (isExecutable(element)) {
      return enclosingTypeAndMemberName(element)
          .append(
              elideMethodParameterTypes
                  ? (asExecutable(element).getParameters().isEmpty() ? "()" : "(…)")
                  : asExecutable(element).getParameters().stream()
                      .map(parameter -> XTypes.toStableString(parameter.getType()))
                      .collect(joining(", ", "(", ")")))
          .toString();
    } else if (isMethodParameter(element)) {
      XExecutableElement methodOrConstructor = asMethodParameter(element).getEnclosingElement();
      return enclosingTypeAndMemberName(methodOrConstructor)
          .append('(')
          .append(
              formatArgumentInList(
                  methodOrConstructor.getParameters().indexOf(element),
                  methodOrConstructor.getParameters().size(),
                  getSimpleName(element)))
          .append(')')
          .toString();
    } else if (isField(element)) {
      return enclosingTypeAndMemberName(element).toString();
    } else if (isTypeElement(element)) {
      return asTypeElement(element).getQualifiedName();
    }
    throw new UnsupportedOperationException("Can't determine string for element " + element);
  }

  private static StringBuilder enclosingTypeAndMemberName(XElement element) {
    StringBuilder name = new StringBuilder(elementToString(element.getEnclosingElement()));
    if (!getSimpleName(element).contentEquals("<init>")) {
      name.append('.').append(getSimpleName(element));
    }
    return name;
  }
}
