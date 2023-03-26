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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

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
      ImmutableSet<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(AggregatedElements::unwrapProxy)
        .collect(toImmutableSet());
  }

  private static XTypeElement unwrapProxy(XTypeElement element) {
    return element.hasAnnotation(ClassNames.AGGREGATED_ELEMENT_PROXY)
        ? Processors.getAnnotationClassValue(
            element.getAnnotation(ClassNames.AGGREGATED_ELEMENT_PROXY), "value")
        : element;
  }

  // TODO(kuanyingchou): Migrate this once we have suitable APIs for packages in XProcessing.
  /** Returns all aggregated elements in the aggregating package after validating them. */
  private static ImmutableSet<TypeElement> from(
      String aggregatingPackage, ClassName aggregatingAnnotation, Elements elements) {
    PackageElement packageElement = elements.getPackageElement(aggregatingPackage);

    if (packageElement == null) {
      return ImmutableSet.of();
    }

    ImmutableSet<TypeElement> aggregatedElements =
        packageElement.getEnclosedElements().stream()
            .map(MoreElements::asType)
            // We're only interested in returning the original deps here. Proxies will be generated
            // (if needed) and swapped just before generating @ComponentTreeDeps.
            .filter(
                element -> !Processors.hasAnnotation(element, ClassNames.AGGREGATED_ELEMENT_PROXY))
            .collect(toImmutableSet());

    ProcessorErrors.checkState(
        !aggregatedElements.isEmpty(),
        packageElement,
        "No dependencies found. Did you remove code in package %s?",
        packageElement);

    for (TypeElement aggregatedElement : aggregatedElements) {
      ProcessorErrors.checkState(
          Processors.hasAnnotation(aggregatedElement, aggregatingAnnotation),
          aggregatedElement,
          "Expected element, %s, to be annotated with @%s, but only found: %s.",
          aggregatedElement.getSimpleName(),
          aggregatingAnnotation,
          aggregatedElement.getAnnotationMirrors());
    }

    return aggregatedElements;
  }

  /** Returns all aggregated elements in the aggregating package after validating them. */
  public static ImmutableSet<XTypeElement> from(
      String aggregatingPackage, ClassName aggregatingAnnotation, XProcessingEnv env) {
    return from(aggregatingPackage, aggregatingAnnotation, toJavac(env).getElementUtils()).stream()
        .map(element -> toXProcessing(element, env))
        .collect(toImmutableSet());
  }

  private AggregatedElements() {}
}
