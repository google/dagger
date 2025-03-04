/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.XTypeKt.isArray;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;
import static dagger.internal.codegen.xprocessing.XElements.isPackage;
import static dagger.internal.codegen.xprocessing.XElements.isPrivate;
import static dagger.internal.codegen.xprocessing.XElements.isPublic;
import static dagger.internal.codegen.xprocessing.XTypeElements.isNested;
import static dagger.internal.codegen.xprocessing.XTypes.asArray;
import static dagger.internal.codegen.xprocessing.XTypes.getEnclosingType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isNoType;
import static dagger.internal.codegen.xprocessing.XTypes.isNullType;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeVariable;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.TypeName;
import java.util.Optional;

/**
 * Utility methods for determining whether a {@link XType} or an {@link XElement} is accessible
 * given the rules outlined in <a
 * href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6">section 6.6 of the
 * Java Language Specification</a>.
 *
 * <p>This class only provides an approximation for accessibility. It does not always yield the same
 * result as the compiler, but will always err on the side of declaring something inaccessible. This
 * ensures that using this class will never result in generating code that will not compile.
 */
public final class Accessibility {
  /** Returns true if the given type can be referenced from any package. */
  public static boolean isTypePubliclyAccessible(XType type) {
    return isTypeAccessibleFrom(type, Optional.empty());
  }

  /** Returns true if the given type can be referenced from code in the given package. */
  public static boolean isTypeAccessibleFrom(XType type, String packageName) {
    return isTypeAccessibleFrom(type, Optional.of(packageName));
  }

  private static boolean isTypeAccessibleFrom(XType type, Optional<String> packageName) {
    if (isNoType(type) || isPrimitive(type) || isNullType(type) || isTypeVariable(type)) {
      return true;
    } else if (isArray(type)) {
      return isTypeAccessibleFrom(asArray(type).getComponentType(), packageName);
    } else if (isDeclared(type)) {
      XType enclosingType = getEnclosingType(type);
      if (enclosingType != null && !isTypeAccessibleFrom(enclosingType, packageName)) {
        return false;
      }
      if (!isElementAccessibleFrom(type.getTypeElement(), packageName)) {
        return false;
      }
      return type.getTypeArguments().stream()
          .allMatch(typeArgument -> isTypeAccessibleFrom(typeArgument, packageName));
    } else if (isWildcard(type)) {
      return type.extendsBound() == null || isTypeAccessibleFrom(type.extendsBound(), packageName);
    }
    throw new AssertionError(String.format("%s should not be checked for accessibility", type));
  }

  /** Returns true if the given element can be referenced from any package. */
  public static boolean isElementPubliclyAccessible(XElement element) {
    return isElementAccessibleFrom(element, Optional.empty());
  }

  /** Returns true if the given element can be referenced from other code in its own package. */
  public static boolean isElementAccessibleFromOwnPackage(XElement element) {
    return isElementAccessibleFrom(
        element, Optional.of(element.getClosestMemberContainer().getClassName().packageName()));
  }

  /** Returns true if the given element can be referenced from code in the given package. */
  // TODO(gak): account for protected
  // TODO(bcorso): account for kotlin srcs (package-private doesn't exist, internal does exist).
  public static boolean isElementAccessibleFrom(XElement element, String packageName) {
    return isElementAccessibleFrom(element, Optional.of(packageName));
  }

  private static boolean isElementAccessibleFrom(XElement element, Optional<String> packageName) {
    if (isPackage(element)) {
      return true;
    } else if (isTypeElement(element)) {
      return isNested(asTypeElement(element))
          ? accessibleMember(element, packageName)
          : accessibleModifiers(element, packageName);
    } else if (isExecutable(element) || isField(element)) {
      return accessibleMember(element, packageName);
    }
    throw new AssertionError();
  }

  private static boolean accessibleMember(XElement element, Optional<String> packageName) {
    return isElementAccessibleFrom(element.getEnclosingElement(), packageName)
        && accessibleModifiers(element, packageName);
  }

  private static boolean accessibleModifiers(XElement element, Optional<String> packageName) {
    if (isPublic(element)) {
      return true;
    } else if (isPrivate(element)) {
      return false;
    }
    return packageName.isPresent()
        && element
            .getClosestMemberContainer()
            .getClassName()
            .packageName()
            .contentEquals(packageName.get());
  }

  /** Returns true if the raw type of {@code type} is accessible from the given package. */
  public static boolean isRawTypeAccessible(XType type, String requestingPackage) {
    return isDeclared(type)
        ? isElementAccessibleFrom(type.getTypeElement(), requestingPackage)
        : isTypeAccessibleFrom(type, requestingPackage);
  }

  /** Returns true if the raw type of {@code type} is accessible from any package. */
  public static boolean isRawTypePubliclyAccessible(XType type) {
    return isDeclared(type)
        ? isElementPubliclyAccessible(type.getTypeElement())
        : isTypePubliclyAccessible(type);
  }

  /**
   * Returns an accessible type in {@code requestingClass}'s package based on {@code type}:
   *
   * <ul>
   *   <li>If {@code type} is accessible from the package, returns it.
   *   <li>If not, but {@code type}'s raw type is accessible from the package, returns the raw type.
   *   <li>Otherwise returns {@link Object}.
   * </ul>
   */
  public static TypeName accessibleTypeName(
      XType type, XClassName requestingClass, XProcessingEnv processingEnv) {
    if (isTypeAccessibleFrom(type, requestingClass.getPackageName())) {
      return type.getTypeName();
    } else if (isDeclared(type) && isRawTypeAccessible(type, requestingClass.getPackageName())) {
      return type.getRawType().getTypeName();
    } else {
      return TypeName.OBJECT;
    }
  }

  private Accessibility() {}
}
