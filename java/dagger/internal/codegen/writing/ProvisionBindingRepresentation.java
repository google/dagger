/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.model.BindingKind.DELEGATE;
import static dagger.internal.codegen.writing.DelegateRequestRepresentation.isBindsScopeStrongerThanDependencyScope;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.writing.ComponentImplementation.CompilerMode;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class ProvisionBindingRepresentation implements BindingRepresentation {
  private final BindingGraph graph;
  private final CompilerMode compilerMode;
  private final ProvisionBinding binding;
  private final DirectInstanceBindingRepresentation directInstanceBindingRepresentation;
  private final FrameworkInstanceBindingRepresentation frameworkInstanceBindingRepresentation;

  @AssistedInject
  ProvisionBindingRepresentation(
      @Assisted ProvisionBinding binding,
      DirectInstanceBindingRepresentation.Factory directInstanceBindingRepresentationFactory,
      FrameworkInstanceBindingRepresentation.Factory frameworkInstanceBindingRepresentationFactory,
      BindingGraph graph,
      ComponentImplementation componentImplementation) {
    this.binding = binding;
    this.graph = graph;
    this.compilerMode = componentImplementation.compilerMode();
    this.directInstanceBindingRepresentation =
        directInstanceBindingRepresentationFactory.create(binding);
    this.frameworkInstanceBindingRepresentation =
        frameworkInstanceBindingRepresentationFactory.create(binding);
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return usesDirectInstanceExpression(request.requestKind())
        ? directInstanceBindingRepresentation.getRequestRepresentation(request)
        : frameworkInstanceBindingRepresentation.getRequestRepresentation(request);
  }

  private boolean usesDirectInstanceExpression(RequestKind requestKind) {
    if (requestKind != RequestKind.INSTANCE && requestKind != RequestKind.FUTURE) {
      return false;
    }

    // In fast init mode, we can avoid generating direct instance expressions if a framework
    // instance expression already exists in the graph. Default mode has more edge cases, so can not
    // be handled with simple pre-check in the graph. For example, a provider for a subcomponent
    // builder is backed with its direct instance, returning framework instance for both cases will
    // form a loop. There are also difficulties introduced by manually created framework requests.
    // TODO(wanyingd): refactor framework instance so that we don't need to generate both direct
    // instance and framework instance representation for the same binding.
    if (compilerMode.isFastInit() && graph.topLevelBindingGraph().hasFrameworkRequest(binding)) {
      return false;
    }

    switch (binding.kind()) {
      case MEMBERS_INJECTOR:
        // Currently, we always use a framework instance for MembersInjectors, e.g.
        // InstanceFactory.create(Foo_MembersInjector.create(...)).
        // TODO(b/199889259): Consider optimizing this for fastInit mode.
      case ASSISTED_FACTORY:
        // Assisted factory binding can be requested with framework request, and it is essentially a
        // provider for assisted injection binding. So we will always return framework instance for
        // assisted factory bindings.
        return false;
      case ASSISTED_INJECTION:
        throw new IllegalStateException(
            "Assisted injection binding shouldn't be requested with an instance request.");
      default:
        // We don't need to use Provider#get() if there's no caching, so use a direct instance.
        // TODO(bcorso): This can be optimized in cases where we know a Provider field already
        // exists, in which case even if it's not scoped we might as well call Provider#get().
        return !needsCaching(binding, graph);
    }
  }

  /**
   * Returns {@code true} if the component needs to make sure the provided value is cached.
   *
   * <p>The component needs to cache the value for scoped bindings except for {@code @Binds}
   * bindings whose scope is no stronger than their delegate's.
   */
  static boolean needsCaching(ProvisionBinding binding, BindingGraph graph) {
    if (!binding.scope().isPresent()) {
      return false;
    }
    if (binding.kind().equals(DELEGATE)) {
      return isBindsScopeStrongerThanDependencyScope(binding, graph);
    }
    return true;
  }

  @AssistedFactory
  static interface Factory {
    ProvisionBindingRepresentation create(ProvisionBinding binding);
  }
}
