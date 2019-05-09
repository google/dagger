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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.multibindings.Multibinds;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * A declaration that a multibinding with a certain key is available to be injected in a component
 * even if the component has no multibindings for that key. Identified by a map- or set-returning
 * method annotated with {@link Multibinds @Multibinds}.
 */
@AutoValue
abstract class MultibindingDeclaration extends BindingDeclaration implements HasContributionType {

  /**
   * The map or set key whose availability is declared. For maps, this will be {@code Map<K,
   * Provider<V>>}. For sets, this will be {@code Set<T>}.
   */
  @Override
  public abstract Key key();

  /**
   * {@link ContributionType#SET} if the declared type is a {@link Set}, or
   * {@link ContributionType#MAP} if it is a {@link Map}.
   */
  @Override
  public abstract ContributionType contributionType();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /**
   * A factory for {@link MultibindingDeclaration}s.
   */
  static final class Factory {
    private final DaggerTypes types;
    private final KeyFactory keyFactory;

    @Inject
    Factory(DaggerTypes types, KeyFactory keyFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
    }

    /** A multibinding declaration for a {@link Multibinds @Multibinds} method. */
    MultibindingDeclaration forMultibindsMethod(
        ExecutableElement moduleMethod, TypeElement moduleElement) {
      checkArgument(isAnnotationPresent(moduleMethod, Multibinds.class));
      return forDeclaredMethod(
          moduleMethod,
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(moduleElement.asType()), moduleMethod)),
          moduleElement);
    }

    private MultibindingDeclaration forDeclaredMethod(
        ExecutableElement method,
        ExecutableType methodType,
        TypeElement contributingType) {
      TypeMirror returnType = methodType.getReturnType();
      checkArgument(
          SetType.isSet(returnType) || MapType.isMap(returnType),
          "%s must return a set or map",
          method);
      return new AutoValue_MultibindingDeclaration(
          Optional.<Element>of(method),
          Optional.of(contributingType),
          keyFactory.forMultibindsMethod(methodType, method),
          contributionType(returnType));
    }

    private ContributionType contributionType(TypeMirror returnType) {
      if (MapType.isMap(returnType)) {
        return ContributionType.MAP;
      } else if (SetType.isSet(returnType)) {
        return ContributionType.SET;
      } else {
        throw new IllegalArgumentException("Must be Map or Set: " + returnType);
      }
    }
  }
}
