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

package dagger.hilt.android.processor.internal.custombasetestapplication;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

// TODO(b/155288571): Consider moving this processing into RootProcessor.
/** Processor for {@link dagger.hilt.android.testing.CustomBaseTestApplication}. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class CustomBaseTestApplicationProcessor extends BaseProcessor {
  private RoundEnvironment currRoundEnv;

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION.toString());
  }

  @Override
  public void preRoundProcess(RoundEnvironment roundEnv) {
    currRoundEnv = roundEnv;
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    ProcessorErrors.checkState(
        MoreElements.isType(element),
        element,
        "@%s should only be used on classes or interfaces but found: %s",
        annotation.getSimpleName(),
        element);

    TypeElement hiltTestAnnotation =
        getElementUtils().getTypeElement(ClassNames.HILT_ANDROID_TEST.toString());
    ProcessorErrors.checkState(
        hiltTestAnnotation != null
            && !currRoundEnv.getElementsAnnotatedWith(hiltTestAnnotation).isEmpty(),
        element,
        "@%s can only be used if there is an @%s within the same processing round.",
        AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION,
        ClassNames.HILT_ANDROID_TEST);

    // TODO(user): enable custom base applications on individual tests.
    ProcessorErrors.checkState(
        !Processors.hasAnnotation(element, ClassNames.HILT_ANDROID_TEST),
        element,
        "@%s cannot be used with @%s.",
        AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION,
        ClassNames.HILT_ANDROID_TEST);

    generate(MoreElements.asType(element));
  }

  private void generate(TypeElement element) throws IOException {
    TypeElement baseApplicationElement =
        Processors.getAnnotationClassValue(
            getElementUtils(),
            Processors.getAnnotationMirror(element, AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION),
            "value");

    TypeSpec.Builder generator =
        TypeSpec.classBuilder(Processors.getFullEnclosedName(element))
            .addOriginatingElement(element)
            .addJavadoc("Generated class for aggregating test applications. \n")
            .addAnnotation(
                AnnotationSpec.builder(AndroidClassNames.CUSTOM_BASE_TEST_APPLICATION_DATA)
                    .addMember("annotatedClass", "$T.class", element)
                    .addMember("baseApplicationClass", "$T.class", baseApplicationElement)
                    .build());

    Processors.addGeneratedAnnotation(generator, processingEnv, getClass());

    JavaFile.builder(CustomBaseTestApplications.AGGREGATING_PACKAGE, generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }
}
