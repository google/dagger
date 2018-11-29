/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.function.Function;
import javax.lang.model.element.Element;

/**
 * A {@link ProcessingStep} that processes one element at a time and defers any for which {@link
 * TypeNotPresentException} is thrown.
 */
// TODO(dpb): Contribute to auto-common.
abstract class TypeCheckingProcessingStep<E extends Element> implements ProcessingStep {
  private final Function<Element, E> downcaster;

  TypeCheckingProcessingStep(Function<Element, E> downcaster) {
    this.downcaster = checkNotNull(downcaster);
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> deferredElements = ImmutableSet.builder();
    ImmutableSetMultimap.copyOf(elementsByAnnotation)
        .inverse()
        .asMap()
        .forEach(
            (element, annotations) -> {
              try {
                process(downcaster.apply(element), ImmutableSet.copyOf(annotations));
              } catch (TypeNotPresentException e) {
                deferredElements.add(element);
              }
            });
    return deferredElements.build();
  }

  /**
   * Processes one element. If this method throws {@link TypeNotPresentException}, the element will
   * be deferred until the next round of processing.
   *
   * @param annotations the subset of {@link ProcessingStep#annotations()} that annotate {@code
   *     element}
   */
  protected abstract void process(E element, ImmutableSet<Class<? extends Annotation>> annotations);
}
