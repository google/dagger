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

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** A {@code Provider<MembersInjector<Foo>>} creation expression. */
final class MembersInjectorProviderCreationExpression
    implements FrameworkInstanceCreationExpression {

  private final ComponentBindingExpressions componentBindingExpressions;
  private final ProvisionBinding binding;

  MembersInjectorProviderCreationExpression(
      ProvisionBinding binding, ComponentBindingExpressions componentBindingExpressions) {
    this.binding = checkNotNull(binding);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  @Override
  public CodeBlock creationExpression() {
    TypeMirror membersInjectedType =
        getOnlyElement(MoreTypes.asDeclared(binding.key().type()).getTypeArguments());

    boolean castThroughRawType = false;
    CodeBlock membersInjector;
    if (binding.injectionSites().isEmpty()) {
      membersInjector = CodeBlock.of("$T.<$T>noOp()", MEMBERS_INJECTORS, membersInjectedType);
    } else {
      TypeElement injectedTypeElement = MoreTypes.asTypeElement(membersInjectedType);
      while (!hasLocalInjectionSites(injectedTypeElement)) {
        // Cast through a raw type since we're going to be using the MembersInjector for the
        // parent type.
        castThroughRawType = true;
        injectedTypeElement = MoreTypes.asTypeElement(injectedTypeElement.getSuperclass());
      }

      membersInjector = CodeBlock.of(
          "$T.create($L)",
          membersInjectorNameForType(injectedTypeElement),
          componentBindingExpressions.getCreateMethodArgumentsCodeBlock(binding));
    }

    // TODO(ronshapiro): consider adding a MembersInjectorBindingExpression to return this directly
    // (as it's rarely requested as a Provider).
    CodeBlock providerExpression = CodeBlock.of("$T.create($L)", INSTANCE_FACTORY, membersInjector);
    // If needed we cast through raw type around the InstanceFactory type as opposed to the
    // MembersInjector since we end up with an InstanceFactory<MembersInjector> as opposed to a
    // InstanceFactory<MembersInjector<Foo>> and that becomes unassignable. To fix it would require
    // a second cast. If we just cast to the raw type InstanceFactory though, that becomes
    // assignable.
    return castThroughRawType
        ? CodeBlock.of("($T) $L", INSTANCE_FACTORY, providerExpression) : providerExpression;
  }

  private boolean hasLocalInjectionSites(TypeElement injectedTypeElement) {
    return binding.injectionSites()
        .stream()
        .anyMatch(
            injectionSite ->
                injectionSite.element().getEnclosingElement().equals(injectedTypeElement));
  }

  @Override
  public boolean useInnerSwitchingProvider() {
    return !binding.injectionSites().isEmpty();
  }
}
