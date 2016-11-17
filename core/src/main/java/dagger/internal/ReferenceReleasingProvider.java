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

package dagger.internal;

import static dagger.internal.Preconditions.checkNotNull;

import java.lang.ref.WeakReference;
import javax.inject.Provider;

/**
 * A {@link Provider} implementation that can exchange its strong reference to the stored object for
 * a {@link WeakReference}.
 *
 * <p>The provider can be in any one of four states at a time:
 *
 * <ul>
 * <li>In <b>uninitialized</b> state, the provider's strong reference and its weak reference are
 *     both {@code null}.
 * <li>In <b>cleared</b> state, the strong reference is {@code null}, and the weak reference's value
 *     is {@code null}.
 * <li>In <b>strong-reference</b> state, the strong reference refers to the stored object, and the
 *     weak reference is {@code null}.
 * <li>In <b>weak-reference</b> state, the strong reference is {@code null}, and the weak
 *     reference's value is not {@code null}.
 * </ul>
 *
 * <p>The provider starts in <b>uninitialized</b> state.
 *
 * <p>{@link #get()} transitions to <b>strong-reference</b> state when in <b>uninitialized</b> or
 * <b>cleared</b> state.
 *
 * <p>{@link #releaseStrongReference()} transitions to <b>weak-reference</b> state when in
 * <b>strong-reference</b> state, unless the stored value is {@code null}.
 *
 * <p>{@link #restoreStrongReference()} transitions to <b>strong-reference</b> state when in
 * <b>weak-reference</b> state.
 *
 * <p>If garbage collection clears the weak reference while in <b>weak-reference</b> state, the
 * provider transitions to <b>cleared</b> state.
 *
 * <p><img src="doc-files/ReferenceReleasingProvider-statemachine.png">
 *
 * @see <a href="http://google.github.io/dagger/users-guide.html#releasable-references">Releasable
 *     references</a>
 */
@GwtIncompatible
public final class ReferenceReleasingProvider<T> implements Provider<T> {
  private static final Object NULL = new Object(); // sentinel used when provider.get() returns null

  private final Provider<T> provider;
  private volatile Object strongReference;
  private volatile WeakReference<T> weakReference;

  private ReferenceReleasingProvider(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  /**
   * Releases the strong reference to the object previously returned by {@link #get()}, and creates
   * a {@link WeakReference} to that object, unless the stored value is {@code null}.
   */
  public void releaseStrongReference() {
    Object value = strongReference;
    if (value != null && value != NULL) {
      synchronized (this) {
        @SuppressWarnings("unchecked") // values other than NULL come from the provider
        T storedValue = (T) value;
        weakReference = new WeakReference<T>(storedValue);
        strongReference = null;
      }
    }
  }

  /**
   * Restores the strong reference that was previously {@linkplain #releaseStrongReference()
   * released} if the {@link WeakReference} has not yet been cleared during garbage collection.
   */
  public void restoreStrongReference() {
    Object value = strongReference;
    if (weakReference != null && value == null) {
      synchronized (this) {
        value = strongReference;
        if (weakReference != null && value == null) {
          value = weakReference.get();
          if (value != null) {
            strongReference = value;
            weakReference = null;
          }
        }
      }
    }
  }

  /**
   * Returns the result of calling {@link Provider#get()} on the underlying {@link Provider}.
   *
   * <p>Calling {@code get()} in <b>uninitialized</b> or <b>cleared</b> state calls {@code get()}
   * on the underlying provider, sets the strong reference to the returned value, and returns it,
   * leaving the provider in <b>strong-reference</b> state.
   *
   * <p>Calling {@code get()} in <b>strong-reference</b> state simply returns the strong reference,
   * leaving the provider in <b>strong-reference</b> state.
   *
   * <p>Calling {@code get()} in <b>weak-reference</b> state returns the {@link WeakReference}'s
   * value, leaving the provider in <b>weak-reference</b> state.
   */
  @SuppressWarnings("unchecked") // cast only happens when result comes from the provider
  @Override
  public T get() {
    Object value = currentValue();
    if (value == null) {
      synchronized (this) {
        value = currentValue();
        if (value == null) {
          value = provider.get();
          if (value == null) {
            value = NULL;
          }
          strongReference = value;
        }
      }
    }
    return value == NULL ? null : (T) value;
  }

  private Object currentValue() {
    Object value = strongReference;
    if (value != null) {
      return value;
    }
    if (weakReference != null) {
      return weakReference.get();
    }
    return null;
  }

  /**
   * Returns a {@link Provider} that stores the value from the given delegate provider and is
   * managed by {@code references}.
   */
  public static <T> ReferenceReleasingProvider<T> create(
      Provider<T> delegate, ReferenceReleasingProviderManager references) {
    ReferenceReleasingProvider<T> provider =
        new ReferenceReleasingProvider<T>(checkNotNull(delegate));
    references.addProvider(provider);
    return provider;
  }
}
