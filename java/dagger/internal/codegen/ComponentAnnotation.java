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

package dagger.internal.codegen;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreTypes.asTypeElements;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnyAnnotation;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * A {@code @Component}, {@code @Subcomponent}, {@code @ProductionComponent}, or
 * {@code @ProductionSubcomponent} annotation, or a {@code @Module} or {@code @ProducerModule}
 * annotation that is being treated as a component annotation when validating full binding graphs
 * for modules.
 */
abstract class ComponentAnnotation {
  /** The root component annotation types. */
  private static final ImmutableSet<Class<? extends Annotation>> ROOT_COMPONENT_ANNOTATIONS =
     ImmutableSet.of(Component.class, ProductionComponent.class);

  /** The subcomponent annotation types. */
  private static final ImmutableSet<Class<? extends Annotation>> SUBCOMPONENT_ANNOTATIONS =
     ImmutableSet.of(Subcomponent.class, ProductionSubcomponent.class);

  /** All component annotation types. */
  private static final ImmutableSet<Class<? extends Annotation>> ALL_COMPONENT_ANNOTATIONS =
     ImmutableSet.<Class<? extends Annotation>>builder()
         .addAll(ROOT_COMPONENT_ANNOTATIONS)
         .addAll(SUBCOMPONENT_ANNOTATIONS)
         .build();

  /** The annotation itself. */
  abstract AnnotationMirror annotation();

  /** The simple name of the annotation type. */
  String simpleName() {
    return MoreAnnotationMirrors.simpleName(annotation()).toString();
  }

  /**
   * Returns {@code true} if the annotation is a {@code @Subcomponent} or
   * {@code @ProductionSubcomponent}.
   */
  abstract boolean isSubcomponent();

  /**
   * Returns {@code true} if the annotation is a {@code @ProductionComponent},
   * {@code @ProductionSubcomponent}, or {@code @ProducerModule}.
   */
  abstract boolean isProduction();

  /**
   * Returns {@code true} if the annotation is a real component annotation and not a module
   * annotation.
   */
  abstract boolean isRealComponent();

  /** The values listed as {@code dependencies}. */
  abstract ImmutableList<AnnotationValue> dependencyValues();

  /** The types listed as {@code dependencies}. */
  ImmutableList<TypeMirror> dependencyTypes() {
    return dependencyValues().stream().map(MoreAnnotationValues::asType).collect(toImmutableList());
  }

  /**
   * The types listed as {@code dependencies}.
   *
   * @throws IllegalArgumentException if any of {@link #dependencyTypes()} are error types
   */
  ImmutableList<TypeElement> dependencies() {
    return asTypeElements(dependencyTypes()).asList();
  }

  /** The values listed as {@code modules}. */
  abstract ImmutableList<AnnotationValue> moduleValues();

  /** The types listed as {@code modules}. */
  ImmutableList<TypeMirror> moduleTypes() {
    return moduleValues().stream().map(MoreAnnotationValues::asType).collect(toImmutableList());
  }

  /**
   * The types listed as {@code modules}.
   *
   * @throws IllegalArgumentException if any of {@link #moduleTypes()} are error types
   */
  ImmutableSet<TypeElement> modules() {
    return asTypeElements(moduleTypes());
  }

  protected final ImmutableList<AnnotationValue> getAnnotationValues(String parameterName) {
    return asAnnotationValues(getAnnotationValue(annotation(), parameterName));
  }

  /**
   * Returns an object representing a root component annotation, not a subcomponent annotation, if
   * one is present on {@code typeElement}.
   */
  static Optional<ComponentAnnotation> rootComponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, ROOT_COMPONENT_ANNOTATIONS);
  }

  /**
   * Returns an object representing a subcomponent annotation, if one is present on {@code
   * typeElement}.
   */
  static Optional<ComponentAnnotation> subcomponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, SUBCOMPONENT_ANNOTATIONS);
  }

  /**
   * Returns an object representing a root component or subcomponent annotation, if one is present
   * on {@code typeElement}.
   */
  static Optional<ComponentAnnotation> anyComponentAnnotation(TypeElement typeElement) {
    return anyComponentAnnotation(typeElement, ALL_COMPONENT_ANNOTATIONS);
  }

  private static Optional<ComponentAnnotation> anyComponentAnnotation(
      TypeElement typeElement, Collection<Class<? extends Annotation>> annotations) {
    return getAnyAnnotation(typeElement, annotations).map(ComponentAnnotation::componentAnnotation);
  }

  /** Returns {@code true} if the argument is a component annotation. */
  static boolean isComponentAnnotation(AnnotationMirror annotation) {
    return ALL_COMPONENT_ANNOTATIONS.stream()
        .anyMatch(annotationClass -> isTypeOf(annotationClass, annotation.getAnnotationType()));
  }

  /** Creates an object representing a component or subcomponent annotation. */
  static ComponentAnnotation componentAnnotation(AnnotationMirror annotation) {
    RealComponentAnnotation.Builder annotationBuilder =
        RealComponentAnnotation.builder().annotation(annotation);

    if (isTypeOf(Component.class, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(false).isSubcomponent(false).build();
    }
    if (isTypeOf(Subcomponent.class, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(false).isSubcomponent(true).build();
    }
    if (isTypeOf(ProductionComponent.class, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(true).isSubcomponent(false).build();
    }
    if (isTypeOf(ProductionSubcomponent.class, annotation.getAnnotationType())) {
      return annotationBuilder.isProduction(true).isSubcomponent(true).build();
    }
    throw new IllegalArgumentException(
        annotation
            + " must be a Component, Subcomponent, ProductionComponent, "
            + "or ProductionSubcomponent annotation");
  }

  /** Creates a fictional component annotation representing a module. */
  static ComponentAnnotation fromModuleAnnotation(ModuleAnnotation moduleAnnotation) {
    return new AutoValue_ComponentAnnotation_FictionalComponentAnnotation(moduleAnnotation);
  }

  /** The root component annotation types. */
  static ImmutableSet<Class<? extends Annotation>> rootComponentAnnotations() {
    return ROOT_COMPONENT_ANNOTATIONS;
  }

  /** The subcomponent annotation types. */
  static ImmutableSet<Class<? extends Annotation>> subcomponentAnnotations() {
    return SUBCOMPONENT_ANNOTATIONS;
  }

  /** All component annotation types. */
  static ImmutableSet<Class<? extends Annotation>> allComponentAnnotations() {
    return ALL_COMPONENT_ANNOTATIONS;
  }

  /**
   * An actual component annotation.
   *
   * @see FictionalComponentAnnotation
   */
  @AutoValue
  abstract static class RealComponentAnnotation extends ComponentAnnotation {

    @Override
    @Memoized
    ImmutableList<AnnotationValue> dependencyValues() {
      return isSubcomponent() ? ImmutableList.of() : getAnnotationValues("dependencies");
    }

    @Override
    @Memoized
    ImmutableList<TypeMirror> dependencyTypes() {
      return super.dependencyTypes();
    }

    @Override
    @Memoized
    ImmutableList<TypeElement> dependencies() {
      return super.dependencies();
    }

    @Override
    boolean isRealComponent() {
      return true;
    }

    @Override
    @Memoized
    ImmutableList<AnnotationValue> moduleValues() {
      return getAnnotationValues("modules");
    }

    @Override
    @Memoized
    ImmutableList<TypeMirror> moduleTypes() {
      return super.moduleTypes();
    }

    @Override
    @Memoized
    ImmutableSet<TypeElement> modules() {
      return super.modules();
    }

    static Builder builder() {
      return new AutoValue_ComponentAnnotation_RealComponentAnnotation.Builder();
    }

    @AutoValue.Builder
    interface Builder {
      Builder annotation(AnnotationMirror annotation);

      Builder isSubcomponent(boolean isSubcomponent);

      Builder isProduction(boolean isProduction);

      RealComponentAnnotation build();
    }
  }

  /**
   * A fictional component annotation used to represent modules or other collections of bindings as
   * a component.
   */
  @AutoValue
  abstract static class FictionalComponentAnnotation extends ComponentAnnotation {

    @Override
    AnnotationMirror annotation() {
      return moduleAnnotation().annotation();
    }

    @Override
    boolean isSubcomponent() {
      return false;
    }

    @Override
    boolean isProduction() {
      return moduleAnnotation().annotationClass().equals(ProducerModule.class);
    }

    @Override
    boolean isRealComponent() {
      return false;
    }

    @Override
    ImmutableList<AnnotationValue> dependencyValues() {
      return ImmutableList.of();
    }

    @Override
    ImmutableList<AnnotationValue> moduleValues() {
      return moduleAnnotation().includesAsAnnotationValues();
    }

    @Override
    @Memoized
    ImmutableList<TypeMirror> moduleTypes() {
      return super.moduleTypes();
    }

    @Override
    @Memoized
    ImmutableSet<TypeElement> modules() {
      return super.modules();
    }

    abstract ModuleAnnotation moduleAnnotation();
  }
}
