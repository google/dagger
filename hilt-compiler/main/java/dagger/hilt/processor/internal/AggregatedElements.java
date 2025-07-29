/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XAnnotations;
import java.util.Optional;

/** Utility class for aggregating metadata. */
public final class AggregatedElements {

  /** Returns the class name of the proxy or {@link Optional#empty()} if a proxy is not needed. */
  public static Optional<ClassName> aggregatedElementProxyName(XTypeElement aggregatedElement) {
    if (aggregatedElement.isPublic() && !aggregatedElement.isInternal()) {
      // Public aggregated elements do not have proxies.
      return Optional.empty();
    }
    ClassName name = aggregatedElement.getClassName();
    // To avoid going over the class name size limit, just prepend a single character.
    return Optional.of(name.peerClass("_" + name.simpleName()));
  }

  /** Returns back the set of input {@code aggregatedElements} with all proxies unwrapped. */
  public static ImmutableSet<XTypeElement> unwrapProxies(
      ImmutableCollection<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(AggregatedElements::unwrapProxy)
        .collect(toImmutableSet());
  }

  private static XTypeElement unwrapProxy(XTypeElement element) {
    return element.hasAnnotation(ClassNames.AGGREGATED_ELEMENT_PROXY)
        ? XAnnotations.getAsTypeElement(
            element.getAnnotation(ClassNames.AGGREGATED_ELEMENT_PROXY), "value")
        : element;
  }

  /** Returns all aggregated elements in the aggregating package after validating them. */
  public static ImmutableSet<XTypeElement> from(
      String aggregatingPackage, ClassName aggregatingAnnotation, XProcessingEnv env) {
    ImmutableSet<XTypeElement> aggregatedElements =
        env.getTypeElementsFromPackage(aggregatingPackage).stream()
            // We're only interested in returning the original deps here. Proxies will be generated
            // (if needed) and swapped just before generating @ComponentTreeDeps.
            .filter(element -> !element.hasAnnotation(ClassNames.AGGREGATED_ELEMENT_PROXY))
            .collect(toImmutableSet());

    for (XTypeElement aggregatedElement : aggregatedElements) {
      ProcessorErrors.checkState(
          aggregatedElement.hasAnnotation(aggregatingAnnotation),
          aggregatedElement,
          "Expected element, %s, to be annotated with @%s, but only found: %s.",
          aggregatedElement.getName(),
          aggregatingAnnotation,
          aggregatedElement.getAllAnnotations().stream()
              .map(XAnnotations::toStableString)
              .collect(toImmutableList()));
    }

    return aggregatedElements;
  }

  private AggregatedElements() {}
}
