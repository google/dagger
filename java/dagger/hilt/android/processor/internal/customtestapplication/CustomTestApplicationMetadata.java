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

package dagger.hilt.android.processor.internal.customtestapplication;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XElements;

/** Stores the metadata for a custom base test application. */
@AutoValue
abstract class CustomTestApplicationMetadata {
  /** Returns the annotated element. */
  abstract XTypeElement element();

  /** Returns the name of the base application. */
  abstract ClassName baseAppName();

  /** Returns the name of the generated application */
  ClassName appName() {
    return Processors.append(
        Processors.getEnclosedClassName(element().getClassName()), "_Application");
  }

  static CustomTestApplicationMetadata of(XElement element) {
    Preconditions.checkState(
        element.hasAnnotation(ClassNames.CUSTOM_TEST_APPLICATION),
        "The given element, %s, is not annotated with @%s.",
        XElements.toStableString(element),
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName());

    ProcessorErrors.checkState(
        XElementKt.isTypeElement(element),
        element,
        "@%s should only be used on classes or interfaces but found: %s",
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
        XElements.toStableString(element));

    XTypeElement baseAppElement = getBaseElement(element);

    return new AutoValue_CustomTestApplicationMetadata(
        XElements.asTypeElement(element), baseAppElement.getClassName());
  }

  private static XTypeElement getBaseElement(XElement element) {
    XTypeElement baseElement =
        element.getAnnotation(ClassNames.CUSTOM_TEST_APPLICATION)
            .getAsType("value")
            .getTypeElement();

    XTypeElement baseSuperclassElement = baseElement;
    while (baseSuperclassElement.getSuperClass() != null) {
      ProcessorErrors.checkState(
          !baseSuperclassElement.hasAnnotation(ClassNames.HILT_ANDROID_APP),
          element,
          "@%s value cannot be annotated with @%s. Found: %s",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          ClassNames.HILT_ANDROID_APP.simpleName(),
          baseSuperclassElement.getClassName());

      ImmutableList<XFieldElement> injectFields =
          baseSuperclassElement.getDeclaredFields().stream()
              .filter(field -> field.hasAnnotation(ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectFields.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject fields. Found "
              + "%s with @Inject fields %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement.getClassName(),
          injectFields.stream().map(XElements::toStableString).collect(toImmutableList()));

      ImmutableList<XExecutableElement> injectMethods =
          baseSuperclassElement.getDeclaredMethods().stream()
              .filter(method -> method.hasAnnotation(ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectMethods.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject methods. Found "
              + "%s with @Inject methods %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement.getClassName(),
          injectMethods.stream().map(XElements::toStableString).collect(toImmutableList()));

      ImmutableList<XExecutableElement> injectConstructors =
          baseSuperclassElement.getConstructors().stream()
              .filter(method -> method.hasAnnotation(ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectConstructors.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject constructors. "
              + "Found %s with @Inject constructors %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement.getClassName().canonicalName(),
          injectConstructors.stream().map(XElements::toStableString).collect(toImmutableList()));

      baseSuperclassElement = baseSuperclassElement.getSuperClass().getTypeElement();
    }

    // We check this last because if the base type is a @HiltAndroidApp we'd accidentally fail
    // with this message instead of the one above when the superclass hasn't yet been generated.
    ProcessorErrors.checkState(
        Processors.isAssignableFrom(baseElement, ClassNames.APPLICATION),
        element,
        "@%s value should be an instance of %s. Found: %s",
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
        ClassNames.APPLICATION,
        baseElement.getClassName());

    return baseElement;
  }
}
