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

package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

/** Utility methods related to {@link Key}s. */
final class Keys {
  static boolean isValidMembersInjectionKey(Key key) {
    return !key.qualifier().isPresent()
        && !key.multibindingContributionIdentifier().isPresent()
        && key.type().getKind().equals(TypeKind.DECLARED);
  }

  /**
   * Returns {@code true} if this is valid as an implicit key (that is, if it's valid for a
   * just-in-time binding by discovering an {@code @Inject} constructor).
   */
  static boolean isValidImplicitProvisionKey(Key key, DaggerTypes types) {
    return isValidImplicitProvisionKey(key.qualifier(), key.type(), types);
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  static boolean isValidImplicitProvisionKey(
      Optional<? extends AnnotationMirror> qualifier, TypeMirror type, final DaggerTypes types) {
    // Qualifiers disqualify implicit provisioning.
    if (qualifier.isPresent()) {
      return false;
    }

    return type.accept(
        new SimpleTypeVisitor6<Boolean, Void>(false) {
          @Override
          public Boolean visitDeclared(DeclaredType type, Void ignored) {
            // Non-classes or abstract classes aren't allowed.
            TypeElement element = MoreElements.asType(type.asElement());
            if (!element.getKind().equals(ElementKind.CLASS)
                || element.getModifiers().contains(Modifier.ABSTRACT)) {
              return false;
            }

            // If the key has type arguments, validate that each type argument is declared.
            // Otherwise the type argument may be a wildcard (or other type), and we can't
            // resolve that to actual types.
            for (TypeMirror arg : type.getTypeArguments()) {
              if (arg.getKind() != TypeKind.DECLARED) {
                return false;
              }
            }

            // Also validate that the key is not the erasure of a generic type.
            // If it is, that means the user referred to Foo<T> as just 'Foo',
            // which we don't allow.  (This is a judgement call -- we *could*
            // allow it and instantiate the type bounds... but we don't.)
            return MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
                || !types.isSameType(types.erasure(element.asType()), type);
          }
        },
        null);
  }
}
