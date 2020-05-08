/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.custombasetestapplication;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Extracts the list of test applications defined in the given build compilation. */
public final class CustomBaseTestApplications {
  static final String AGGREGATING_PACKAGE =
      CustomBaseTestApplications.class.getPackage().getName() + ".codegen";

  /** Stores the metadata for a custom base test application. */
  @AutoValue
  public abstract static class CustomBaseTestApplicationMetadata {
    /** Returns the annotated element. */
    public abstract TypeElement element();

    /** Returns the name of the base application element. */
    public abstract TypeElement baseAppElement();

    /** Returns the name of the base application. */
    public ClassName elementName() {
      return ClassName.get(element());
    }

    /** Returns the name of the base application. */
    public ClassName baseAppName() {
      return ClassName.get(baseAppElement());
    }

    /** Returns the name of the generated application */
    public ClassName appName() {
      return Processors.append(Processors.getEnclosedClassName(elementName()), "_Application");
    }
  }

  public static Optional<CustomBaseTestApplicationMetadata> get(Elements elements) {
    ImmutableSet<CustomBaseTestApplicationMetadata> customBaseTestApplications =
        getInternal(elements);
    if (customBaseTestApplications.isEmpty()) {
      return Optional.empty();
    }

    // TODO(b/155291926): Support robolectric tests with multiple @CustomBaseTestApplication.
    checkState(
        customBaseTestApplications.size() == 1,
        "Cannot have more than 1 @CustomBaseTestApplication. Found: %s",
        customBaseTestApplications.stream()
            .map(CustomBaseTestApplicationMetadata::element)
            .map(TypeElement::getQualifiedName)
            .collect(joining(", ")));

    return Optional.of(customBaseTestApplications.iterator().next());
  }

  private static ImmutableSet<CustomBaseTestApplicationMetadata> getInternal(Elements elements) {
    PackageElement packageElement = elements.getPackageElement(AGGREGATING_PACKAGE);
    if (packageElement == null) {
      return ImmutableSet.of();
    }
    List<? extends Element> enclosedElements = packageElement.getEnclosedElements();
    ProcessorErrors.checkState(
        !enclosedElements.isEmpty(),
        packageElement,
        "No enclosed elements Found in package %s. Did you delete code in the package?",
        packageElement);

    ImmutableSet.Builder<CustomBaseTestApplicationMetadata> builder = ImmutableSet.builder();
    for (Element element : enclosedElements) {
      ProcessorErrors.checkState(
          MoreElements.isType(element),
          element,
          "Only interfaces and classes may be in package %s."
              + " Did you add custom code in the package?",
          packageElement);

      AnnotationMirror mirror =
          Processors.getAnnotationMirror(
              element, AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION_DATA);

      ProcessorErrors.checkState(
          mirror != null,
          element,
          "Classes in package %s must be annotated with @%s: %s."
              + " Found: %s. Files in this package are generated, did you add custom code in the"
              + " package? ",
          packageElement,
          AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION_DATA,
          element.getSimpleName(),
          element.getAnnotationMirrors());

      TypeElement annotatedElement =
          Processors.getAnnotationClassValue(elements, mirror, "annotatedClass");
      TypeElement baseApplicationElement =
          Processors.getAnnotationClassValue(elements, mirror, "baseApplicationClass");

      builder.add(
          new AutoValue_CustomBaseTestApplications_CustomBaseTestApplicationMetadata(
              annotatedElement, baseApplicationElement));
    }

    return builder.build();
  }

  private CustomBaseTestApplications() {}
}
