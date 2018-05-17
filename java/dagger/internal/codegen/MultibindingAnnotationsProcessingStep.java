/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen;

import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerElements.isAnyAnnotationPresent;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Binds;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * Processing step that verifies that {@link IntoSet}, {@link ElementsIntoSet} and {@link IntoMap}
 * are not present on invalid elements.
 */
final class MultibindingAnnotationsProcessingStep implements ProcessingStep {

  private static final ImmutableSet<Class<? extends Annotation>> VALID_BINDING_ANNOTATIONS =
      ImmutableSet.of(Provides.class, Produces.class, Binds.class);

  private final Messager messager;

  @Inject
  MultibindingAnnotationsProcessingStep(Messager messager) {
    this.messager = messager;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(IntoSet.class, ElementsIntoSet.class, IntoMap.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Entry<Class<? extends Annotation>, Element> entry : elementsByAnnotation.entries()) {
      Element element = entry.getValue();
      if (!isAnyAnnotationPresent(element, VALID_BINDING_ANNOTATIONS)) {
        AnnotationMirror annotation = getAnnotationMirror(entry.getValue(), entry.getKey()).get();
        messager.printMessage(
            Kind.ERROR,
            "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods",
            element,
            annotation);
      }
    }
    return ImmutableSet.of();
  }
}
