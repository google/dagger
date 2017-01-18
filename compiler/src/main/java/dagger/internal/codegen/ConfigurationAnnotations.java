/*
 * Copyright (C) 2014 The Dagger Authors.
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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.DaggerElements.getAnyAnnotation;
import static dagger.internal.codegen.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeListValue;
import static dagger.internal.codegen.MoreAnnotationValues.asAnnotationValues;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProducerModule;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component}
 * and {@link Module}).
 *
 * @author Gregory Kick
 */
final class ConfigurationAnnotations {

  static Optional<AnnotationMirror> getComponentAnnotation(TypeElement component) {
    return getAnyAnnotation(component, Component.class, ProductionComponent.class);
  }

  static Optional<AnnotationMirror> getSubcomponentAnnotation(TypeElement subcomponent) {
    return getAnyAnnotation(subcomponent, Subcomponent.class, ProductionSubcomponent.class);
  }

  static Optional<AnnotationMirror> getComponentOrSubcomponentAnnotation(TypeElement type) {
    Optional<AnnotationMirror> componentAnnotation = getComponentAnnotation(type);
    if (componentAnnotation.isPresent()) {
      return componentAnnotation;
    }
    return getSubcomponentAnnotation(type);
  }

  static boolean isSubcomponent(Element element) {
    return isAnyAnnotationPresent(element, Subcomponent.class, ProductionSubcomponent.class);
  }

  static Optional<TypeElement> getSubcomponentBuilder(TypeElement subcomponent) {
    checkArgument(isSubcomponent(subcomponent));
    for (TypeElement nestedType : typesIn(subcomponent.getEnclosedElements())) {
      if (isSubcomponentBuilder(nestedType)) {
        return Optional.of(nestedType);
      }
    }
    return Optional.empty();
  }

  static boolean isSubcomponentBuilder(Element element) {
    return isAnyAnnotationPresent(
        element, Subcomponent.Builder.class, ProductionSubcomponent.Builder.class);
  }

  /**
   * Returns the annotation values for the modules directly installed into a component or included
   * in a module.
   *
   * @param annotatedType the component or module type
   * @param annotation the component or module annotation
   */
  static ImmutableList<AnnotationValue> getModules(
      TypeElement annotatedType, AnnotationMirror annotation) {
    if (ComponentDescriptor.Kind.forAnnotatedElement(annotatedType).isPresent()) {
      return asAnnotationValues(getAnnotationValue(annotation, MODULES_ATTRIBUTE));
    }
    if (ModuleDescriptor.Kind.forAnnotatedElement(annotatedType).isPresent()) {
      return asAnnotationValues(getAnnotationValue(annotation, INCLUDES_ATTRIBUTE));
    }
    throw new IllegalArgumentException(String.format("unsupported annotation: %s", annotation));
  }

  private static final String MODULES_ATTRIBUTE = "modules";

  static ImmutableList<TypeMirror> getComponentModules(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return getTypeListValue(componentAnnotation, MODULES_ATTRIBUTE);
  }

  private static final String DEPENDENCIES_ATTRIBUTE = "dependencies";

  static ImmutableList<TypeMirror> getComponentDependencies(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return getTypeListValue(componentAnnotation, DEPENDENCIES_ATTRIBUTE);
  }

  static Optional<AnnotationMirror> getModuleAnnotation(TypeElement moduleElement) {
    return getAnyAnnotation(moduleElement, Module.class, ProducerModule.class);
  }

  private static final String INCLUDES_ATTRIBUTE = "includes";

  static ImmutableList<TypeMirror> getModuleIncludes(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return getTypeListValue(moduleAnnotation, INCLUDES_ATTRIBUTE);
  }

  private static final String SUBCOMPONENTS_ATTRIBUTE = "subcomponents";

  static ImmutableList<TypeMirror> getModuleSubcomponents(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return getTypeListValue(moduleAnnotation, SUBCOMPONENTS_ATTRIBUTE);
  }

  private static final String INJECTS_ATTRIBUTE = "injects";

  static ImmutableList<TypeMirror> getModuleInjects(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return getTypeListValue(moduleAnnotation, INJECTS_ATTRIBUTE);
  }

  /** Returns the first type that specifies this' nullability, or empty if none. */
  static Optional<DeclaredType> getNullableType(Element element) {
    List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();
    for (AnnotationMirror mirror : mirrors) {
      if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
        return Optional.of(mirror.getAnnotationType());
      }
    }
    return Optional.empty();
  }

  static <T extends Element> void validateComponentDependencies(
      ValidationReport.Builder<T> report, Iterable<TypeMirror> types) {
    validateTypesAreDeclared(report, types, "component dependency");
    for (TypeMirror type : types) {
      if (getModuleAnnotation(MoreTypes.asTypeElement(type)).isPresent()) {
        report.addError(
            String.format("%s is a module, which cannot be a component dependency", type));
      }
    }
  }

  private static <T extends Element> void validateTypesAreDeclared(
      final ValidationReport.Builder<T> report, Iterable<TypeMirror> types, final String typeName) {
    for (TypeMirror type : types) {
      type.accept(new SimpleTypeVisitor6<Void, Void>(){
        @Override
        protected Void defaultAction(TypeMirror e, Void aVoid) {
          report.addError(String.format("%s is not a valid %s type", e, typeName));
          return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, Void aVoid) {
          // Declared types are valid
          return null;
        }
      }, null);
    }
  }

  /**
   * Returns the full set of modules transitively {@linkplain Module#includes included} from the
   * given seed modules.  If a module is malformed and a type listed in {@link Module#includes}
   * is not annotated with {@link Module}, it is ignored.
   *
   * @deprecated Use {@link ComponentDescriptor#transitiveModules}.
   */
  @Deprecated
  static ImmutableSet<TypeElement> getTransitiveModules(
      Types types, Elements elements, Iterable<TypeElement> seedModules) {
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    Queue<TypeElement> moduleQueue = new ArrayDeque<>();
    Iterables.addAll(moduleQueue, seedModules);
    Set<TypeElement> moduleElements = Sets.newLinkedHashSet();
    for (TypeElement moduleElement = moduleQueue.poll();
        moduleElement != null;
        moduleElement = moduleQueue.poll()) {
      Optional<AnnotationMirror> moduleMirror = getModuleAnnotation(moduleElement);
      if (moduleMirror.isPresent()) {
        ImmutableSet.Builder<TypeElement> moduleDependenciesBuilder = ImmutableSet.builder();
        moduleDependenciesBuilder.addAll(
            MoreTypes.asTypeElements(getModuleIncludes(moduleMirror.get())));
        // (note: we don't recurse on the parent class because we don't want the parent class as a
        // root that the component depends on, and also because we want the dependencies rooted
        // against this element, not the parent.)
        addIncludesFromSuperclasses(types, moduleElement, moduleDependenciesBuilder, objectType);
        ImmutableSet<TypeElement> moduleDependencies = moduleDependenciesBuilder.build();
        moduleElements.add(moduleElement);
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.contains(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
    }
    return ImmutableSet.copyOf(moduleElements);
  }

  /** Returns the enclosed elements annotated with the given annotation type. */
  static ImmutableList<DeclaredType> enclosedBuilders(TypeElement typeElement,
      final Class<? extends Annotation> annotation) {
    final ImmutableList.Builder<DeclaredType> builders = ImmutableList.builder();
    for (TypeElement element : typesIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, annotation)) {
        builders.add(MoreTypes.asDeclared(element.asType()));
      }
    }
    return builders.build();
  }

  /** Traverses includes from superclasses and adds them into the builder. */
  private static void addIncludesFromSuperclasses(Types types, TypeElement element,
      ImmutableSet.Builder<TypeElement> builder, TypeMirror objectType) {
    // Also add the superclass to the queue, in case any @Module definitions were on that.
    TypeMirror superclass = element.getSuperclass();
    while (!types.isSameType(objectType, superclass)
        && superclass.getKind().equals(TypeKind.DECLARED)) {
      element = MoreElements.asType(types.asElement(superclass));
      getModuleAnnotation(element)
          .ifPresent(
              moduleMirror -> {
                builder.addAll(MoreTypes.asTypeElements(getModuleIncludes(moduleMirror)));
              });
      superclass = element.getSuperclass();
    }
  }

  private ConfigurationAnnotations() {}
}
