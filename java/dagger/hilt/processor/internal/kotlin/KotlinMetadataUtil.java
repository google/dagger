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

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreElements.isType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static javax.lang.model.element.Modifier.STATIC;
import static kotlinx.metadata.Flag.Class.IS_COMPANION_OBJECT;
import static kotlinx.metadata.Flag.Class.IS_OBJECT;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import kotlin.Metadata;
import kotlinx.metadata.Flag;

/** Utility class for interacting with Kotlin Metadata. */
public final class KotlinMetadataUtil {

  private final KotlinMetadataFactory metadataFactory;

  @Inject
  KotlinMetadataUtil(KotlinMetadataFactory metadataFactory) {
    this.metadataFactory = metadataFactory;
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(Element element) {
    return isAnnotationPresent(closestEnclosingTypeElement(element), Metadata.class);
  }

  /**
   * Returns {@code true} if this element has the Kotlin Metadata annotation or if it is enclosed in
   * an element that does.
   */
  public boolean hasMetadata(XElement element) {
    return hasMetadata(toJavac(element));
  }

  /**
   * Returns the annotations on the given {@code element} annotated with {@code annotationName}.
   *
   * <p>Note: If the given {@code element} is a non-static field this method will return annotations
   * on both the backing field and the associated synthetic property (if one exists).
   */
  public ImmutableList<AnnotationMirror> getAnnotationsAnnotatedWith(
      Element element, ClassName annotationName) {
    return getAnnotations(element).stream()
        .filter(annotation -> hasAnnotation(annotation, annotationName))
        .collect(toImmutableList());
  }

  /**
   * Returns the annotations on the given {@code element} that match the {@code annotationName}.
   *
   * <p>Note: If the given {@code element} is a non-static field this method will return
   * annotations on both the backing field and the associated synthetic property (if one exists).
   */
  private ImmutableList<AnnotationMirror> getAnnotations(Element element) {
    // Currently, we avoid trying to get annotations from properties on object class's (i.e.
    // properties with static jvm backing fields) due to issues explained in CL/336150864.
    // Instead, just return the annotations on the element.
    if (element.getKind() != ElementKind.FIELD || element.getModifiers().contains(STATIC)) {
      return ImmutableList.copyOf(element.getAnnotationMirrors());
    }
    // Dedupe any annotation that appears on both the field and the property
    return Stream.concat(
            element.getAnnotationMirrors().stream(),
            getSyntheticPropertyAnnotations(asVariable(element)).stream())
        .map(AnnotationMirrors.equivalence()::wrap)
        .distinct()
        .map(Equivalence.Wrapper::get)
        .collect(toImmutableList());
  }

  private boolean hasAnnotation(AnnotationMirror annotation, ClassName annotationName) {
    return MoreElements.isAnnotationPresent(
        annotation.getAnnotationType().asElement(),
        annotationName.canonicalName());
  }

  /**
   * Returns the synthetic annotations of a Kotlin property.
   *
   * <p>Note that this method only looks for additional annotations in the synthetic property
   * method, if any, of a Kotlin property and not for annotations in its backing field.
   */
  private ImmutableList<AnnotationMirror> getSyntheticPropertyAnnotations(VariableElement field) {
    return hasMetadata(field)
        ? metadataFactory
            .create(field)
            .getSyntheticAnnotationMethod(field)
            .map(Element::getAnnotationMirrors)
            .map(annotations -> ImmutableList.<AnnotationMirror>copyOf(annotations))
            .orElse(ImmutableList.<AnnotationMirror>of())
        : ImmutableList.of();
  }

  /**
   * Returns {@code true} if the synthetic method for annotations is missing. This can occur when
   * the Kotlin metadata of the property reports that it contains a synthetic method for annotations
   * but such method is not found since it is synthetic and ignored by the processor.
   */
  public boolean isMissingSyntheticPropertyForAnnotations(VariableElement fieldElement) {
    return metadataFactory.create(fieldElement).isMissingSyntheticAnnotationMethod(fieldElement);
  }

  /** Returns {@code true} if this type element is a Kotlin Object. */
  public boolean isObjectClass(TypeElement typeElement) {
    return hasMetadata(typeElement)
        && metadataFactory.create(typeElement).classMetadata().flags(IS_OBJECT);
  }

  /* Returns {@code true} if this type element is a Kotlin Companion Object. */
  public boolean isCompanionObjectClass(XTypeElement typeElement) {
    return isCompanionObjectClass(toJavac(typeElement));
  }

  /* Returns {@code true} if this type element is a Kotlin Companion Object. */
  public boolean isCompanionObjectClass(TypeElement typeElement) {
    return hasMetadata(typeElement)
        && metadataFactory.create(typeElement).classMetadata().flags(IS_COMPANION_OBJECT);
  }

  /** Returns {@code true} if this type element is a Kotlin object or companion object. */
  public boolean isObjectOrCompanionObjectClass(TypeElement typeElement) {
    return isObjectClass(typeElement) || isCompanionObjectClass(typeElement);
  }

  /**
   * Returns {@code true} if the given type element was declared {@code internal} in its Kotlin
   * source.
   */
  public boolean isVisibilityInternal(TypeElement type) {
    return hasMetadata(type)
        && metadataFactory.create(type).classMetadata().flags(Flag.IS_INTERNAL);
  }

  /**
   * Returns {@code true} if the given executable element was declared {@code internal} in its
   * Kotlin source.
   */
  public boolean isVisibilityInternal(ExecutableElement method) {
    return hasMetadata(method)
        && metadataFactory.create(method).getFunctionMetadata(method).flags(Flag.IS_INTERNAL);
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  public Optional<ExecutableElement> getPropertyGetter(VariableElement fieldElement) {
    return metadataFactory.create(fieldElement).getPropertyGetter(fieldElement);
  }

  public Optional<XMethodElement> getPropertyGetter(XFieldElement fieldElement) {
    return getPropertyGetter(toJavac(fieldElement))
        .map(element -> (XMethodElement) toXProcessing(element, getProcessingEnv(fieldElement)));
  }

  public boolean containsConstructorWithDefaultParam(XTypeElement typeElement) {
    return hasMetadata(typeElement)
        && metadataFactory.create(typeElement).containsConstructorWithDefaultParam();
  }

  /** Returns the argument or the closest enclosing element that is a {@link TypeElement}. */
  static TypeElement closestEnclosingTypeElement(Element element) {
    Element current = element;
    while (current != null) {
      if (isType(current)) {
        return asType(current);
      }
      current = current.getEnclosingElement();
    }
    throw new IllegalStateException("There is no enclosing TypeElement for: " + element);
  }
}
