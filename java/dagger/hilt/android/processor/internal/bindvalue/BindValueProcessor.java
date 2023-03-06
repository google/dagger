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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.extension.DaggerStreams;
import java.util.Collection;
import java.util.Map;
import javax.annotation.processing.Processor;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
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

  private final ListMultimap<TypeElement, Element> testRootMap = ArrayListMultimap.create();

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
    processEach(toJavac(annotation), toJavac(element));
  }

  private void processEach(TypeElement annotation, Element element) {
    ClassName annotationClassName = ClassName.get(annotation);
    Element enclosingElement = element.getEnclosingElement();
    // Restrict BindValue to the direct test class (e.g. not allowed in a base test class) because
    // otherwise generated BindValue modules from the base class will not associate with the
    // correct test class. This would make the modules apply globally which would be a weird
    // difference since just moving a declaration to the parent would change whether the module is
    // limited to the test that declares it to global.
    ProcessorErrors.checkState(
        enclosingElement.getKind() == ElementKind.CLASS
            && (Processors.hasAnnotation(enclosingElement, ClassNames.HILT_ANDROID_TEST)
            ),
        enclosingElement,
        "@%s can only be used within a class annotated with "
            + "@HiltAndroidTest. Found: %s",
        annotationClassName.simpleName(),
        enclosingElement);

    testRootMap.put(MoreElements.asType(enclosingElement), element);
  }

  @Override
  public void postRoundProcess(XRoundEnv roundEnv) throws Exception {
    // Generate a module for each testing class with a @BindValue field.
    for (Map.Entry<TypeElement, Collection<Element>> e : testRootMap.asMap().entrySet()) {
      BindValueMetadata metadata = BindValueMetadata.create(e.getKey(), e.getValue());
      new BindValueGenerator(getProcessingEnv(), metadata).generate();
    }
  }

  static ImmutableList<ClassName> getBindValueAnnotations(Element element) {
    ImmutableList.Builder<ClassName> builder = ImmutableList.builder();
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      TypeName tn = AnnotationSpec.get(annotation).type;
      if (SUPPORTED_ANNOTATIONS.contains(tn)) {
        builder.add((ClassName) tn); // the cast is checked by .contains()
      }
    }
    return builder.build();
  }
}
