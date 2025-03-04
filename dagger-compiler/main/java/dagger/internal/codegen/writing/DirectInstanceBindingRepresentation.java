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

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Returns request representation based on a direct instance expression. */
final class DirectInstanceBindingRepresentation {
  private final ContributionBinding binding;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentMethodRequestRepresentation.Factory
      componentMethodRequestRepresentationFactory;
  private final ImmediateFutureRequestRepresentation.Factory
      immediateFutureRequestRepresentationFactory;
  private final PrivateMethodRequestRepresentation.Factory
      privateMethodRequestRepresentationFactory;
  private final UnscopedDirectInstanceRequestRepresentationFactory
      unscopedDirectInstanceRequestRepresentationFactory;
  private final Map<BindingRequest, RequestRepresentation> requestRepresentations = new HashMap<>();

  @AssistedInject
  DirectInstanceBindingRepresentation(
      @Assisted ContributionBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentMethodRequestRepresentation.Factory componentMethodRequestRepresentationFactory,
      ImmediateFutureRequestRepresentation.Factory immediateFutureRequestRepresentationFactory,
      PrivateMethodRequestRepresentation.Factory privateMethodRequestRepresentationFactory,
      UnscopedDirectInstanceRequestRepresentationFactory
          unscopedDirectInstanceRequestRepresentationFactory) {
    this.binding = binding;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.componentMethodRequestRepresentationFactory = componentMethodRequestRepresentationFactory;
    this.immediateFutureRequestRepresentationFactory = immediateFutureRequestRepresentationFactory;
    this.privateMethodRequestRepresentationFactory = privateMethodRequestRepresentationFactory;
    this.unscopedDirectInstanceRequestRepresentationFactory =
        unscopedDirectInstanceRequestRepresentationFactory;
  }

  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    return reentrantComputeIfAbsent(
        requestRepresentations, request, this::getRequestRepresentationUncached);
  }

  private RequestRepresentation getRequestRepresentationUncached(BindingRequest request) {
    switch (request.requestKind()) {
      case INSTANCE:
        return requiresMethodEncapsulation(binding)
            ? wrapInMethod(unscopedDirectInstanceRequestRepresentationFactory.create(binding))
            : unscopedDirectInstanceRequestRepresentationFactory.create(binding);

      case FUTURE:
        return immediateFutureRequestRepresentationFactory.create(
            getRequestRepresentation(bindingRequest(binding.key(), RequestKind.INSTANCE)),
            binding.key().type().xprocessing());

      default:
        throw new AssertionError(
            String.format("Invalid binding request kind: %s", request.requestKind()));
    }
  }

  /**
   * Returns a binding expression that uses a given one as the body of a method that users call. If
   * a component provision method matches it, it will be the method implemented. If it does not
   * match a component provision method and the binding is modifiable, then a new public modifiable
   * binding method will be written. If the binding doesn't match a component method and is not
   * modifiable, then a new private method will be written.
   */
  RequestRepresentation wrapInMethod(RequestRepresentation bindingExpression) {
    // If we've already wrapped the expression, then use the delegate.
    if (bindingExpression instanceof MethodRequestRepresentation) {
      return bindingExpression;
    }

    BindingRequest request = bindingRequest(binding.key(), RequestKind.INSTANCE);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.findFirstMatchingComponentMethod(request);

    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);

    // Consider the case of a request from a component method like:
    //
    //   DaggerMyComponent extends MyComponent {
    //     @Overrides
    //     Foo getFoo() {
    //       <FOO_BINDING_REQUEST>
    //     }
    //   }
    //
    // Normally, in this case we would return a ComponentMethodRequestRepresentation rather than a
    // PrivateMethodRequestRepresentation so that #getFoo() can inline the implementation rather
    // than
    // create an unnecessary private method and return that. However, with sharding we don't want to
    // inline the implementation because that would defeat some of the class pool savings if those
    // fields had to communicate across shards. Thus, when a key belongs to a separate shard use a
    // PrivateMethodRequestRepresentation and put the private method in the shard.
    if (matchingComponentMethod.isPresent() && shardImplementation.isComponentShard()) {
      ComponentMethodDescriptor componentMethod = matchingComponentMethod.get();
      return componentMethodRequestRepresentationFactory.create(bindingExpression, componentMethod);
    } else {
      return privateMethodRequestRepresentationFactory.create(request, binding, bindingExpression);
    }
  }

  private static boolean requiresMethodEncapsulation(ContributionBinding binding) {
    switch (binding.kind()) {
      case COMPONENT:
      case COMPONENT_PROVISION:
      case SUBCOMPONENT_CREATOR:
      case COMPONENT_DEPENDENCY:
      case MULTIBOUND_SET:
      case MULTIBOUND_MAP:
      case BOUND_INSTANCE:
      case ASSISTED_FACTORY:
      case ASSISTED_INJECTION:
      case INJECTION:
      case PROVISION:
        // These binding kinds satify a binding request with a component method or a private
        // method when the requested binding has dependencies. The method will wrap the logic of
        // creating the binding instance. Without the encapsulation, we might see many levels of
        // nested instance creation code in a single statement to satisfy all dependencies of a
        // binding request.
        return !binding.dependencies().isEmpty();
      case MEMBERS_INJECTOR:
      case PRODUCTION:
      case COMPONENT_PRODUCTION:
      case OPTIONAL:
      case DELEGATE:
      case MEMBERS_INJECTION:
        return false;
    }
    throw new AssertionError(String.format("No such binding kind: %s", binding.kind()));
  }

  @AssistedFactory
  static interface Factory {
    DirectInstanceBindingRepresentation create(ContributionBinding binding);
  }
}
