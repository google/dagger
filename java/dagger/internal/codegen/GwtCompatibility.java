/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.squareup.javapoet.AnnotationSpec;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;

final class GwtCompatibility {

  /**
   * Returns a {@code @GwtIncompatible} annotation that is applied to {@code binding}'s {@link
   * Binding#bindingElement()} or any enclosing type.
   */
  static Optional<AnnotationSpec> gwtIncompatibleAnnotation(Binding binding) {
    checkArgument(binding.bindingElement().isPresent());
    Element element = binding.bindingElement().get();
    while (element != null) {
      Optional<AnnotationSpec> gwtIncompatible =
          element
              .getAnnotationMirrors()
              .stream()
              .filter(annotation -> isGwtIncompatible(annotation))
              .map(AnnotationSpec::get)
              .findFirst();
      if (gwtIncompatible.isPresent()) {
        return gwtIncompatible;
      }
      element = element.getEnclosingElement();
    }
    return Optional.empty();
  }

  private static boolean isGwtIncompatible(AnnotationMirror annotation) {
    Name simpleName = annotation.getAnnotationType().asElement().getSimpleName();
    return simpleName.contentEquals("GwtIncompatible");
  }
}
