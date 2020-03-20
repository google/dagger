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

package dagger.hilt.processor.internal.root;

import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * The valid root types for TikTok and Sting applications.
 */
enum RootType {
    ROOT(ClassNames.GENERATE_COMPONENTS),

    TEST_ROOT(ClassNames.INTERNAL_TEST_ROOT);

  @SuppressWarnings("ImmutableEnumChecker")
  private final ClassName annotation;

  RootType(ClassName annotation) {
    this.annotation = annotation;
  }

  public boolean isTestRoot() {
    return this == TEST_ROOT;
  }

  public ClassName className() {
    return annotation;
  }

  public static RootType of(ProcessingEnvironment env, TypeElement element) {
    if (Processors.hasAnnotation(element, ClassNames.GENERATE_COMPONENTS)) {
      ProcessorErrors.checkState(
          Processors.hasAnnotation(element, ClassNames.ANDROID_ENTRY_POINT)
              || Processors.hasAnnotation(element, ClassNames.ANDROID_ROBOLECTRIC_ENTRY_POINT)
              || Processors.hasAnnotation(element, ClassNames.ANDROID_EMULATOR_ENTRY_POINT),
          element,
          "Classes annotated with @GenerateComponents must also be annotated with "
              + "@AndroidEntryPoint, @AndroidRobolectricEntryPoint, or "
              + "@AndroidEmulatorEntryPoint.");
      return Processors.hasAnnotation(element, ClassNames.ANDROID_ENTRY_POINT) ? ROOT : TEST_ROOT;
    } else if (Processors.hasAnnotation(element, ClassNames.INTERNAL_TEST_ROOT)) {
      return TEST_ROOT;
    }
    throw new IllegalStateException("Unknown root type: " + element);
  }
}
