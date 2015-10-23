/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Utilities for handling types in annotation processors
 */
final class Util {
  /**
   * Returns the {@code V} type for a {@link Map} type like Map<K, Provider<V>>} if the map
   * includes such a construction
   */
  public static TypeMirror getProvidedValueTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    return MoreTypes.asDeclared(mapType.getTypeArguments().get(1)).getTypeArguments().get(0);
  }

  // TODO(cgruber): Consider an object that holds and exposes the various parts of a Map type.
  /**
   * returns the value type for a {@link Map} type like Map<K, V>}.
   */
  public static TypeMirror getValueTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return mapArgs.get(1);
  }

  /**
   * Returns the key type for a {@link Map} type like Map<K, Provider<V>>}
   */
  public static DeclaredType getKeyTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return MoreTypes.asDeclared(mapArgs.get(0));
  }

  /**
   * Wraps an {@link Optional} of a type in an {@code Optional} of a {@link Wrapper} for that type.
   */
  static <T> Optional<Equivalence.Wrapper<T>> wrapOptionalInEquivalence(
      Equivalence<T> equivalence, Optional<T> optional) {
    return optional.isPresent()
        ? Optional.of(equivalence.wrap(optional.get()))
        : Optional.<Equivalence.Wrapper<T>>absent();
  }

  /**
   * Unwraps an {@link Optional} of a {@link Wrapper} into an {@code Optional} of the underlying
   * type.
   */
  static <T> Optional<T> unwrapOptionalEquivalence(
      Optional<Equivalence.Wrapper<T>> wrappedOptional) {
    return wrappedOptional.isPresent()
        ? Optional.of(wrappedOptional.get().get())
        : Optional.<T>absent();
  }

  private static boolean requiresEnclosingInstance(TypeElement typeElement) {
    switch (typeElement.getNestingKind()) {
      case TOP_LEVEL:
        return false;
      case MEMBER:
        return !typeElement.getModifiers().contains(STATIC);
      case ANONYMOUS:
      case LOCAL:
        return true;
      default:
        throw new AssertionError("TypeElement cannot have nesting kind: "
            + typeElement.getNestingKind());
    }
  }

  /**
   * Returns true if and only if a component can instantiate new instances (typically of a module)
   * rather than requiring that they be passed.
   */
  static boolean componentCanMakeNewInstances(TypeElement typeElement) {
    switch (typeElement.getKind()) {
      case CLASS:
        break;
      case ENUM:
      case ANNOTATION_TYPE:
      case INTERFACE:
        return false;
      default:
        throw new AssertionError("TypeElement cannot have kind: " + typeElement.getKind());
    }

    if (typeElement.getModifiers().contains(ABSTRACT)) {
      return false;
    }

    if (requiresEnclosingInstance(typeElement)) {
      return false;
    }

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR)
          && ((ExecutableElement) enclosed).getParameters().isEmpty()
          && !enclosed.getModifiers().contains(PRIVATE)) {
        return true;
      }
    }

    // TODO(gak): still need checks for visibility

    return false;
  }

  /*
   * Borrowed from AutoValue and slightly modified. TODO(gak): reconcile and put in autocommon.
   */
  private static ImmutableList<ExecutableElement> findLocalAndInheritedMethods(Elements elements,
      TypeElement type) {
    List<ExecutableElement> methods = Lists.newArrayList();
    TypeElement objectType = elements.getTypeElement(Object.class.getName());
    findLocalAndInheritedMethodsRecursive(objectType, elements, type, methods);
    return ImmutableList.copyOf(methods);
  }

  private static void findLocalAndInheritedMethodsRecursive(
      TypeElement objectType,
      Elements elements,
      TypeElement type,
      List<ExecutableElement> methods) {
    if (objectType.equals(type)) {
      return;
    }

    for (TypeMirror superInterface : type.getInterfaces()) {
      findLocalAndInheritedMethodsRecursive(objectType,
          elements, MoreElements.asType(MoreTypes.asElement(superInterface)), methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      findLocalAndInheritedMethodsRecursive(objectType,
          elements, MoreElements.asType(MoreTypes.asElement(type.getSuperclass())), methods);
    }
    // Add each method of this class, and in so doing remove any inherited method it overrides.
    // This algorithm is quadratic in the number of methods but it's hard to see how to improve
    // that while still using Elements.overrides.
    List<ExecutableElement> theseMethods = ElementFilter.methodsIn(type.getEnclosedElements());
    for (ExecutableElement method : theseMethods) {
      if (!method.getModifiers().contains(Modifier.PRIVATE)) {
        boolean alreadySeen = false;
        for (Iterator<ExecutableElement> methodIter = methods.iterator(); methodIter.hasNext();) {
          ExecutableElement otherMethod = methodIter.next();
          if (elements.overrides(method, otherMethod, type)) {
            methodIter.remove();
          } else if (method.getSimpleName().equals(otherMethod.getSimpleName())
              && method.getParameters().equals(otherMethod.getParameters())) {
            // If we inherit this method on more than one path, we don't want to add it twice.
            alreadySeen = true;
          }
        }
        if (!alreadySeen) {
          methods.add(method);
        }
      }
    }
  }

  /*
   * Borrowed from AutoValue and slightly modified. TODO(gak): reconcile and put in autocommon.
   */
  static ImmutableSet<ExecutableElement> getUnimplementedMethods(
      Elements elements, TypeElement type) {
    ImmutableSet.Builder<ExecutableElement> unimplementedMethods = ImmutableSet.builder();
    List<ExecutableElement> methods = findLocalAndInheritedMethods(elements, type);
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        unimplementedMethods.add(method);
      }
    }
    return unimplementedMethods.build();
  }

  private Util() {}
}
