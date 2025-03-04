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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.binding.BindingType.PRODUCTION;

import dagger.internal.codegen.model.RequestKind;

/**
 * A mapper for associating a {@link RequestKind} to a {@link FrameworkType}, dependent on the type
 * of code to be generated (e.g., for {@code Provider} or {@code Producer}).
 */
public enum FrameworkTypeMapper {
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

  public static FrameworkTypeMapper forBindingType(BindingType bindingType) {
    return bindingType.equals(PRODUCTION) ? FOR_PRODUCER : FOR_PROVIDER;
  }

  public abstract FrameworkType getFrameworkType(RequestKind requestKind);
}
