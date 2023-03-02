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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.base.RequestKinds.requestType;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import java.util.Optional;

/**
 * A request for a binding, which may be in the form of a request for a dependency to pass to a
 * constructor or module method ({@link RequestKind}) or an internal request for a framework
 * instance ({@link FrameworkType}).
 */
@AutoValue
public abstract class BindingRequest {
  /** Creates a {@link BindingRequest} for the given {@link DependencyRequest}. */
  public static BindingRequest bindingRequest(DependencyRequest dependencyRequest) {
    return bindingRequest(dependencyRequest.key(), dependencyRequest.kind());
  }

  /**
   * Creates a {@link BindingRequest} for a normal dependency request for the given {@link Key} and
   * {@link RequestKind}.
   */
  public static BindingRequest bindingRequest(Key key, RequestKind requestKind) {
    // When there's a request that has a 1:1 mapping to a FrameworkType, the request should be
    // associated with that FrameworkType as well, because we want to ensure that if a request
    // comes in for that as a dependency first and as a framework instance later, they resolve to
    // the same binding expression.
    // TODO(cgdecker): Instead of doing this, make ComponentRequestRepresentations create a
    // RequestRepresentation for the RequestKind that simply delegates to the RequestRepresentation
    // for the FrameworkType. Then there are separate RequestRepresentations, but we don't end up
    // doing weird things like creating two fields when there should only be one.
    return new AutoValue_BindingRequest(
        key, requestKind, FrameworkType.forRequestKind(requestKind));
  }

  /**
   * Creates a {@link BindingRequest} for a request for a framework instance for the given {@link
   * Key} with the given {@link FrameworkType}.
   */
  public static BindingRequest bindingRequest(Key key, FrameworkType frameworkType) {
    return new AutoValue_BindingRequest(
        key, frameworkType.requestKind(), Optional.of(frameworkType));
  }

  /** Returns the {@link Key} for the requested binding. */
  public abstract Key key();

  /** Returns the request kind associated with this request. */
  public abstract RequestKind requestKind();

  /** Returns the framework type associated with this request, if any. */
  public abstract Optional<FrameworkType> frameworkType();

  /** Returns whether this request is of the given kind. */
  public final boolean isRequestKind(RequestKind requestKind) {
    return requestKind.equals(requestKind());
  }

  public final XType requestedType(XType contributedType, XProcessingEnv processingEnv) {
    return requestType(requestKind(), contributedType, processingEnv);
  }

  /** Returns a name that can be used for the kind of request this is. */
  public final String kindName() {
    return requestKind().toString();
  }
}
