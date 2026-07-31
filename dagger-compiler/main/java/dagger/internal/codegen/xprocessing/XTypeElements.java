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

package dagger.internal.codegen.xprocessing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.isPrivate;
import static java.util.stream.Collectors.joining;
import static kotlin.streams.jdk8.StreamsKt.asStream;

import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.processing.XHasModifiers;
import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XTypeElement;
import androidx.room3.compiler.processing.XTypeParameterElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import java.util.HashSet;
import java.util.Set;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XTypeElement} helper methods. */
public final class XTypeElements {
  private enum Visibility {
    PUBLIC,
    PRIVATE,
    OTHER;

    /** Returns the visibility of the given {@link XTypeElement}. */
    private static Visibility of(XTypeElement element) {
      checkNotNull(element);
      if (isPrivate(element)) {
        return Visibility.PRIVATE;
      } else if (element.isPublic()) {
        return Visibility.PUBLIC;
      } else {
        return Visibility.OTHER;
      }
    }
  }

  // TODO(bcorso): Consider XParameterizable interface to handle both methods and types.
  /** Returns the type arguments for the given type as a list of {@link TypeVariableName}. */
  public static ImmutableList<XTypeName> typeVariableNames(XTypeElement typeElement) {
    return typeElement.getTypeParameters().stream()
        .map(XTypeParameterElement::asTypeVariableName)
        .collect(toImmutableList());
  }

  /** Returns {@code true} if the given element is nested. */
  public static boolean isNested(XTypeElement typeElement) {
    return typeElement.getEnclosingTypeElement() != null;
  }

  /** Returns {@code true} if the given {@code type} has type parameters. */
  public static boolean hasTypeParameters(XTypeElement typeElement) {
    return !typeElement.getTypeParameters().isEmpty();
  }

  /** Returns all non-private, non-static, abstract methods in {@code type}. */
  public static ImmutableList<XMethodElement> getAllUnimplementedMethods(XTypeElement type) {
    return getAllNonPrivateInstanceMethods(type).stream()
        .filter(XHasModifiers::isAbstract)
        .collect(toImmutableList());
  }

  /** Returns all non-private, non-static methods in {@code type}. */
  public static ImmutableList<XMethodElement> getAllNonPrivateInstanceMethods(XTypeElement type) {
    return getAllMethods(type).stream()
        .filter(method -> !isPrivate(method) && !method.isStatic())
        .collect(toImmutableList());
  }

  // TODO(bcorso): rename this to getAllMethodsWithoutPrivate, since the private method declared
  // within this element is being filtered out. This doesn't mirror {@code
  // MoreElements#getAllMethods}'s behavior but have the same name, and can cause confusion to
  // developers.
  public static ImmutableList<XMethodElement> getAllMethods(XTypeElement type) {
    return asStream(type.getAllMethods())
        .filter(method -> isAccessibleFrom(method, type))
        .collect(toImmutableList());
  }

  public static ImmutableList<XMethodElement> getAllMethodsIncludingPrivate(XTypeElement type) {
    return asStream(type.getAllMethods()).collect(toImmutableList());
  }

  private static boolean isAccessibleFrom(XMethodElement method, XTypeElement type) {
    if (method.isPublic() || method.isProtected()) {
      return true;
    }
    if (isPrivate(method)) {
      return false;
    }
    return method
        .getClosestMemberContainer()
        .getClassName()
        .packageName()
        .equals(type.getClassName().packageName());
  }

  public static boolean isEffectivelyPublic(XTypeElement element) {
    return allVisibilities(element).stream()
        .allMatch(visibility -> visibility.equals(Visibility.PUBLIC));
  }

  public static boolean isEffectivelyPrivate(XTypeElement element) {
    return allVisibilities(element).contains(Visibility.PRIVATE);
  }

  public static boolean isJvmClass(XTypeElement element) {
    return element.isClass() || element.isKotlinObject() || element.isCompanionObject();
  }

  /**
   * Returns a list of visibilities containing visibility of the given element and the visibility of
   * its enclosing elements.
   */
  private static ImmutableSet<Visibility> allVisibilities(XTypeElement element) {
    checkNotNull(element);
    ImmutableSet.Builder<Visibility> visibilities = ImmutableSet.builder();
    XTypeElement currentElement = element;
    while (currentElement != null) {
      visibilities.add(Visibility.of(currentElement));
      currentElement = currentElement.getEnclosingTypeElement();
    }
    return visibilities.build();
  }

  /**
   * Returns a string representation of {@link XTypeElement} that is independent of the backend
   * (javac/ksp).
   *
   * <p>This method is similar to {@link XElements#toStableString(XElement)} and
   * {@link XTypes#toStableString(XType)}, but this string representation includes the type variables and
   * their bounds, e.g. {@code Foo<T extends Comparable<T>>}. This is useful for error messages that
   * need to reference the type variable bounds.
   */
  public static String toStableString(XTypeElement typeElement) {
    try {
      return toStableString(typeElement.getType().getTypeName(), new HashSet<>(), /* depth= */ 0);
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  private static String toStableString(TypeName typeName, Set<TypeName> visited, int depth) {
    if (typeName instanceof ClassName) {
      return ((ClassName) typeName).canonicalName();
    } else if (typeName instanceof ArrayTypeName) {
      return String.format(
          "%s[]", toStableString(((ArrayTypeName) typeName).componentType, visited, depth + 1));
    } else if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      return String.format(
          "%s<%s>",
          parameterizedTypeName.rawType,
          parameterizedTypeName.typeArguments.stream()
              .map(typeArgument -> toStableString(typeArgument, visited, depth + 1))
              // We purposely don't use a space after the comma to for backwards compatibility with
              // usages that depended on the previous TypeMirror#toString() implementation.
              .collect(joining(",")));
    } else if (typeName instanceof WildcardTypeName) {
      WildcardTypeName wildcardTypeName = (WildcardTypeName) typeName;
      // Wildcard types have exactly 1 upper bound.
      TypeName upperBound = getOnlyElement(wildcardTypeName.upperBounds);
      if (!upperBound.equals(TypeName.OBJECT)) {
        // Wildcards with non-Object upper bounds can't have lower bounds.
        checkState(wildcardTypeName.lowerBounds.isEmpty());
        return String.format("? extends %s", toStableString(upperBound, visited, depth + 1));
      }
      if (!wildcardTypeName.lowerBounds.isEmpty()) {
        // Wildcard types can have at most 1 lower bound.
        TypeName lowerBound = getOnlyElement(wildcardTypeName.lowerBounds);
        return String.format("? super %s", toStableString(lowerBound, visited, depth + 1));
      }
      // If the upper bound is Object and there is no lower bound then just use "?".
      return "?";
    } else if (typeName instanceof TypeVariableName) {
      // The idea here is that for an XTypeElement with type variables, we only want to include the
      // bounds in the definition, i.e. at depth == 1, and not every time the type variable is
      // referenced. For example, for `class Foo<T1 extends Bar, T2 extends List<T1>>`, we want the
      // bounds for `T2` to show up as `List<T1>` and not as `List<T1 extends Bar>`.
      TypeVariableName typeVariableName = (TypeVariableName) typeName;
      return typeVariableName.bounds.isEmpty() || depth != 1
          ? typeVariableName.name
          : String.format(
              "%s extends %s",
              typeVariableName.name,
              typeVariableName.bounds.stream()
                  .map(bound -> toStableString(bound, visited, depth + 1))
                  .collect(joining(" & ")));
    } else {
      // For all other types (e.g. primitive types) just use the TypeName's toString()
      return typeName.toString();
    }
  }

  private XTypeElements() {}
}
