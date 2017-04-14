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

package dagger.internal.codegen;

import static com.google.common.collect.Maps.newLinkedHashMapWithExpectedSize;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * A registry that associates a {@link BindingKey} with a {@link RequestFulfillment}. The registry
 * is responsible for choosing the most appropriate {@link RequestFulfillment} implementation based
 * on the properties of the binding and how it is used throughout the component.
 */
final class RequestFulfillmentRegistry {
  private final ImmutableMap<BindingKey, ResolvedBindings> resolvedBindingsMap;
  private final HasBindingMembers hasBindingMembers;
  /** This map is mutated as {@link #getRequestFulfillment} is invoked. */
  private final Map<BindingKey, RequestFulfillment> requestFulfillments;

  RequestFulfillmentRegistry(
      ImmutableMap<BindingKey, ResolvedBindings> resolvedBindingsMap,
      HasBindingMembers hasBindingMembers) {
    this.resolvedBindingsMap = resolvedBindingsMap;
    this.hasBindingMembers = hasBindingMembers;
    this.requestFulfillments = newLinkedHashMapWithExpectedSize(resolvedBindingsMap.size());
  }

  /** Returns a {@link RequestFulfillment} implementation for the given {@link BindingKey} */
  RequestFulfillment getRequestFulfillment(BindingKey bindingKey) {
    return requestFulfillments.computeIfAbsent(bindingKey, this::createRequestFulfillment);
  }

  private RequestFulfillment createRequestFulfillment(BindingKey bindingKey) {
    /* TODO(gak): it is super convoluted that we create the member selects separately and then
     * look them up again this way. Now that we have RequestFulfillment, the next step is to
     * create it and the MemberSelect and the field on demand rather than in a first pass. */
    MemberSelect memberSelect = hasBindingMembers.getMemberSelect(bindingKey);
    ResolvedBindings resolvedBindings = resolvedBindingsMap.get(bindingKey);
    switch (resolvedBindings.bindingType()) {
      case MEMBERS_INJECTION:
        return new MembersInjectorRequestFulfillment(bindingKey, memberSelect);
      case PRODUCTION:
        return new ProducerFieldRequestFulfillment(bindingKey, memberSelect);
      case PROVISION:
        ProvisionBinding provisionBinding =
            (ProvisionBinding) resolvedBindings.contributionBinding();

        ProviderFieldRequestFulfillment providerFieldRequestFulfillment =
            new ProviderFieldRequestFulfillment(bindingKey, memberSelect);
        if (provisionBinding.implicitDependencies().isEmpty()
            && !provisionBinding.scope().isPresent()
            && !provisionBinding.requiresModuleInstance()
            && provisionBinding.bindingElement().isPresent()
            && (provisionBinding.bindingKind().equals(INJECTION)
                || provisionBinding.bindingKind().equals(PROVISION))) {
          return new SimpleMethodRequestFulfillment(
              bindingKey, provisionBinding, providerFieldRequestFulfillment, this);
        }
        return providerFieldRequestFulfillment;
      default:
        throw new AssertionError();
    }
  }
}
