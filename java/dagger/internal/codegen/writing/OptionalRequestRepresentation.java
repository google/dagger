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

package dagger.internal.codegen.writing;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.OptionalType.OptionalKind;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.DependencyRequest;
import javax.lang.model.SourceVersion;

/** A binding expression for optional bindings. */
final class OptionalRequestRepresentation extends RequestRepresentation {
  private final ProvisionBinding binding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final DaggerTypes types;
  private final SourceVersion sourceVersion;

  @AssistedInject
  OptionalRequestRepresentation(
      @Assisted ProvisionBinding binding,
      ComponentRequestRepresentations componentRequestRepresentations,
      DaggerTypes types,
      SourceVersion sourceVersion) {
    this.binding = binding;
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.types = types;
    this.sourceVersion = sourceVersion;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    OptionalType optionalType = OptionalType.from(binding.key());
    OptionalKind optionalKind = optionalType.kind();
    if (binding.dependencies().isEmpty()) {
      if (sourceVersion.compareTo(SourceVersion.RELEASE_7) <= 0) {
        // When compiling with -source 7, javac's type inference isn't strong enough to detect
        // Futures.immediateFuture(Optional.absent()) for keys that aren't Object. It also has
        // issues
        // when used as an argument to some members injection proxy methods (see
        // https://github.com/google/dagger/issues/916)
        if (isTypeAccessibleFrom(binding.key().type().java(), requestingClass.packageName())) {
          return Expression.create(
              binding.key().type().java(),
              optionalKind.parameterizedAbsentValueExpression(optionalType));
        }
      }
      return Expression.create(binding.key().type().java(), optionalKind.absentValueExpression());
    }
    DependencyRequest dependency = getOnlyElement(binding.dependencies());

    CodeBlock dependencyExpression =
        componentRequestRepresentations
            .getDependencyExpression(bindingRequest(dependency), requestingClass)
            .codeBlock();

    // If the dependency type is inaccessible, then we have to use Optional.<Object>of(...), or else
    // we will get "incompatible types: inference variable has incompatible bounds.
    return isTypeAccessibleFrom(dependency.key().type().java(), requestingClass.packageName())
        ? Expression.create(
            binding.key().type().java(), optionalKind.presentExpression(dependencyExpression))
        : Expression.create(
            types.erasure(binding.key().type().java()),
            optionalKind.presentObjectExpression(dependencyExpression));
  }
  
  @AssistedFactory
  static interface Factory {
    OptionalRequestRepresentation create(ProvisionBinding binding);
  }
}
