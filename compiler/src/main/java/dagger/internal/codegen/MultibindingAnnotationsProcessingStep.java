/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ErrorMessages.MULTIBINDING_ANNOTATION_NOT_ON_PROVIDES_OR_PRODUCES;
import static dagger.internal.codegen.Util.hasAnnotationType;

/**
 * Processing step which verifies that {@link IntoSet @IntoSet}, {@link ElementsIntoSet
 * @ElementsIntoSet} and {@link IntoMap @IntoMap} are not present on invalid elements.
 */
final class MultibindingAnnotationsProcessingStep implements ProcessingStep {

  private final Messager messager;

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
      boolean onBindingMethod =
          FluentIterable.from(element.getAnnotationMirrors()).anyMatch(providesOrProducesMethod());
      if (!onBindingMethod) {
        AnnotationMirror annotation = getAnnotationMirror(entry.getValue(), entry.getKey()).get();
        messager.printMessage(
            Kind.ERROR, MULTIBINDING_ANNOTATION_NOT_ON_PROVIDES_OR_PRODUCES, element, annotation);
      }
    }
    return ImmutableSet.of();
  }

  private static Predicate<AnnotationMirror> providesOrProducesMethod() {
    return Predicates.or(hasAnnotationType(Provides.class), hasAnnotationType(Produces.class));
  }
}
