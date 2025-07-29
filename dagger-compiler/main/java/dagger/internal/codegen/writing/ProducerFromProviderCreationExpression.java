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

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;

/** An {@code Producer} creation expression for provision bindings. */
final class ProducerFromProviderCreationExpression implements FrameworkInstanceCreationExpression {
  private final RequestRepresentation providerRequestRepresentation;
  private final XClassName requestingClass;

  @AssistedInject
  ProducerFromProviderCreationExpression(
      @Assisted RequestRepresentation providerRequestRepresentation,
      @Assisted XClassName requestingClass) {
    this.providerRequestRepresentation = providerRequestRepresentation;
    this.requestingClass = requestingClass;
  }

  @Override
  public XCodeBlock creationExpression() {
    return FrameworkType.PROVIDER.to(
        RequestKind.PRODUCER,
        providerRequestRepresentation.getDependencyExpression(requestingClass).codeBlock());
  }

  @Override
  public Optional<XClassName> alternativeFrameworkClass() {
    return Optional.of(XTypeNames.PRODUCER);
  }

  @AssistedFactory
  static interface Factory {
    ProducerFromProviderCreationExpression create(
        RequestRepresentation providerRequestRepresentation, XClassName requestingClass);
  }
}
