/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {
  private final KotlinMetadataFactory metadataFactory;

  @Inject
  KotlinMetadataUtil(KotlinMetadataFactory metadataFactory) {
    this.metadataFactory = metadataFactory;
  }

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(XElement element) {
    return closestEnclosingTypeElement(element).hasAnnotation(XTypeNames.KOTLIN_METADATA);
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  public ImmutableSet<XAnnotation> getSyntheticPropertyAnnotations(
      XFieldElement fieldElement, XClassName annotationType) {
    return metadataFactory
        .create(fieldElement)
        .getSyntheticAnnotationMethod(fieldElement)
        .map(methodElement -> methodElement.getAnnotationsAnnotatedWith(annotationType))
        .map(ImmutableSet::copyOf)
        .orElse(ImmutableSet.of());
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(XFieldElement fieldElement) {
    return metadataFactory.create(fieldElement).isMissingSyntheticAnnotationMethod(fieldElement);
  }

  public Optional<XMethodElement> getPropertyGetter(XFieldElement fieldElement) {
    return metadataFactory.create(fieldElement).getPropertyGetter(fieldElement);
  }

  /**
   * Returns a map mapping all method signatures within the given class element, including methods
   * that it inherits from its ancestors, to their method names.
   */
  public ImmutableMap<String, String> getAllMethodNamesBySignature(XTypeElement element) {
    checkState(
        hasMetadata(element), "Can not call getAllMethodNamesBySignature for non-Kotlin class");
    return metadataFactory.create(element)
        .classMetadata()
        .getFunctionsBySignature().values().stream()
        .collect(
            toImmutableMap(
                FunctionMetadata::getSignature,
                FunctionMetadata::getName)); // SUPPRESS_GET_NAME_CHECK
  }
}
