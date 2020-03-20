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

package dagger.hilt.android.processor.internal.testing;

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
public abstract class InternalTestRootMetadata {

  /** Returns the type of test root (emulator or robolectric). */
  public abstract ClassName testType();

  /** Returns the {@link TypeElement} for the test class. */
  public abstract TypeElement testElement();

  /** Returns the {@link TypeElement} for the base application. */
  public abstract TypeElement baseElement();

  /** Returns the {@link ClassName} for the test class. */
  public ClassName testName() {
    return ClassName.get(testElement());
  }

  /** Returns the {@link ClassName} for the base application. */
  public ClassName baseName() {
    return ClassName.get(baseElement());
  }

  /** The name of the generated Hilt test application class for the given test name. */
  public ClassName appName() {
    return Processors.append(Processors.getEnclosedClassName(testName()), "_Application");
  }

  /** The name of the generated entry point for injecting the test. */
  public ClassName injectorClassName() {
    return Processors.append(Processors.getEnclosedClassName(appName()), "_Injector");
  }

  public static InternalTestRootMetadata of(ProcessingEnvironment env, Element element) {

    ProcessorErrors.checkState(
        Processors.hasAnnotation(element, ClassNames.GENERATE_COMPONENTS),
        element,
        "Expected element to be annotated with @GenerateComponents.");

    TypeElement testElement = MoreElements.asType(element);

    TypeElement baseElement =
        env.getElementUtils().getTypeElement(ClassNames.MULTI_DEX_APPLICATION.toString());

    ProcessorErrors.checkState(
        !Processors.hasAnnotation(element, ClassNames.ANDROID_ENTRY_POINT),
        element,
        "Tests cannot be annotated with @AndroidEntryPoint. Please use either "
            + "@AndroidRobolectricEntryPoint or @AndroidEmulatorEntryPoint");

    ProcessorErrors.checkState(
        Processors.hasAnnotation(element, ClassNames.ANDROID_ROBOLECTRIC_ENTRY_POINT)
            || Processors.hasAnnotation(element, ClassNames.ANDROID_EMULATOR_ENTRY_POINT),
        element,
        "Test must be annotated with either @AndroidRobolectricEntryPoint or "
            + "@AndroidEmulatorEntryPoint");

    ClassName testType =
        Processors.hasAnnotation(element, ClassNames.ANDROID_ROBOLECTRIC_ENTRY_POINT)
            ? ClassNames.INTERNAL_TEST_ROOT_TYPE_ROBOLECTRIC
            : ClassNames.INTERNAL_TEST_ROOT_TYPE_EMULATOR;

    return new AutoValue_InternalTestRootMetadata(testType, testElement, baseElement);
  }
}
