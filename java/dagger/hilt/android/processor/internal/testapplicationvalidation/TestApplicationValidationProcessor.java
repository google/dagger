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

package dagger.hilt.android.processor.internal.testapplicationvalidation;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * Validates {@link dagger.hilt.android.testing.MergedTestApplication} usages.
 *
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class TestApplicationValidationProcessor extends BaseProcessor {
  private final List<ClassName> mergedTestApplications = new ArrayList<>();
  private boolean foundHiltAndroidTest;

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        ClassNames.HILT_ANDROID_TEST.toString(),
        ClassNames.MERGED_TEST_APPLICATION.toString());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    if (ClassName.get(annotation).equals(ClassNames.HILT_ANDROID_TEST)) {
      foundHiltAndroidTest = true;
      return;
    }

    ProcessorErrors.checkState(
        MoreElements.isType(element),
        element,
        "@%s can only be used on a class or interface, but found: %s",
        annotation.getSimpleName(),
        element);

    if (ClassName.get(annotation).equals(ClassNames.MERGED_TEST_APPLICATION)) {
      ProcessorErrors.checkState(
          !Processors.hasAnnotation(element, ClassNames.HILT_ANDROID_TEST),
          element,
          "@%s should not be used on Hilt tests annotated with @%s, but found: %s",
          annotation.getSimpleName(),
          ClassNames.HILT_ANDROID_TEST.simpleName(),
          element);
      mergedTestApplications.add(ClassName.get(MoreElements.asType(element)));
      ProcessorErrors.checkState(
          mergedTestApplications.size() == 1,
          element,
          "Cannot have more than one usage of @%s. Found: %s.",
          ClassNames.MERGED_TEST_APPLICATION.simpleName(),
          mergedTestApplications);
    } else {
      throw new AssertionError("Unexpected annotation: " + annotation + " on element " + element);
    }

  }

  @Override
  public void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
    if (roundEnv.processingOver() && !mergedTestApplications.isEmpty()) {
      ProcessorErrors.checkState(
          foundHiltAndroidTest,
          mergedTestApplications.stream()
              .map(className -> getElementUtils().getTypeElement(className.toString()))
              .collect(toImmutableList()),
          "Cannot use @%s in a build without a test. Found: %s",
          ClassNames.MERGED_TEST_APPLICATION.simpleName(),
          mergedTestApplications);
    }
  }
}
