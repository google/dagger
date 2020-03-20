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

package dagger.hilt.android.processor.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.lang.model.util.Types;

/** More utility methods for types. */
public final class MoreTypes {
  private MoreTypes() {}

  /**
   * If the received mirror represents a declared type or an array of declared types, this returns
   * the represented declared type. Otherwise throws an IllegalStateException.
   */
  public static DeclaredType getDeclaredType(TypeMirror type) {
    return type.accept(
        new SimpleTypeVisitor7<DeclaredType, Void>() {
          @Override public DeclaredType visitArray(ArrayType type, Void unused) {
            return getDeclaredType(type.getComponentType());
          }

          @Override public DeclaredType visitDeclared(DeclaredType type, Void unused) {
            return type;
          }

          @Override public DeclaredType visitError(ErrorType type, Void unused) {
            return type;
          }

          @Override public DeclaredType defaultAction(TypeMirror type, Void unused) {
            throw new IllegalStateException("Unhandled type: " + type);
          }
        }, null /* the Void accumulator */);
  }

  /** Returns the TypeElement corresponding to a TypeMirror. */
  public static TypeElement asTypeElement(TypeMirror type) {
    return asTypeElement(getDeclaredType(type));
  }

  /** Returns the TypeElement corresponding to a DeclaredType. */
  public static TypeElement asTypeElement(DeclaredType type) {
    return (TypeElement) type.asElement();
  }

  /**
   * Returns a {@link ExecutableType} if the {@link TypeMirror} represents an executable type such
   * as a method, constructor, or initializer or throws an {@link IllegalArgumentException}.
   */
  public static ExecutableType asExecutable(TypeMirror maybeExecutableType) {
    return maybeExecutableType.accept(ExecutableTypeVisitor.INSTANCE, null);
  }

  private static final class ExecutableTypeVisitor extends CastingTypeVisitor<ExecutableType> {
    private static final ExecutableTypeVisitor INSTANCE = new ExecutableTypeVisitor();

    ExecutableTypeVisitor() {
      super("executable type");
    }

    @Override
    public ExecutableType visitExecutable(ExecutableType type, Void ignore) {
      return type;
    }
  }

  private abstract static class CastingTypeVisitor<T> extends SimpleTypeVisitor7<T, Void> {
    private final String label;

    CastingTypeVisitor(String label) {
      this.label = label;
    }

    @Override
    protected T defaultAction(TypeMirror e, Void v) {
      throw new IllegalArgumentException(e + " does not represent a " + label);
    }
  }

  /**
   * Returns the first matching method, if one exists (starting with classElement, then searching
   * each sub classes).
   */
  public static Optional<ExecutableElement> findInheritedMethod(
      Types types, TypeElement classElement, ExecutableElement method) {
    Optional<ExecutableElement> match = Optional.empty();
    while (!match.isPresent() && !classElement.asType().getKind().equals(TypeKind.NONE)) {
      match = findMethod(types, classElement, method);
      classElement = MoreTypes.asTypeElement(classElement.getSuperclass());
    }
    return match;
  }

  /** Returns a method with a matching signature in classElement if one exists. */
  public static Optional<ExecutableElement> findMethod(
      Types types, TypeElement classElement, ExecutableElement method) {
    ExecutableType methodType = asExecutable(method.asType());
    Set<ExecutableElement> matchingMethods =
        findMethods(classElement, method.getSimpleName().toString())
            .stream()
            .filter(clsMethod -> types.isSubsignature(asExecutable(clsMethod.asType()), methodType))
            .collect(Collectors.toSet());

    Preconditions.checkState(
        matchingMethods.size() <= 1,
        "Found multiple methods with matching signature in class %s: %s",
        classElement,
        matchingMethods);

    return matchingMethods.size() == 1
        ? Optional.of(Iterables.getOnlyElement(matchingMethods))
        : Optional.empty();
  }

  /** Returns methods with a matching name in classElement. */
  public static Set<ExecutableElement> findMethods(TypeElement classElement, String name) {
    return ElementFilter.methodsIn(classElement.getEnclosedElements())
        .stream()
        .filter(clsMethod -> clsMethod.getSimpleName().contentEquals(name))
        .collect(Collectors.toSet());
  }
}
