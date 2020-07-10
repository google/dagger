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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Helper methods for defining components and the component hierarchy. */
public final class Components {

  /**
   * Returns the {@link ComponentDescriptor}s for a given element annotated with {@link
   * dagger.hilt.InstallIn}.
   */
  public static ImmutableSet<ComponentDescriptor> getComponentDescriptors(
      Elements elements, Element element) {
    ImmutableSet<ComponentDescriptor> componentDescriptors;
    if (Processors.hasAnnotation(element, ClassNames.INSTALL_IN)) {
      componentDescriptors = getInstallInComponentDescriptors(elements, element);
    } else if (Processors.hasErrorTypeAnnotation(element)) {
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

    return componentDescriptors;
  }

  public static AnnotationSpec getInstallInAnnotationSpec(ImmutableSet<ClassName> components) {
    Preconditions.checkArgument(!components.isEmpty());
    AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassNames.INSTALL_IN);
    components.forEach(component -> builder.addMember("value", "$T.class", component));
    return builder.build();
  }

  private static ImmutableSet<ComponentDescriptor> getInstallInComponentDescriptors(
      Elements elements, Element element) {
    AnnotationMirror hiltInstallIn =
        Processors.getAnnotationMirror(element, ClassNames.INSTALL_IN);
    ImmutableList<TypeElement> generatedComponents =
        Processors.getAnnotationClassValues(elements, hiltInstallIn, "value");
    DefineComponents defineComponents = DefineComponents.create();
    return generatedComponents.stream()
        .map(e -> mapComponents(elements, e))
        // TODO(b/144939893): Memoize ComponentDescriptors so we're not recalculating.
        .map(defineComponents::componentDescriptor)
        .collect(toImmutableSet());
  }

  // Temporary hack while ApplicationComponent is renamed to SingletonComponent
  private static TypeElement mapComponents(Elements elements, TypeElement element) {
    if (element.getQualifiedName().contentEquals("dagger.hilt.android.components.ApplicationComponent")) {
      TypeElement singletonComponent = elements.getTypeElement(
          "dagger.hilt.components.SingletonComponent");
      Preconditions.checkState(singletonComponent != null);
      return singletonComponent;
    }
    return element;
  }

  private Components() {}
}
