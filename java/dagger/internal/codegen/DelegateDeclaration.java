/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.collect.Iterables;
import dagger.Binds;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;

/**
 * The declaration for a delegate binding established by a {@link Binds} method.
 */
@AutoValue
abstract class DelegateDeclaration extends BindingDeclaration implements HasContributionType {
  abstract DependencyRequest delegateRequest();

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedMapKey();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  static final class Factory {
    private final DaggerTypes types;
    private final KeyFactory keyFactory;
    private final DependencyRequestFactory dependencyRequestFactory;

    @Inject
    Factory(
        DaggerTypes types,
        KeyFactory keyFactory,
        DependencyRequestFactory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    DelegateDeclaration create(
        ExecutableElement bindsMethod, TypeElement contributingModule) {
      checkArgument(MoreElements.isAnnotationPresent(bindsMethod, Binds.class));
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(contributingModule.asType()), bindsMethod));
      DependencyRequest delegateRequest =
          dependencyRequestFactory.forRequiredResolvedVariable(
              Iterables.getOnlyElement(bindsMethod.getParameters()),
              Iterables.getOnlyElement(resolvedMethod.getParameterTypes()));
      return new AutoValue_DelegateDeclaration(
          ContributionType.fromBindingElement(bindsMethod),
          keyFactory.forBindsMethod(bindsMethod, contributingModule),
          Optional.<Element>of(bindsMethod),
          Optional.of(contributingModule),
          delegateRequest,
          wrapOptionalInEquivalence(getMapKey(bindsMethod)));
    }
  }
}
