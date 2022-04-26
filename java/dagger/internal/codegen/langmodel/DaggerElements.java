/*
 * Copyright (C) 2013 The Dagger Authors.
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

package dagger.internal.codegen.langmodel;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.asList;
import static java.util.Comparator.comparing;

import androidx.room.compiler.processing.XElement;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.Reusable;
import dagger.internal.codegen.base.ClearableCache;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Extension of {@link Elements} that adds Dagger-specific methods. */
@Reusable
public final class DaggerElements implements Elements, ClearableCache {
  private final Map<TypeElement, ImmutableSet<ExecutableElement>> getLocalAndInheritedMethodsCache =
      new HashMap<>();
  private final Elements elements;
  private final Types types;

  public DaggerElements(Elements elements, Types types) {
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to or recursively encloses {@code enclosed}.
   */
  public static boolean transitivelyEncloses(XElement encloser, XElement enclosed) {
    return transitivelyEncloses(toJavac(encloser), toJavac(enclosed));
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to or recursively encloses {@code enclosed}.
   */
  public static boolean transitivelyEncloses(Element encloser, Element enclosed) {
    Element current = enclosed;
    while (current != null) {
      if (current.equals(encloser)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  public ImmutableSet<ExecutableElement> getLocalAndInheritedMethods(TypeElement type) {
    return getLocalAndInheritedMethodsCache.computeIfAbsent(
        type, k -> MoreElements.getLocalAndInheritedMethods(type, types, elements));
  }

  @Override
  public TypeElement getTypeElement(CharSequence name) {
    return elements.getTypeElement(name);
  }

  /** Returns the type element for a class name. */
  public TypeElement getTypeElement(ClassName className) {
    return getTypeElement(className.canonicalName());
  }

  /** Returns the argument or the closest enclosing element that is a {@link TypeElement}. */
  public static TypeElement closestEnclosingTypeElement(Element element) {
    Element current = element;
    while (current != null) {
      if (MoreElements.isType(current)) {
        return MoreElements.asType(current);
      }
      current = current.getEnclosingElement();
    }
    throw new IllegalStateException("There is no enclosing TypeElement for: " + element);
  }

  /**
   * Compares elements according to their declaration order among siblings. Only valid to compare
   * elements enclosed by the same parent.
   */
  public static final Comparator<Element> DECLARATION_ORDER =
      comparing(element -> siblings(element).indexOf(element));

  // For parameter elements, element.getEnclosingElement().getEnclosedElements() is empty. So
  // instead look at the parameter list of the enclosing executable.
  private static List<? extends Element> siblings(Element element) {
    return element.getKind().equals(ElementKind.PARAMETER)
        ? asExecutable(element.getEnclosingElement()).getParameters()
        : element.getEnclosingElement().getEnclosedElements();
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} has the same canonical name as any of
   * that of {@code annotationClasses}.
   */
  public static boolean isAnyAnnotationPresent(
      Element element, Iterable<ClassName> annotationClasses) {
    for (ClassName annotation : annotationClasses) {
      if (isAnnotationPresent(element, annotation)) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  public static boolean isAnyAnnotationPresent(
      Element element, ClassName first, ClassName... otherAnnotations) {
    return isAnyAnnotationPresent(element, asList(first, otherAnnotations));
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@link
   * AnnotationMirror#getAnnotationType() annotation type} has the same canonical name as that of
   * {@code annotationClass}. This method is a safer alternative to calling {@link
   * Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   */
  public static boolean isAnnotationPresent(Element element, ClassName annotationName) {
    return getAnnotationMirror(element, annotationName).isPresent();
  }

  // Note: This is similar to auto-common's MoreElements except using ClassName rather than Class.
  // TODO(bcorso): Contribute a String version to auto-common's MoreElements?
  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, ClassName annotationName) {
    String annotationClassName = annotationName.canonicalName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement =
          MoreElements.asType(annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.empty();
  }

  @Override
  public PackageElement getPackageElement(CharSequence name) {
    return elements.getPackageElement(name);
  }

  @Override
  public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
      AnnotationMirror a) {
    return elements.getElementValuesWithDefaults(a);
  }

  /** Returns a map of annotation values keyed by attribute name. */
  public Map<String, ? extends AnnotationValue> getElementValuesWithDefaultsByName(
      AnnotationMirror a) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    getElementValuesWithDefaults(a).forEach((k, v) -> builder.put(k.getSimpleName().toString(), v));
    return builder.build();
  }

  @Override
  public String getDocComment(Element e) {
    return elements.getDocComment(e);
  }

  @Override
  public boolean isDeprecated(Element e) {
    return elements.isDeprecated(e);
  }

  @Override
  public Name getBinaryName(TypeElement type) {
    return elements.getBinaryName(type);
  }

  @Override
  public PackageElement getPackageOf(Element type) {
    return elements.getPackageOf(type);
  }

  @Override
  public List<? extends Element> getAllMembers(TypeElement type) {
    return elements.getAllMembers(type);
  }

  @Override
  public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
    return elements.getAllAnnotationMirrors(e);
  }

  @Override
  public boolean hides(Element hider, Element hidden) {
    return elements.hides(hider, hidden);
  }

  @Override
  public boolean overrides(
      ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
    return elements.overrides(overrider, overridden, type);
  }

  @Override
  public String getConstantExpression(Object value) {
    return elements.getConstantExpression(value);
  }

  @Override
  public void printElements(Writer w, Element... elements) {
    this.elements.printElements(w, elements);
  }

  @Override
  public Name getName(CharSequence cs) { // SUPPRESS_GET_NAME_CHECK: This is not xprocessing usage.
    return elements.getName(cs); // SUPPRESS_GET_NAME_CHECK: This is not xprocessing usage.
  }

  @Override
  public boolean isFunctionalInterface(TypeElement type) {
    return elements.isFunctionalInterface(type);
  }

  @Override
  public void clearCache() {
    getLocalAndInheritedMethodsCache.clear();
  }
}
