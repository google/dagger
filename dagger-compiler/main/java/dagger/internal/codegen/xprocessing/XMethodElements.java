/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;


import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XMethodElement} helper methods. */
public final class XMethodElements {

  /** Returns the type this method is enclosed in. */
  public static XTypeElement getEnclosingTypeElement(XMethodElement method) {
    // TODO(bcorso): In Javac, a method is always enclosed in a type; however, once we start
    //  processing Kotlin we'll want to check this explicitly and add an error to the validation
    //  report if the method is not enclosed in a type.
    return method.getEnclosingElement().getType().getTypeElement();
  }

  /** Returns {@code true} if the given method has type parameters. */
  public static boolean hasTypeParameters(XMethodElement method) {
    return !method.getExecutableType().getTypeVariableNames().isEmpty();
  }

  private XMethodElements() {}
}
