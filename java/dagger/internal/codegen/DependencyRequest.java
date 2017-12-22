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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.Provides;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * Represents a request for a key at an injection point. Parameters to {@link Inject} constructors
 * or {@link Provides} methods are examples of key requests.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): Set bindings and the permutations thereof need to be addressed
@AutoValue
abstract class DependencyRequest {
  abstract RequestKind kind();
  abstract Key key();

  BindingKey bindingKey() {
    switch (kind()) {
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PROVIDER_OF_LAZY:
      case PRODUCER:
      case PRODUCED:
      case FUTURE:
        return BindingKey.contribution(key());
      case MEMBERS_INJECTION:
        return BindingKey.membersInjection(key());
      default:
        throw new AssertionError(this);
    }
  }

  /** The element that declares this dependency request. Absent for synthetic requests. */
  abstract Optional<Element> requestElement();

  /**
   * Returns {@code true} if {@code requestElement}'s type is a primitive type.
   *
   * <p>Because the {@link #key()} of a {@link DependencyRequest} is {@linkplain
   * KeyFactory#boxPrimitives(TypeMirror) boxed} to normalize it with other keys, this inspects the
   * {@link #requestElement()} directly.
   */
  boolean requestsPrimitiveType() {
    return requestElement().map(element -> element.asType().getKind().isPrimitive()).orElse(false);
  }

  /** Returns true if this request allows null objects. */
  abstract boolean isNullable();

  static DependencyRequest.Builder builder() {
    return new AutoValue_DependencyRequest.Builder().isNullable(false);
  }

  @CanIgnoreReturnValue
  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder kind(RequestKind kind);

    abstract Builder key(Key key);

    abstract Builder requestElement(Element element);

    abstract Builder isNullable(boolean isNullable);

    @CheckReturnValue
    abstract DependencyRequest build();
  }

}
