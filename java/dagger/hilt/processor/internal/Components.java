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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreElements;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import dagger.hilt.processor.internal.kotlin.KotlinMetadataUtils;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Helper methods for defining components and the component hierarchy. */
public final class Components {
  // TODO(bcorso): Remove this once all usages are replaced with #getComponents().
  /**
   * Returns the {@link ComponentDescriptor}s for a given element annotated with {@link
   * dagger.hilt.InstallIn}.
   */
  public static ImmutableSet<ComponentDescriptor> getComponentDescriptors(
      Elements elements, Element element) {
    DefineComponents defineComponents = DefineComponents.create();
    return getComponents(elements, element).stream()
        .map(component -> elements.getTypeElement(component.canonicalName()))
        // TODO(b/144939893): Memoize ComponentDescriptors so we're not recalculating.
        .map(defineComponents::componentDescriptor)
        .collect(toImmutableSet());
  }

  /** Returns the {@link dagger.hilt.InstallIn} components for a given element. */
  public static ImmutableSet<ClassName> getComponents(Elements elements, Element element) {
    ImmutableSet<ClassName> components;
    if (Processors.hasAnnotation(element, ClassNames.INSTALL_IN)
        || Processors.hasAnnotation(element, ClassNames.TEST_INSTALL_IN)) {
      components = getHiltInstallInComponents(elements, element);
    } else {
      // Check the enclosing element in case it passed in module is a companion object. This helps
      // in cases where the element was arrived at by checking a binding method and moving outward.
      Element enclosing = element.getEnclosingElement();
      if (enclosing != null
          && MoreElements.isType(enclosing)
          && MoreElements.isType(element)
          && Processors.hasAnnotation(enclosing, ClassNames.MODULE)
          && KotlinMetadataUtils.getMetadataUtil().isCompanionObjectClass(
              MoreElements.asType(element))) {
        return getComponents(elements, enclosing);
      }
      if (Processors.hasErrorTypeAnnotation(element)) {
        throw new BadInputException(
            "Error annotation found on element " + element + ". Look above for compilation errors",
            element);
      } else {
        throw new BadInputException(
            String.format(
                "An @InstallIn annotation is required for: %s." ,
                element),
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

  private static ImmutableSet<ClassName> getHiltInstallInComponents(
      Elements elements, Element element) {
    Preconditions.checkArgument(
        Processors.hasAnnotation(element, ClassNames.INSTALL_IN)
            || Processors.hasAnnotation(element, ClassNames.TEST_INSTALL_IN));

    ImmutableSet<TypeElement> components =
        ImmutableSet.copyOf(
            Processors.hasAnnotation(element, ClassNames.INSTALL_IN)
                ? Processors.getAnnotationClassValues(
                    elements,
                    Processors.getAnnotationMirror(element, ClassNames.INSTALL_IN),
                    "value")
                : Processors.getAnnotationClassValues(
                    elements,
                    Processors.getAnnotationMirror(element, ClassNames.TEST_INSTALL_IN),
                    "components"));

    ImmutableSet<TypeElement> undefinedComponents =
        components.stream()
            .filter(component -> !Processors.hasAnnotation(component, ClassNames.DEFINE_COMPONENT))
            .collect(toImmutableSet());

    ProcessorErrors.checkState(
        undefinedComponents.isEmpty(),
        element,
        "@InstallIn, can only be used with @DefineComponent-annotated classes, but found: %s",
        undefinedComponents);

    return components.stream().map(ClassName::get).collect(toImmutableSet());
  }

  private Components() {}
}
