/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.hilt.processor.internal.kotlin;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Optional;
import java.util.stream.Stream;
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
    return XElements.closestEnclosingTypeElement(element).hasAnnotation(ClassNames.KOTLIN_METADATA);
  }

  // TODO(kuanyingchou): Consider replacing it with `XAnnotated.getAnnotationsAnnotatedWith()`
  //  once b/278077018 is resolved.
  /**
   * Returns the annotations on the given {@code element} annotated with {@code annotationName}.
   *
   * <p>Note: If the given {@code element} is a non-static field this method will return annotations
   * on both the backing field and the associated synthetic property (if one exists).
   */
  public ImmutableList<XAnnotation> getAnnotationsAnnotatedWith(
      XElement element, ClassName annotationName) {
    return getAnnotations(element).stream()
        .filter(annotation -> annotation.getTypeElement().hasAnnotation(annotationName))
        .collect(toImmutableList());
  }

  /**
   * Returns the annotations on the given {@code element} that match the {@code annotationName}.
   *
   * <p>Note: If the given {@code element} is a non-static field this method will return annotations
   * on both the backing field and the associated synthetic property (if one exists).
   */
  private ImmutableList<XAnnotation> getAnnotations(XElement element) {
    // Currently, we avoid trying to get annotations from properties on object class's (i.e.
    // properties with static jvm backing fields) due to issues explained in CL/336150864.
    // Instead, just return the annotations on the element.
    if (!XElementKt.isField(element) || XElements.isStatic(element)) {
      return ImmutableList.copyOf(element.getAllAnnotations());
    }
    // Dedupe any annotation that appears on both the field and the property
    return Stream.concat(
            element.getAllAnnotations().stream(),
            getSyntheticPropertyAnnotations(XElements.asField(element)).stream())
        .map(XAnnotations.equivalence()::wrap)
        .distinct()
        .map(Equivalence.Wrapper::get)
        .collect(toImmutableList());
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  private ImmutableList<XAnnotation> getSyntheticPropertyAnnotations(XFieldElement field) {
    return hasMetadata(field)
        ? metadataFactory
            .create(field)
            .getSyntheticAnnotationMethod(field)
            .map(XExecutableElement::getAllAnnotations)
            .map(ImmutableList::copyOf)
            .orElse(ImmutableList.<XAnnotation>of())
        : ImmutableList.of();
  }

  public Optional<XMethodElement> getPropertyGetter(XFieldElement fieldElement) {
    return metadataFactory.create(fieldElement).getPropertyGetter(fieldElement);
  }

  public boolean containsConstructorWithDefaultParam(XTypeElement typeElement) {
    return hasMetadata(typeElement)
        && metadataFactory.create(typeElement).containsConstructorWithDefaultParam();
  }
}
