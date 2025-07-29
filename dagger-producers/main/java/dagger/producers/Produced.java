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

package dagger.producers;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.Beta;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * An interface that represents the result of a {@linkplain Producer production} of type {@code T},
 * or an exception that was thrown during that production. For any type {@code T} that can be
 * injected, you can also inject {@code Produced<T>}, which enables handling of any exceptions that
 * were thrown during the production of {@code T}.
 *
 * <p>For example: <pre><code>
 *   {@literal @}Produces Html getResponse(
 *       UserInfo criticalInfo, {@literal Produced<ExtraInfo>} noncriticalInfo) {
 *     try {
 *       return new Html(criticalInfo, noncriticalInfo.get());
 *     } catch (ExecutionException e) {
 *       logger.warning(e, "Noncritical info");
 *       return new Html(criticalInfo);
 *     }
 *   }
 * </code></pre>
 *
 * @since 2.0
 */
@Beta
@CheckReturnValue
public abstract class Produced<T> {
  /**
   * Returns the result of a production.
   *
   * @throws ExecutionException if the production threw an exception
   */
  public abstract T get() throws ExecutionException;

  /**
   * Two {@code Produced} objects compare equal if both are successful with equal values, or both
   * are failed with equal exceptions.
   */
  @Override
  public abstract boolean equals(Object o);

  /** Returns an appropriate hash code to match {@link #equals(Object)}. */
  @Override
  public abstract int hashCode();

  /** Returns a successful {@code Produced}, whose {@link #get} will return the given value. */
  public static <T> Produced<T> successful(@NullableDecl T value) {
    return new Successful<T>(value);
  }

  /**
   * Returns a failed {@code Produced}, whose {@link #get} will throw an
   * {@code ExecutionException} with the given cause.
   */
  public static <T> Produced<T> failed(Throwable throwable) {
    return new Failed<T>(checkNotNull(throwable));
  }

  private static final class Successful<T> extends Produced<T> {
    @NullableDecl private final T value;

    private Successful(@NullableDecl T value) {
      this.value = value;
    }

    @Override
    @NullableDecl
    public T get() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof Successful) {
        Successful<?> that = (Successful<?>) o;
        return Objects.equal(this.value, that.value);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return value == null ? 0 : value.hashCode();
    }

    @Override
    public String toString() {
      return "Produced[" + value + "]";
    }
  }

  private static final class Failed<T> extends Produced<T> {
    private final Throwable throwable;

    private Failed(Throwable throwable) {
      this.throwable = checkNotNull(throwable);
    }

    @Override
    public T get() throws ExecutionException {
      throw new ExecutionException(throwable);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof Failed) {
        Failed<?> that = (Failed<?>) o;
        return this.throwable.equals(that.throwable);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return throwable.hashCode();
    }

    @Override
    public String toString() {
      return "Produced[failed with " + throwable.getClass().getCanonicalName() + "]";
    }
  }

  private Produced() {}
}
