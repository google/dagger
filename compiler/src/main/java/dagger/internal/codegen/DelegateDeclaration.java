/*
 * Copyright (C) 2016 Google, Inc.
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
import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import dagger.Binds;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.Key.HasKey;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.ContributionType.UNIQUE;

/**
 * The declaration for a delegate binding established by a {@link Bind} method.
 */
@AutoValue
abstract class DelegateDeclaration implements HasKey, HasSourceElement, HasContributionType {
  abstract DependencyRequest delegateRequest();

  @Override
  public ContributionType contributionType() {
    return UNIQUE;
  }

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(
        Types types, Key.Factory keyFactory, DependencyRequest.Factory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    DelegateDeclaration create(
        ExecutableElement bindsMethod, TypeElement contributingElement) {
      checkArgument(MoreElements.isAnnotationPresent(bindsMethod, Binds.class));
      SourceElement sourceElement = SourceElement.forElement(bindsMethod, contributingElement);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(sourceElement.asMemberOfContributingType(types));
      DependencyRequest delegateRequest =
          dependencyRequestFactory.forRequiredResolvedVariable(
              MoreTypes.asDeclared(contributingElement.asType()),
              Iterables.getOnlyElement(bindsMethod.getParameters()),
              Iterables.getOnlyElement(resolvedMethod.getParameterTypes()));
      return new AutoValue_DelegateDeclaration(
          keyFactory.forBindsMethod(sourceElement), sourceElement, delegateRequest);
    }
  }
}
