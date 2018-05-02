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

package dagger.model;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.Provides;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.Element;

/**
 * Represents a request for a {@link Key} at an injection point. For example, parameters to {@link
 * Inject} constructors, {@link Provides} methods, and component methods are all dependency
 * requests.
 *
 * <p id="synthetic">A dependency request is considered to be <em>synthetic</em> if it does not have
 * an {@link Element} in code that requests the key directly. For example, an {@link
 * java.util.concurrent.Executor} is required for all {@code @Produces} methods to run
 * asynchronously even though it is not directly specified as a parameter to the binding method.
 */
@AutoValue
public abstract class DependencyRequest {
  /** The kind of this request. */
  public abstract RequestKind kind();

  /** The key of this request. */
  public abstract Key key();

  /**
   * The element that declares this dependency request. Absent for <a href="#synthetic">synthetic
   * </a> requests.
   */
  public abstract Optional<Element> requestElement();

  /**
   * Returns {@code true} if this request allows null objects. A request is nullable if it is
   * has an annotation with "Nullable" as its simple name.
   */
  public abstract boolean isNullable();

  /** Returns a new builder of dependency requests. */
  public static DependencyRequest.Builder builder() {
    return new AutoValue_DependencyRequest.Builder().isNullable(false);
  }

  /** A builder of {@link DependencyRequest}s. */
  @CanIgnoreReturnValue
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder kind(RequestKind kind);

    public abstract Builder key(Key key);

    public abstract Builder requestElement(Element element);

    public abstract Builder isNullable(boolean isNullable);

    @CheckReturnValue
    public abstract DependencyRequest build();
  }
}
