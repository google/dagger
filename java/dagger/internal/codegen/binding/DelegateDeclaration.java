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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Iterables;
import dagger.Binds;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.ContributionType.HasContributionType;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DependencyRequest;
import java.util.Optional;
import javax.inject.Inject;

/** The declaration for a delegate binding established by a {@link Binds} method. */
@AutoValue
public abstract class DelegateDeclaration extends BindingDeclaration
    implements HasContributionType {
  abstract DependencyRequest delegateRequest();

  // Note: We're using DaggerAnnotation instead of XAnnotation for its equals/hashcode
  abstract Optional<DaggerAnnotation> mapKey();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /** A {@link DelegateDeclaration} factory. */
  public static final class Factory {
    private final KeyFactory keyFactory;
    private final DependencyRequestFactory dependencyRequestFactory;

    @Inject
    Factory(
        KeyFactory keyFactory,
        DependencyRequestFactory dependencyRequestFactory) {
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    public DelegateDeclaration create(XMethodElement bindsMethod, XTypeElement contributingModule) {
      checkArgument(bindsMethod.hasAnnotation(TypeNames.BINDS));
      XMethodType resolvedMethod = bindsMethod.asMemberOf(contributingModule.getType());
      DependencyRequest delegateRequest =
          dependencyRequestFactory.forRequiredResolvedVariable(
              Iterables.getOnlyElement(bindsMethod.getParameters()),
              Iterables.getOnlyElement(resolvedMethod.getParameterTypes()));
      return new AutoValue_DelegateDeclaration(
          ContributionType.fromBindingElement(bindsMethod),
          keyFactory.forBindsMethod(bindsMethod, contributingModule),
          Optional.<XElement>of(bindsMethod),
          Optional.of(contributingModule),
          delegateRequest,
          getMapKey(bindsMethod).map(DaggerAnnotation::from));
    }
  }
}
