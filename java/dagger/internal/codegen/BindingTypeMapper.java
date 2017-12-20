/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingType.CONTRIBUTION_TYPES;
import static dagger.internal.codegen.BindingType.PRODUCTION;
import static dagger.internal.codegen.BindingType.PROVISION;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import javax.inject.Provider;

/**
 * A mapper for associating a {@link RequestKind} to a {@link BindingType}, dependent on the type of
 * code to be generated (e.g., for {@link Provider} or {@link Producer}).
 */
enum BindingTypeMapper {
  FOR_PROVIDER() {
    @Override public BindingType getBindingType(RequestKind requestKind) {
      switch (requestKind) {
        case INSTANCE:
        case PROVIDER:
        case PROVIDER_OF_LAZY:
        case LAZY:
          return PROVISION;
        case PRODUCED:
        case PRODUCER:
          throw new IllegalArgumentException(requestKind.toString());
        default:
          throw new AssertionError(requestKind);
      }
    }
  },
  FOR_PRODUCER() {
    @Override public BindingType getBindingType(RequestKind requestKind) {
      switch (requestKind) {
        case INSTANCE:
        case PRODUCED:
        case PRODUCER:
          return PRODUCTION;
        case PROVIDER:
        case PROVIDER_OF_LAZY:
        case LAZY:
          return PROVISION;
        default:
          throw new AssertionError(requestKind);
      }
    }
  };

  static BindingTypeMapper forBindingType(BindingType bindingType) {
    return bindingType.equals(PRODUCTION) ? FOR_PRODUCER : FOR_PROVIDER;
  }

  abstract BindingType getBindingType(RequestKind requestKind);

  /**
   * Returns the {@link BindingType} to use for a collection of requests of the same
   * {@link BindingKey}. This allows factories to only take a single argument for multiple requests
   * of the same key.
   */
  BindingType getBindingType(Iterable<DependencyRequest> requests) {
    ImmutableSet<BindingType> classes =
        FluentIterable.from(requests).transform(request -> getBindingType(request.kind())).toSet();
    if (classes.size() == 1) {
      return getOnlyElement(classes);
    } else if (classes.equals(CONTRIBUTION_TYPES)) {
      return PROVISION;
    } else {
      throw new IllegalArgumentException("Bad set of framework classes: " + classes);
    }
  }
}
