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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.hasModifiers;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.asList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.Formatter.formatArgumentInList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.Traverser;
import dagger.Reusable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor8;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.Types;

/** Extension of {@link Elements} that adds Dagger-specific methods. */
@Reusable
final class DaggerElements implements Elements {

  private final Elements elements;
  private final Types types;

  @VisibleForTesting
  DaggerElements(Elements elements, Types types) {
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  DaggerElements(ProcessingEnvironment processingEnv) {
    this(processingEnv.getElementUtils(), processingEnv.getTypeUtils());
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to {@code enclosed} or recursively encloses
   * it.
   */
  static boolean elementEncloses(TypeElement encloser, Element enclosed) {
    return Iterables.contains(GET_ENCLOSED_ELEMENTS.breadthFirst(encloser), enclosed);
  }

  private static final Traverser<Element> GET_ENCLOSED_ELEMENTS =
      Traverser.forTree(Element::getEnclosedElements);

  ImmutableSet<ExecutableElement> getUnimplementedMethods(TypeElement type) {
    return FluentIterable.from(getLocalAndInheritedMethods(type, types, elements))
        .filter(hasModifiers(ABSTRACT))
        .toSet();
  }

  /** Returns the type element for a class. */
  TypeElement getTypeElement(Class<?> clazz) {
    return getTypeElement(clazz.getCanonicalName());
  }

  @Override
  public TypeElement getTypeElement(CharSequence name) {
    return elements.getTypeElement(name);
  }

  /**
   * Returns a useful string form for an element.
   *
   * <p>Elements directly enclosed by a type are preceded by the enclosing type's qualified name.
   *
   * <p>Parameters are given with their enclosing executable, with other parameters elided.
   */
  static String elementToString(Element element) {
    return element.accept(ELEMENT_TO_STRING, null);
  }

  private static final ElementVisitor<String, Void> ELEMENT_TO_STRING =
      new ElementKindVisitor8<String, Void>() {
        @Override
        public String visitExecutable(ExecutableElement executableElement, Void aVoid) {
          return enclosingTypeAndMemberName(executableElement)
              .append(
                  executableElement
                      .getParameters()
                      .stream()
                      .map(parameter -> parameter.asType().toString())
                      .collect(joining(", ", "(", ")")))
              .toString();
        }

        @Override
        public String visitVariableAsParameter(VariableElement parameter, Void aVoid) {
          ExecutableElement methodOrConstructor = asExecutable(parameter.getEnclosingElement());
          return enclosingTypeAndMemberName(methodOrConstructor)
              .append('(')
              .append(
                  formatArgumentInList(
                      methodOrConstructor.getParameters().indexOf(parameter),
                      methodOrConstructor.getParameters().size(),
                      parameter.getSimpleName()))
              .append(')')
              .toString();
        }

        @Override
        public String visitVariableAsField(VariableElement field, Void aVoid) {
          return enclosingTypeAndMemberName(field).toString();
        }

        @Override
        public String visitType(TypeElement type, Void aVoid) {
          return type.getQualifiedName().toString();
        }

        @Override
        protected String defaultAction(Element element, Void aVoid) {
          throw new UnsupportedOperationException(
              "Can't determine string for " + element.getKind() + " element " + element);
        }

        private StringBuilder enclosingTypeAndMemberName(Element element) {
          return new StringBuilder()
              .append(element.getEnclosingElement().accept(this, null))
              .append('.')
              .append(element.getSimpleName());
        }
      };

  /** Returns the argument or the closest enclosing element that is a {@link TypeElement}. */
  static TypeElement closestEnclosingTypeElement(Element element) {
    return element.accept(CLOSEST_ENCLOSING_TYPE_ELEMENT, null);
  }

  private static final ElementVisitor<TypeElement, Void> CLOSEST_ENCLOSING_TYPE_ELEMENT =
      new SimpleElementVisitor8<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element element, Void p) {
          return element.getEnclosingElement().accept(this, null);
        }

        @Override
        public TypeElement visitType(TypeElement type, Void p) {
          return type;
        }
      };

  /**
   * Compares elements according to their declaration order among siblings. Only valid to compare
   * elements enclosed by the same parent.
   */
  static final Comparator<Element> DECLARATION_ORDER =
      comparing(element -> element.getEnclosingElement().getEnclosedElements().indexOf(element));

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose
   * {@linkplain AnnotationMirror#getAnnotationType() annotation type} has the same canonical name
   * as any of that of {@code annotationClasses}.
   */
  static boolean isAnyAnnotationPresent(
      Element element, Iterable<? extends Class<? extends Annotation>> annotationClasses) {
    for (Class<? extends Annotation> annotation : annotationClasses) {
      if (MoreElements.isAnnotationPresent(element, annotation)) {
        return true;
      }
    }
    return false;
  }

  @SafeVarargs
  static boolean isAnyAnnotationPresent(
      Element element,
      Class<? extends Annotation> first,
      Class<? extends Annotation>... otherAnnotations) {
    return isAnyAnnotationPresent(element, asList(first, otherAnnotations));
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} is equivalent to {@code annotationType}.
   */
  static boolean isAnnotationPresent(Element element, TypeMirror annotationType) {
    return element
        .getAnnotationMirrors()
        .stream()
        .map(AnnotationMirror::getAnnotationType)
        .anyMatch(candidate -> MoreTypes.equivalence().equivalent(candidate, annotationType));
  }

  /**
   * Returns the annotation present on {@code element} whose type is {@code first} or within {@code
   * rest}, checking each annotation type in order.
   */
  @SafeVarargs
  static Optional<AnnotationMirror> getAnyAnnotation(
      Element element, Class<? extends Annotation> first, Class<? extends Annotation>... rest) {
    return getAnyAnnotation(element, asList(first, rest));
  }

  /**
   * Returns the annotation present on {@code element} whose type is in {@code annotations},
   * checking each annotation type in order.
   */
  static Optional<AnnotationMirror> getAnyAnnotation(
      Element element, Collection<? extends Class<? extends Annotation>> annotations) {
    return element
        .getAnnotationMirrors()
        .stream()
        .filter(hasAnnotationTypeIn(annotations))
        .map((AnnotationMirror a) -> a) // Avoid returning Optional<? extends AnnotationMirror>.
        .findFirst();
  }

  /** Returns the annotations present on {@code element} of all types. */
  @SafeVarargs
  static ImmutableSet<AnnotationMirror> getAllAnnotations(
      Element element, Class<? extends Annotation> first, Class<? extends Annotation>... rest) {
    return element
        .getAnnotationMirrors()
        .stream()
        .filter(hasAnnotationTypeIn(asList(first, rest)))
        .collect(toImmutableSet());
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    return Optional.ofNullable(MoreElements.getAnnotationMirror(element, annotationClass).orNull());
  }

  private static Predicate<AnnotationMirror> hasAnnotationTypeIn(
      Collection<? extends Class<? extends Annotation>> annotations) {
    Set<String> annotationClassNames =
        annotations.stream().map(Class::getCanonicalName).collect(toSet());
    return annotation ->
        annotationClassNames.contains(
            MoreTypes.asTypeElement(annotation.getAnnotationType()).getQualifiedName().toString());
  }

  static ImmutableSet<String> suppressedWarnings(Element element) {
    SuppressWarnings suppressedWarnings = element.getAnnotation(SuppressWarnings.class);
    if (suppressedWarnings == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(suppressedWarnings.value());
  }

  /**
   * Invokes {@link Elements#getTypeElement(CharSequence)}, throwing {@link TypeNotPresentException}
   * if it is not accessible in the current compilation.
   */
  TypeElement checkTypePresent(String typeName) {
    TypeElement type = elements.getTypeElement(typeName);
    if (type == null) {
      throw new TypeNotPresentException(typeName, null);
    }
    return type;
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
  public Name getName(CharSequence cs) {
    return elements.getName(cs);
  }

  @Override
  public boolean isFunctionalInterface(TypeElement type) {
    return elements.isFunctionalInterface(type);
  }
}
