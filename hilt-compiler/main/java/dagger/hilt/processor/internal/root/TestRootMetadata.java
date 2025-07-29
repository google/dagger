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

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;

/** Metadata class for {@code InternalTestRoot} annotated classes. */
@AutoValue
abstract class TestRootMetadata {

  /** Returns the {@link TypeElement} for the test class. */
  abstract XTypeElement testElement();

  /** Returns the {@link TypeElement} for the base application. */
  abstract XTypeElement baseElement();

  /** Returns the {@link ClassName} for the test class. */
  ClassName testName() {
    return testElement().getClassName();
  }

  /** Returns the {@link ClassName} for the base application. */
  ClassName baseAppName() {
    return baseElement().getClassName();
  }

  /** The name of the generated Hilt test application class for the given test name. */
  ClassName appName() {
    return Processors.append(Processors.getEnclosedClassName(testName()), "_Application");
  }

  /** The name of the generated Hilt test application class for the given test name. */
  ClassName testInjectorName() {
    return Processors.append(Processors.getEnclosedClassName(testName()), "_GeneratedInjector");
  }

  /**
   * Returns either the SkipTestInjection annotation or the first annotation that was annotated
   * with SkipTestInjection, if present.
   */
  Optional<XAnnotation> skipTestInjectionAnnotation() {
    XAnnotation skipTestAnnotation = testElement().getAnnotation(ClassNames.SKIP_TEST_INJECTION);
    if (skipTestAnnotation != null) {
      return Optional.of(skipTestAnnotation);
    }

    Set<XAnnotation> annotatedAnnotations = testElement().getAnnotationsAnnotatedWith(
        ClassNames.SKIP_TEST_INJECTION);
    if (!annotatedAnnotations.isEmpty()) {
      // Just return the first annotation that skips test injection if there are multiple since
      // at this point it doesn't really matter and the specific annotation is only really useful
      // for communicating back to the user.
      return Optional.of(annotatedAnnotations.iterator().next());
    }

    return Optional.empty();
  }

  static TestRootMetadata of(XProcessingEnv env, XElement element) {

    XTypeElement testElement = XElements.asTypeElement(element);
    XTypeElement baseElement = env.requireTypeElement(ClassNames.MULTI_DEX_APPLICATION);

    ProcessorErrors.checkState(
        !element.hasAnnotation(ClassNames.ANDROID_ENTRY_POINT),
        element,
        "Tests cannot be annotated with @AndroidEntryPoint. Please use @HiltAndroidTest");

    ProcessorErrors.checkState(
        element.hasAnnotation(ClassNames.HILT_ANDROID_TEST),
        element,
        "Tests must be annotated with @HiltAndroidTest");

    return new AutoValue_TestRootMetadata(testElement, baseElement);
  }
}
