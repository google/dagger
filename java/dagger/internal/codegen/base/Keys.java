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

import static dagger.internal.codegen.base.ComponentAnnotation.allComponentAndCreatorAnnotations;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isRawParameterizedType;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.Key;
import java.util.Optional;

/** Utility methods related to {@link Key}s. */
public final class Keys {
  public static boolean isValidMembersInjectionKey(Key key) {
    return !key.qualifier().isPresent()
        && !key.multibindingContributionIdentifier().isPresent()
        && isDeclared(key.type().xprocessing());
  }

  /**
   * Returns {@code true} if this is valid as an implicit key (that is, if it's valid for a
   * just-in-time binding by discovering an {@code @Inject} constructor).
   */
  public static boolean isValidImplicitProvisionKey(Key key) {
    return isValidImplicitProvisionKey(
        key.qualifier().map(DaggerAnnotation::xprocessing), key.type().xprocessing());
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  public static boolean isValidImplicitProvisionKey(Optional<XAnnotation> qualifier, XType type) {
    // Qualifiers disqualify implicit provisioning.
    if (qualifier.isPresent()) {
      return false;
    }

    // A provision type must be a declared type
    if (!isDeclared(type)) {
      return false;
    }

    // Non-classes or abstract classes aren't allowed.
    XTypeElement typeElement = type.getTypeElement();
    if (!typeElement.isClass() || typeElement.isAbstract()) {
      return false;
    }

    // If the key has type arguments, validate that each type argument is declared.
    // Otherwise the type argument may be a wildcard (or other type), and we can't
    // resolve that to actual types.
    for (XType arg : type.getTypeArguments()) {
      if (!isDeclared(arg)) {
        return false;
      }
    }

    // Also validate that if the type represents a parameterized type the user didn't refer to its
    // raw type, which we don't allow. (This is a judgement call -- we *could* allow it and
    // instantiate the type bounds... but we don't.)
    return !isRawParameterizedType(type);
  }

  /**
   * Returns {@code true} if the given key is for a component/subcomponent or a creator of a
   * component/subcomponent.
   */
  public static boolean isComponentOrCreator(Key key) {
    return !key.qualifier().isPresent()
        && isDeclared(key.type().xprocessing())
        && hasAnyAnnotation(
            key.type().xprocessing().getTypeElement(), allComponentAndCreatorAnnotations());
  }
}
