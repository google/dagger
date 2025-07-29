/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XElements;

/** Helper methods for defining components and the component hierarchy. */
public final class Components {
  /** Returns the {@link dagger.hilt.InstallIn} components for a given element. */
  public static ImmutableSet<ClassName> getComponents(XElement element) {
    ImmutableSet<ClassName> components;
    if (element.hasAnnotation(ClassNames.INSTALL_IN)
        || element.hasAnnotation(ClassNames.TEST_INSTALL_IN)) {
      components = getHiltInstallInComponents(element);
    } else {
      // Check the enclosing element in case it passed in module is a companion object. This helps
      // in cases where the element was arrived at by checking a binding method and moving outward.
      XElement enclosing = element.getEnclosingElement();
      if (enclosing != null
          && isTypeElement(enclosing)
          && isTypeElement(element)
          && enclosing.hasAnnotation(ClassNames.MODULE)
          && asTypeElement(element).isCompanionObject()) {
        return getComponents(enclosing);
      }
      if (Processors.hasErrorTypeAnnotation(element)) {
        throw new BadInputException(
            String.format(
                "Error annotation found on element %s. Look above for compilation errors",
                XElements.toStableString(element)),
            element);
      } else {
        throw new BadInputException(
            String.format(
                "An @InstallIn annotation is required for: %s." ,
                XElements.toStableString(element)),
            element);
      }
    }

    return components;
  }

  public static AnnotationSpec getInstallInAnnotationSpec(ImmutableSet<ClassName> components) {
    Preconditions.checkArgument(!components.isEmpty());
    AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassNames.INSTALL_IN);
    components.forEach(component -> builder.addMember("value", "$T.class", component));
    return builder.build();
  }

  private static ImmutableSet<ClassName> getHiltInstallInComponents(XElement element) {
    Preconditions.checkArgument(
        element.hasAnnotation(ClassNames.INSTALL_IN)
            || element.hasAnnotation(ClassNames.TEST_INSTALL_IN));

    ImmutableList<XTypeElement> components =
        element.hasAnnotation(ClassNames.INSTALL_IN)
            ? Processors.getAnnotationClassValues(
                element.getAnnotation(ClassNames.INSTALL_IN), "value")
            : Processors.getAnnotationClassValues(
                element.getAnnotation(ClassNames.TEST_INSTALL_IN), "components");

    ImmutableSet<XTypeElement> undefinedComponents =
        components.stream()
            .filter(component -> !component.hasAnnotation(ClassNames.DEFINE_COMPONENT))
            .collect(toImmutableSet());

    ProcessorErrors.checkState(
        undefinedComponents.isEmpty(),
        element,
        "@InstallIn, can only be used with @DefineComponent-annotated classes, but found: %s",
        undefinedComponents.stream().map(XElements::toStableString).collect(toImmutableList()));

    return components.stream().map(XTypeElement::getClassName).collect(toImmutableSet());
  }

  private Components() {}
}
