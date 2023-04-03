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

import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.internal.codegen.xprocessing.XAnnotations;
import java.util.List;

/** Extracts the list of annotations annotated with {@link dagger.hilt.GeneratesRootInput}. */
public final class GeneratesRootInputs {
  static final String AGGREGATING_PACKAGE =
      GeneratesRootInputs.class.getPackage().getName() + ".codegen";

  private final XProcessingEnv env;
  private final Supplier<ImmutableList<ClassName>> generatesRootInputAnnotations =
      memoize(() -> getAnnotationList());

  public GeneratesRootInputs(XProcessingEnv processingEnvironment) {
    this.env = processingEnvironment;
  }

  public ImmutableSet<XElement> getElementsToWaitFor(XRoundEnv roundEnv) {
    // Processing can only take place after all dependent annotations have been processed
    // Note: We start with ClassName rather than TypeElement because jdk8 does not treat type
    // elements as equal across rounds. Thus, in order for RoundEnvironment#getElementsAnnotatedWith
    // to work properly, we get new elements to ensure it works across rounds (See b/148693284).
    return generatesRootInputAnnotations.get().stream()
        .map(className -> env.findTypeElement(className.toString()))
        .filter(element -> element != null)
        .flatMap(
            annotation -> roundEnv.getElementsAnnotatedWith(annotation.getQualifiedName()).stream())
        .collect(toImmutableSet());
  }

  private ImmutableList<ClassName> getAnnotationList() {
    List<? extends XTypeElement> annotationElements =
        env.getTypeElementsFromPackage(AGGREGATING_PACKAGE);

    ImmutableList.Builder<ClassName> builder = ImmutableList.builder();
    for (XTypeElement element : annotationElements) {
      ProcessorErrors.checkState(
          element.isClass(),
          element,
          "Only classes may be in package %s. Did you add custom code in the package?",
          AGGREGATING_PACKAGE);

      XAnnotation annotation =
          element.getAnnotation(ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA);
      ProcessorErrors.checkState(
          annotation != null,
          element,
          "Classes in package %s must be annotated with @%s: %s."
              + " Found: %s. Files in this package are generated, did you add custom code in the"
              + " package? ",
          AGGREGATING_PACKAGE,
          ClassNames.GENERATES_ROOT_INPUT_PROPAGATED_DATA,
          element.getClassName().simpleName(),
          element.getAllAnnotations().stream()
              .map(XAnnotations::toStableString)
              .collect(toImmutableSet()));

      XTypeElement value = annotation.getAsType("value").getTypeElement();

      builder.add(value.getClassName());
    }
    // This annotation was on Dagger so it couldn't be annotated with @GeneratesRootInput to be
    // cultivated later. We have to manually add it to the list.
    builder.add(ClassNames.PRODUCTION_COMPONENT);
    return builder.build();
  }
}
