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

package dagger.hilt.processor.internal.root;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Stores the metadata for a custom base test application. */
@AutoValue
abstract class MergedTestApplicationMetadata {
  /** Returns the annotated element. */
  abstract TypeElement element();

  /** Returns the name of the base application. */
  abstract ClassName baseAppName();

  /** Returns the name of the generated application */
  ClassName appName() {
    return Processors.append(
        Processors.getEnclosedClassName(ClassName.get(element())), "_Application");
  }

  static MergedTestApplicationMetadata of(Element element, Elements elements) {
    Preconditions.checkState(
        Processors.hasAnnotation(element, ClassNames.MERGED_TEST_APPLICATION),
        "@%s should only be used on classes or interfaces but found: %s",
        ClassNames.MERGED_TEST_APPLICATION.simpleName(),
        element);

    ProcessorErrors.checkState(
        MoreElements.isType(element),
        element,
        "@%s should only be used on classes or interfaces but found: %s",
        ClassNames.MERGED_TEST_APPLICATION.simpleName(),
        element);

    // TODO(user): enable custom base applications on individual tests.
    ProcessorErrors.checkState(
        !Processors.hasAnnotation(element, ClassNames.HILT_ANDROID_TEST),
        element,
        "@%s cannot be used with @%s.",
        ClassNames.MERGED_TEST_APPLICATION,
        ClassNames.HILT_ANDROID_TEST);

    TypeElement baseAppElement =
        Processors.getAnnotationClassValue(
            elements,
            Processors.getAnnotationMirror(element, ClassNames.MERGED_TEST_APPLICATION),
            "value");

    return new AutoValue_MergedTestApplicationMetadata(
        MoreElements.asType(element), ClassName.get(baseAppElement));
  }
}
