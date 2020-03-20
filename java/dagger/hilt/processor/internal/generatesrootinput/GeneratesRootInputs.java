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

package dagger.hilt.processor.internal.generatesrootinput;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Extracts the list of annotations annotated with {@link dagger.hilt.GeneratesRootInput}. */
public final class GeneratesRootInputs {
  static final String AGGREGATING_PACKAGE =
      GeneratesRootInputs.class.getPackage().getName() + ".codegen";

  private final Elements elements;
  private final Supplier<ImmutableList<ClassName>> generatesRootInputAnnotations =
      memoize(() -> getAnnotationList());

  public GeneratesRootInputs(ProcessingEnvironment processingEnvironment) {
    this.elements = processingEnvironment.getElementUtils();
  }

  public ImmutableSet<Element> getElementsToWaitFor(RoundEnvironment roundEnv) {
    // Processing can only take place after all dependent annotations have been processed
    // Note: We start with ClassName rather than TypeElement because jdk8 does not treat type
    // elements as equal across rounds. Thus, in order for RoundEnvironment#getElementsAnnotatedWith
    // to work properly, we get new elements to ensure it works across rounds (See b/148693284).
    return generatesRootInputAnnotations.get().stream()
        .map(className -> elements.getTypeElement(className.toString()))
        .filter(element -> element != null)
        .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
        .collect(toImmutableSet());
  }

  private ImmutableList<ClassName> getAnnotationList() {
    PackageElement packageElement = elements.getPackageElement(AGGREGATING_PACKAGE);

    if (packageElement == null) {
      return ImmutableList.of();
    }

    List<? extends Element> annotationElements = packageElement.getEnclosedElements();
    checkState(!annotationElements.isEmpty(), "No elements Found in package %s.", packageElement);

    ImmutableList.Builder<ClassName> builder = ImmutableList.builder();
    for (Element element : annotationElements) {
      ProcessorErrors.checkState(
          element.getKind() == ElementKind.CLASS,
          element,
          "Only classes may be in package %s. Did you add custom code in the package?",
          packageElement);

      AnnotationMirror annotationMirror =
          Processors.getAnnotationMirror(element, ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA);
      ProcessorErrors.checkState(
          annotationMirror != null,
          element,
          "Classes in package %s must be annotated with @%s: %s."
              + " Found: %s. Files in this package are generated, did you add custom code in the"
              + " package? ",
          packageElement,
          ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA,
          element.getSimpleName(),
          element.getAnnotationMirrors());

      TypeElement annotation =
          Processors.getAnnotationClassValue(elements, annotationMirror, "value");

      builder.add(ClassName.get(annotation));
    }
    // This annotation was on Dagger so it couldn't be annotated with @GeneratesRootInput to be
    // cultivated later. We have to manually add it to the list.
    builder.add(ClassNames.PRODUCTION_COMPONENT);
    return builder.build();
  }
}
