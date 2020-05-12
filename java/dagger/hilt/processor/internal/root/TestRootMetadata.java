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
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Metadata class for {@code InternalTestRoot} annotated classes. */
@AutoValue
abstract class TestRootMetadata {

  /** Returns the {@link TypeElement} for the test class. */
  abstract TypeElement testElement();

  /** Returns the {@link TypeElement} for the base application. */
  abstract TypeElement baseElement();

  /** Returns the {@link ClassName} for the test class. */
  ClassName testName() {
    return ClassName.get(testElement());
  }

  /** Returns the {@link ClassName} for the base application. */
  ClassName baseAppName() {
    return ClassName.get(baseElement());
  }

  /** The name of the generated Hilt test application class for the given test name. */
  ClassName appName() {
    return Processors.append(Processors.getEnclosedClassName(testName()), "_Application");
  }

  /** The name of the generated Hilt test application class for the given test name. */
  ClassName testInjectorName() {
    return Processors.append(Processors.getEnclosedClassName(testName()), "_GeneratedInjector");
  }

  static TestRootMetadata of(ProcessingEnvironment env, Element element) {

    TypeElement testElement = MoreElements.asType(element);

    TypeElement baseElement =
        env.getElementUtils().getTypeElement(ClassNames.MULTI_DEX_APPLICATION.toString());
    ProcessorErrors.checkState(
        !Processors.hasAnnotation(element, ClassNames.ANDROID_ENTRY_POINT),
        element,
        "Tests cannot be annotated with @AndroidEntryPoint. Please use @HiltAndroidTest");

    ProcessorErrors.checkState(
        Processors.hasAnnotation(element, ClassNames.HILT_ANDROID_TEST),
        element,
        "Tests must be annotated with @HiltAndroidTest");

    return new AutoValue_TestRootMetadata(testElement, baseElement);
  }
}
