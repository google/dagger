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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.javapoet.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.MEMBERS_INJECTORS;

import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/** A {@code Provider<MembersInjector<Foo>>} creation expression. */
final class MembersInjectorProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ShardImplementation shardImplementation;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final ProvisionBinding binding;

  @AssistedInject
  MembersInjectorProviderCreationExpression(
      @Assisted ProvisionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations) {
    this.binding = checkNotNull(binding);
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.componentRequestRepresentations = checkNotNull(componentRequestRepresentations);
  }

  @Override
  public CodeBlock creationExpression() {
    XType membersInjectedType =
        getOnlyElement(binding.key().type().xprocessing().getTypeArguments());

    boolean castThroughRawType = false;
    CodeBlock membersInjector;
    if (binding.injectionSites().isEmpty()) {
      membersInjector =
          CodeBlock.of("$T.<$T>noOp()", MEMBERS_INJECTORS, membersInjectedType.getTypeName());
    } else {
      XTypeElement injectedTypeElement = membersInjectedType.getTypeElement();
      while (!hasLocalInjectionSites(injectedTypeElement)) {
        // Cast through a raw type since we're going to be using the MembersInjector for the
        // parent type.
        castThroughRawType = true;
        injectedTypeElement = injectedTypeElement.getSuperType().getTypeElement();
      }

      membersInjector =
          CodeBlock.of(
              "$T.create($L)",
              membersInjectorNameForType(injectedTypeElement),
              componentRequestRepresentations.getCreateMethodArgumentsCodeBlock(
                  binding, shardImplementation.name()));
    }

    // TODO(ronshapiro): consider adding a MembersInjectorRequestRepresentation to return this
    // directly
    // (as it's rarely requested as a Provider).
    CodeBlock providerExpression = CodeBlock.of("$T.create($L)", INSTANCE_FACTORY, membersInjector);
    // If needed we cast through raw type around the InstanceFactory type as opposed to the
    // MembersInjector since we end up with an InstanceFactory<MembersInjector> as opposed to a
    // InstanceFactory<MembersInjector<Foo>> and that becomes unassignable. To fix it would require
    // a second cast. If we just cast to the raw type InstanceFactory though, that becomes
    // assignable.
    return castThroughRawType
        ? CodeBlock.of("($T) $L", INSTANCE_FACTORY, providerExpression)
        : providerExpression;
  }

  private boolean hasLocalInjectionSites(XTypeElement injectedTypeElement) {
    return binding.injectionSites().stream()
        .map(InjectionSite::enclosingTypeElement)
        .anyMatch(injectedTypeElement::equals);
  }

  @AssistedFactory
  static interface Factory {
    MembersInjectorProviderCreationExpression create(ProvisionBinding binding);
  }
}
