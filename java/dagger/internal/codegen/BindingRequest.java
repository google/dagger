/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.RequestKinds.requestType;

import com.google.auto.value.AutoValue;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.serialization.BindingRequestProto;
import dagger.internal.codegen.serialization.FrameworkTypeWrapper;
import dagger.internal.codegen.serialization.RequestKindWrapper;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A request for a binding, which may be in the form of a request for a dependency to pass to a
 * constructor or module method ({@link RequestKind}) or an internal request for a framework
 * instance ({@link FrameworkType}).
 */
@AutoValue
abstract class BindingRequest {
  /** Creates a {@link BindingRequest} for the given {@link DependencyRequest}. */
  static BindingRequest bindingRequest(DependencyRequest dependencyRequest) {
    return bindingRequest(dependencyRequest.key(), dependencyRequest.kind());
  }

  /**
   * Creates a {@link BindingRequest} for a normal dependency request for the given {@link Key} and
   * {@link RequestKind}.
   */
  static BindingRequest bindingRequest(Key key, RequestKind requestKind) {
    // When there's a request that has a 1:1 mapping to a FrameworkType, the request should be
    // associated with that FrameworkType as well, because we want to ensure that if a request
    // comes in for that as a dependency first and as a framework instance later, they resolve to
    // the same binding expression.
    // TODO(cgdecker): Instead of doing this, make ComponentBindingExpressions create a
    // BindingExpression for the RequestKind that simply delegates to the BindingExpression for the
    // FrameworkType. Then there are separate BindingExpressions, but we don't end up doing weird
    // things like creating two fields when there should only be one.
    return new AutoValue_BindingRequest(
        key, Optional.of(requestKind), FrameworkType.forRequestKind(requestKind));
  }

  /**
   * Creates a {@link BindingRequest} for a request for a framework instance for the given {@link
   * Key} with the given {@link FrameworkType}.
   */
  static BindingRequest bindingRequest(Key key, FrameworkType frameworkType) {
    return new AutoValue_BindingRequest(
        key, frameworkType.requestKind(), Optional.of(frameworkType));
  }

  /** Creates a {@link BindingRequest} for the given {@link FrameworkDependency}. */
  static BindingRequest bindingRequest(FrameworkDependency frameworkDependency) {
    return bindingRequest(frameworkDependency.key(), frameworkDependency.frameworkType());
  }

  /** Returns the {@link Key} for the requested binding. */
  abstract Key key();

  /** Returns the request kind associated with this request, if any. */
  abstract Optional<RequestKind> requestKind();

  /** Returns the framework type associated with this request, if any. */
  abstract Optional<FrameworkType> frameworkType();

  /** Returns whether this request is of the given kind. */
  final boolean isRequestKind(RequestKind requestKind) {
    return requestKind.equals(requestKind().orElse(null));
  }

  final TypeMirror requestedType(TypeMirror contributedType, DaggerTypes types) {
    if (requestKind().isPresent()) {
      return requestType(requestKind().get(), contributedType, types);
    }
    return types.wrapType(contributedType, frameworkType().get().frameworkClass());
  }

  /** Returns a name that can be used for the kind of request this is. */
  final String kindName() {
    Object requestKindObject =
        requestKind().isPresent()
            ? requestKind().get()
            : frameworkType().get().frameworkClass().getSimpleName();
    return requestKindObject.toString();
  }

  /** Returns {@code true} if this request can be satisfied by a production binding. */
  final boolean canBeSatisfiedByProductionBinding() {
    if (requestKind().isPresent()) {
      return RequestKinds.canBeSatisfiedByProductionBinding(requestKind().get());
    }
    return frameworkType().get().equals(FrameworkType.PRODUCER_NODE);
  }

  /** Creates a proto representation of this binding request. */
  BindingRequestProto toProto() {
    BindingRequestProto.Builder builder =
        BindingRequestProto.newBuilder().setKey(KeyFactory.toProto(key()));
    if (frameworkType().isPresent()) {
      builder.setFrameworkType(
          FrameworkTypeWrapper.FrameworkType.valueOf(frameworkType().get().name()));
    } else {
      builder.setRequestKind(RequestKindWrapper.RequestKind.valueOf(requestKind().get().name()));
    }
    return builder.build();
  }
}
