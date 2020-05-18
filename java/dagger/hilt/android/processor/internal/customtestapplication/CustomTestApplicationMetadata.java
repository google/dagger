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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/** Stores the metadata for a custom base test application. */
@AutoValue
abstract class CustomTestApplicationMetadata {
  /** Returns the annotated element. */
  abstract TypeElement element();

  /** Returns the name of the base application. */
  abstract ClassName baseAppName();

  /** Returns the name of the generated application */
  ClassName appName() {
    return Processors.append(
        Processors.getEnclosedClassName(ClassName.get(element())), "_Application");
  }

  static CustomTestApplicationMetadata of(Element element, Elements elements) {
    Preconditions.checkState(
        Processors.hasAnnotation(element, ClassNames.CUSTOM_TEST_APPLICATION),
        "The given element, %s, is not annotated with @%s.",
        element,
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName());

    ProcessorErrors.checkState(
        MoreElements.isType(element),
        element,
        "@%s should only be used on classes or interfaces but found: %s",
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
        element);

    TypeElement baseAppElement = getBaseElement(element, elements);

    return new AutoValue_CustomTestApplicationMetadata(
        MoreElements.asType(element), ClassName.get(baseAppElement));
  }

  private static TypeElement getBaseElement(Element element, Elements elements) {
    TypeElement baseElement =
        Processors.getAnnotationClassValue(
            elements,
            Processors.getAnnotationMirror(element, ClassNames.CUSTOM_TEST_APPLICATION),
            "value");

    TypeElement baseSuperclassElement = baseElement;
    while (!baseSuperclassElement.getSuperclass().getKind().equals(TypeKind.NONE)) {
      ProcessorErrors.checkState(
          !Processors.hasAnnotation(baseSuperclassElement, ClassNames.HILT_ANDROID_APP),
          element,
          "@%s value cannot be annotated with @%s. Found: %s",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          ClassNames.HILT_ANDROID_APP.simpleName(),
          baseSuperclassElement);

      ImmutableList<VariableElement> injectFields =
          ElementFilter.fieldsIn(baseSuperclassElement.getEnclosedElements()).stream()
              .filter(field -> Processors.hasAnnotation(field, ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectFields.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject fields. Found "
              + "%s with @Inject fields %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement,
          injectFields);

      ImmutableList<ExecutableElement> injectMethods =
          ElementFilter.methodsIn(baseSuperclassElement.getEnclosedElements()).stream()
              .filter(method -> Processors.hasAnnotation(method, ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectMethods.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject methods. Found "
              + "%s with @Inject methods %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement,
          injectMethods);

      ImmutableList<ExecutableElement> injectConstructors =
          ElementFilter.constructorsIn(baseSuperclassElement.getEnclosedElements()).stream()
              .filter(method -> Processors.hasAnnotation(method, ClassNames.INJECT))
              .collect(toImmutableList());
      ProcessorErrors.checkState(
          injectConstructors.isEmpty(),
          element,
          "@%s does not support application classes (or super classes) with @Inject constructors. "
              + "Found %s with @Inject constructors %s.",
          ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
          baseSuperclassElement,
          injectConstructors);

      baseSuperclassElement = MoreTypes.asTypeElement(baseSuperclassElement.getSuperclass());
    }

    // We check this last because if the base type is a @HiltAndroidApp we'd accidentally fail
    // with this message instead of the one above when the superclass hasn't yet been generated.
    ProcessorErrors.checkState(
        Processors.isAssignableFrom(baseElement, ClassNames.APPLICATION),
        element,
        "@%s value should be an instance of %s. Found: %s",
        ClassNames.CUSTOM_TEST_APPLICATION.simpleName(),
        ClassNames.APPLICATION,
        baseElement);

    return baseElement;
  }
}
