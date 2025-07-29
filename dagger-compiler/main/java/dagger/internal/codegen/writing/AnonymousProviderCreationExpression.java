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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.anonymousProvider;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XExpression;

/**
 * A {@link javax.inject.Provider} creation expression for an anonymous inner class whose
 * {@code get()} method returns the expression for an instance binding request for its key.
 */
final class AnonymousProviderCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ContributionBinding binding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final XClassName requestingClass;

  @AssistedInject
  AnonymousProviderCreationExpression(
      @Assisted ContributionBinding binding,
      ComponentRequestRepresentations componentRequestRepresentations,
      ComponentImplementation componentImplementation) {
    this.binding = checkNotNull(binding);
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.requestingClass = componentImplementation.name();
  }

  @Override
  public XCodeBlock creationExpression() {
    BindingRequest instanceExpressionRequest = bindingRequest(binding.key(), RequestKind.INSTANCE);
    XExpression instanceExpression =
        componentRequestRepresentations.getDependencyExpression(
            instanceExpressionRequest,
            // Not a real class name, but the actual requestingClass is an inner class within the
            // given class, not that class itself.
            requestingClass.nestedClass("Anonymous"));
    return anonymousProvider(instanceExpression);
  }

  @AssistedFactory
  static interface Factory {
    AnonymousProviderCreationExpression create(ContributionBinding binding);
  }
}
