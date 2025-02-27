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

import static com.google.common.base.Preconditions.checkArgument;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.model.RequestKind;

/**
 * A binding representation that wraps code generation methods that satisfy all kinds of request for
 * that binding.
 */
final class MembersInjectionBindingRepresentation implements BindingRepresentation {
  private final MembersInjectionBinding binding;
  private final MembersInjectionRequestRepresentation membersInjectionRequestRepresentation;

  @AssistedInject
  MembersInjectionBindingRepresentation(
      @Assisted MembersInjectionBinding binding,
      MembersInjectionRequestRepresentation.Factory membersInjectionRequestRepresentationFactory) {
    this.binding = binding;
    this.membersInjectionRequestRepresentation =
        membersInjectionRequestRepresentationFactory.create(binding);
  }

  @Override
  public RequestRepresentation getRequestRepresentation(BindingRequest request) {
    checkArgument(request.isRequestKind(RequestKind.MEMBERS_INJECTION), binding);
    return membersInjectionRequestRepresentation;
  }

  @AssistedFactory
  static interface Factory {
    MembersInjectionBindingRepresentation create(MembersInjectionBinding binding);
  }
}
