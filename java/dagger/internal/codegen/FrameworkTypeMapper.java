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
import static dagger.internal.codegen.BindingType.PRODUCTION;
import static java.util.stream.Collectors.toSet;

import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import java.util.Set;
import javax.inject.Provider;

/**
 * A mapper for associating a {@link RequestKind} to a {@link FrameworkType}, dependent on the type
 * of code to be generated (e.g., for {@link Provider} or {@link Producer}).
 */
enum FrameworkTypeMapper {
  FOR_PROVIDER() {
    @Override
    public FrameworkType getFrameworkType(RequestKind requestKind) {
      switch (requestKind) {
        case INSTANCE:
        case PROVIDER:
        case PROVIDER_OF_LAZY:
        case LAZY:
          return FrameworkType.PROVIDER;
        case PRODUCED:
        case PRODUCER:
          throw new IllegalArgumentException(requestKind.toString());
        default:
          throw new AssertionError(requestKind);
      }
    }
  },
  FOR_PRODUCER() {
    @Override
    public FrameworkType getFrameworkType(RequestKind requestKind) {
      switch (requestKind) {
        case INSTANCE:
        case PRODUCED:
        case PRODUCER:
          return FrameworkType.PRODUCER_NODE;
        case PROVIDER:
        case PROVIDER_OF_LAZY:
        case LAZY:
          return FrameworkType.PROVIDER;
        default:
          throw new AssertionError(requestKind);
      }
    }
  };

  static FrameworkTypeMapper forBindingType(BindingType bindingType) {
    return bindingType.equals(PRODUCTION) ? FOR_PRODUCER : FOR_PROVIDER;
  }

  abstract FrameworkType getFrameworkType(RequestKind requestKind);

  /**
   * Returns the {@link FrameworkType} to use for a collection of requests of the same {@link Key}.
   * This allows factories to only take a single argument for multiple requests of the same key.
   */
  FrameworkType getFrameworkType(Set<DependencyRequest> requests) {
    Set<FrameworkType> frameworkTypes =
        requests.stream().map(request -> getFrameworkType(request.kind())).collect(toSet());
    return frameworkTypes.size() == 1 ? getOnlyElement(frameworkTypes) : FrameworkType.PROVIDER;
  }
}
