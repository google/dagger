/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.android.processor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.difference;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.squareup.javapoet.ClassName;
import java.util.Map;
import java.util.Set;

/**
 * A {@link XProcessingStep} that processes one element at a time and defers any for which {@link
 * TypeNotPresentException} is thrown.
 */
abstract class BaseProcessingStep implements XProcessingStep {
  @Override
  public final ImmutableSet<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  // Subclass must ensure all annotated targets are of valid type.
  @Override
  public ImmutableSet<XElement> process(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    ImmutableSet.Builder<XElement> deferredElements = ImmutableSet.builder();
    inverse(elementsByAnnotation)
        .forEach(
            (element, annotations) -> {
              try {
                process(element, annotations);
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
   * @param annotations the subset of {@link XProcessingStep#annotations()} that annotate {@code
   *     element}
   */
  protected abstract void process(XElement element, ImmutableSet<ClassName> annotations);

  private ImmutableMap<XElement, ImmutableSet<ClassName>> inverse(
      Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    ImmutableMap<String, ClassName> annotationClassNames =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, className -> className));
    checkState(
        annotationClassNames.keySet().containsAll(elementsByAnnotation.keySet()),
        "Unexpected annotations for %s: %s",
        this.getClass().getCanonicalName(),
        difference(elementsByAnnotation.keySet(), annotationClassNames.keySet()));

    ImmutableSetMultimap.Builder<XElement, ClassName> builder = ImmutableSetMultimap.builder();
    elementsByAnnotation.forEach(
        (annotationName, elementSet) ->
            elementSet.forEach(
                element -> builder.put(element, annotationClassNames.get(annotationName))));

    return ImmutableMap.copyOf(Maps.transformValues(builder.build().asMap(), ImmutableSet::copyOf));
  }

  /** Returns the set of annotations processed by this processing step. */
  protected abstract Set<ClassName> annotationClassNames();
}
