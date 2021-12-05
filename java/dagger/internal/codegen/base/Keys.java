/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static dagger.internal.codegen.base.ComponentAnnotation.allComponentAndCreatorAnnotations;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.compat.XConverters;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.Key;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Utility methods related to {@link Key}s. */
public final class Keys {
  public static boolean isValidMembersInjectionKey(Key key) {
    return !key.qualifier().isPresent()
        && !key.multibindingContributionIdentifier().isPresent()
        && key.type().java().getKind().equals(TypeKind.DECLARED);
  }

  /**
   * Returns {@code true} if this is valid as an implicit key (that is, if it's valid for a
   * just-in-time binding by discovering an {@code @Inject} constructor).
   */
  public static boolean isValidImplicitProvisionKey(Key key, DaggerTypes types) {
    return isValidImplicitProvisionKey(
        key.qualifier().map(DaggerAnnotation::java), key.type().java(), types);
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  public static boolean isValidImplicitProvisionKey(
      Optional<XAnnotation> qualifier, XType type, DaggerTypes types) {
    return isValidImplicitProvisionKey(qualifier.map(XConverters::toJavac), toJavac(type), types);
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  public static boolean isValidImplicitProvisionKey(
      Optional<? extends AnnotationMirror> qualifier, TypeMirror type, final DaggerTypes types) {
    // Qualifiers disqualify implicit provisioning.
    if (qualifier.isPresent()) {
      return false;
    }

    // A provision type must be a declared type
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }

    // Non-classes or abstract classes aren't allowed.
    DeclaredType declaredType = asDeclared(type);
    if (!declaredType.asElement().getKind().equals(ElementKind.CLASS)
        || declaredType.asElement().getModifiers().contains(Modifier.ABSTRACT)) {
      return false;
    }

    // If the key has type arguments, validate that each type argument is declared.
    // Otherwise the type argument may be a wildcard (or other type), and we can't
    // resolve that to actual types.
    for (TypeMirror arg : declaredType.getTypeArguments()) {
      if (arg.getKind() != TypeKind.DECLARED) {
        return false;
      }
    }

    DeclaredType definedType = declaredType;

    // Also validate that the key is not the erasure of a generic type.
    // If it is, that means the user referred to Foo<T> as just 'Foo',
    // which we don't allow.  (This is a judgement call -- we *could*
    // allow it and instantiate the type bounds... but we don't.)
    return definedType.getTypeArguments().isEmpty()
        || !types.isSameType(types.erasure(definedType), declaredType);
  }

  /**
   * Returns {@code true} if the given key is for a component/subcomponent or a creator of a
   * component/subcomponent.
   */
  public static boolean isComponentOrCreator(Key key) {
    return !key.qualifier().isPresent()
        && key.type().java().getKind() == TypeKind.DECLARED
        && isAnyAnnotationPresent(
            asTypeElement(key.type().java()), allComponentAndCreatorAnnotations());
  }
}
