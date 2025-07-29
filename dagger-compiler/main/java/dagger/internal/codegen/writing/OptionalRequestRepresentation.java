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
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.OptionalType.OptionalKind;
import dagger.internal.codegen.binding.OptionalBinding;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XExpression;

/** A binding expression for optional bindings. */
final class OptionalRequestRepresentation extends RequestRepresentation {
  private final OptionalBinding binding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final XProcessingEnv processingEnv;

  @AssistedInject
  OptionalRequestRepresentation(
      @Assisted OptionalBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      XProcessingEnv processingEnv) {
    this.binding = binding;
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.processingEnv = processingEnv;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    OptionalType optionalType = OptionalType.from(binding.key());
    OptionalKind optionalKind = optionalType.kind();
    if (binding.dependencies().isEmpty()) {
      if (isPreJava8SourceVersion(processingEnv)) {
        // When compiling with -source 7, javac's type inference isn't strong enough to detect
        // Futures.immediateFuture(Optional.absent()) for keys that aren't Object. It also has
        // issues
        // when used as an argument to some members injection proxy methods (see
        // https://github.com/google/dagger/issues/916)
        if (isTypeAccessibleFrom(
            binding.key().type().xprocessing(), requestingClass.getPackageName())) {
          return XExpression.create(
              binding.key().type().xprocessing(),
              optionalKind.parameterizedAbsentValueExpression(optionalType));
        }
      }
      return XExpression.create(
          binding.key().type().xprocessing(), optionalKind.absentValueExpression());
    }
    DependencyRequest dependency = getOnlyElement(binding.dependencies());

    XCodeBlock dependencyExpression =
        componentRequestRepresentations
            .getDependencyExpression(bindingRequest(dependency), requestingClass)
            .codeBlock();

    boolean needsObjectExpression = !isTypeAccessibleFrom(
        dependency.key().type().xprocessing(), requestingClass.getPackageName())
        || (isPreJava8SourceVersion(processingEnv) && dependency.kind() == RequestKind.PROVIDER);

    return !needsObjectExpression
        ? XExpression.create(
            binding.key().type().xprocessing(),
            optionalKind.presentExpression(dependencyExpression))
        // If the dependency type is inaccessible, then we have to use Optional.<Object>of(...), or
        // else we will get "incompatible types: inference variable has incompatible bounds.
        : XExpression.create(
            processingEnv.getDeclaredType(
                processingEnv.findTypeElement(optionalKind.className()),
                processingEnv.findType(XTypeName.ANY_OBJECT)),
            optionalKind.presentObjectExpression(dependencyExpression));
  }

  @AssistedFactory
  static interface Factory {
    OptionalRequestRepresentation create(OptionalBinding binding);
  }
}
