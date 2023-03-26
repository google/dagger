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

package dagger.hilt.android.processor.internal.bindvalue;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.service.AutoService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Collection;
import java.util.Map;
import javax.annotation.processing.Processor;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Provides a test's @BindValue fields to the SINGLETON component. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class BindValueProcessor extends BaseProcessor {

  private static final ImmutableSet<ClassName> SUPPORTED_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(BindValueMetadata.BIND_VALUE_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_VALUE_INTO_SET_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_ELEMENTS_INTO_SET_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_VALUE_INTO_MAP_ANNOTATIONS)
          .build();

  private final ListMultimap<XTypeElement, XElement> testRootMap = ArrayListMultimap.create();

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return SUPPORTED_ANNOTATIONS.stream()
        .map(TypeName::toString)
        .collect(DaggerStreams.toImmutableSet());
  }

  @Override
  protected void preRoundProcess(XRoundEnv roundEnv) {
    testRootMap.clear();
  }

  @Override
  public void processEach(XTypeElement annotation, XElement element) {
    ClassName annotationClassName = annotation.getClassName();
    XElement enclosingElement = element.getEnclosingElement();
    // Restrict BindValue to the direct test class (e.g. not allowed in a base test class) because
    // otherwise generated BindValue modules from the base class will not associate with the
    // correct test class. This would make the modules apply globally which would be a weird
    // difference since just moving a declaration to the parent would change whether the module is
    // limited to the test that declares it to global.
    ProcessorErrors.checkState(
        isTypeElement(enclosingElement)
            && asTypeElement(enclosingElement).isClass()
            && (enclosingElement.hasAnnotation(ClassNames.HILT_ANDROID_TEST)
            ),
        enclosingElement,
        "@%s can only be used within a class annotated with "
            + "@HiltAndroidTest. Found: %s",
        annotationClassName.simpleName(),
        XElements.toStableString(enclosingElement));

    testRootMap.put(asTypeElement(enclosingElement), element);
  }

  @Override
  public void postRoundProcess(XRoundEnv roundEnv) throws Exception {
    // Generate a module for each testing class with a @BindValue field.
    for (Map.Entry<XTypeElement, Collection<XElement>> e : testRootMap.asMap().entrySet()) {
      BindValueMetadata metadata = BindValueMetadata.create(e.getKey(), e.getValue());
      new BindValueGenerator(processingEnv(), metadata).generate();
    }
  }

  static ImmutableList<ClassName> getBindValueAnnotations(XElement element) {
    return element.getAllAnnotations().stream()
        .map(XAnnotation::getClassName)
        .filter(SUPPORTED_ANNOTATIONS::contains)
        .collect(toImmutableList());
  }
}
