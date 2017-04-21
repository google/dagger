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

package dagger.releasablereferences;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import javax.inject.Provider;

/**
 * An object that can <a href="https://google.github.io/dagger/users-guide.html#releasable-references">release or
 * restore strong references</a> held in a {@link CanReleaseReferences @CanReleaseReferences} scope.
 *
 * <p>Your top-level component can provide a {@link
 * ForReleasableReferences @ForReleasableReferences(Foo.class)} {@link ReleasableReferenceManager}
 * object for any {@link CanReleaseReferences @CanReleaseReferences}-annotated scope {@code Foo}
 * anywhere in your component hierarchy.
 *
 * <p>It can also provide a {@code Set<ReleasableReferenceManager>} that contains all such objects.
 *
 * <p>Each provider in the {@link CanReleaseReferences @CanReleaseReferences} {@link #scope()} can
 * be in any one of four states at a time:
 *
 * <ul>
 *   <li>In <b>uninitialized</b> state, the provider's strong reference and its {@link
 *       WeakReference} are both {@code null}.
 *   <li>In <b>cleared</b> state, the provider's strong reference is {@code null}, and its {@link
 *       WeakReference}'s value is {@code null}.
 *   <li>In <b>strong-reference</b> state, the provider's strong reference refers to the cached
 *       value, and its {@link WeakReference} is {@code null}.
 *   <li>In <b>weak-reference</b> state, the provider's strong reference is {@code null}, and its
 *       {@link WeakReference}'s value is not {@code null}.
 * </ul>
 *
 * <p>All providers within {@link #scope()} start in <b>uninitialized</b> state.
 *
 * <p>Calling {@link Provider#get()} on a provider within {@link #scope()} transitions it to
 * <b>strong-reference</b> state if it was in <b>uninitialized</b> or <b>empty</b> state.
 *
 * <p>{@link #releaseStrongReferences()} transitions all providers within {@link #scope()} that are
 * in <b>strong-reference</b> state to <b>weak-reference</b> state.
 *
 * <p>{@link #restoreStrongReferences()} transitions all providers within {@link #scope()} that are
 * in <b>weak-reference</b> state to <b>strong-reference</b> state.
 *
 * <p>If garbage collection clears the {@link WeakReference} for any provider within {@link
 * #scope()} that is in <b>weak-reference</b> state, that provider transitions to <b>cleared</b>
 * state.
 *
 * <p><img src="doc-files/ReleasableReferenceManager-statemachine.png"
 * alt="ReleasableReferenceManager state machine">
 *
 * <p>This interface is implemented by Dagger.
 *
 * @since 2.8
 */
@Beta
@GwtIncompatible
public interface ReleasableReferenceManager {

  /** The scope whose references are managed by this object. */
  Class<? extends Annotation> scope();

  /**
   * Releases the strong references held by all providers in this {@linkplain #scope() scope} to the
   * objects previously returned by {@link Provider#get()}, leaving only {@link WeakReference}s.
   *
   * <p>If any such {@link WeakReference} is cleared during garbage collection, the next call to
   * that {@link Provider#get()} will execute the underlying binding again, and the provider will
   * hold a strong reference to the new returned value.
   *
   * <p>Calls to {@link Provider#get()} on any such provider return the weakly-referenced object
   * until the {@link WeakReference} is cleared or {@link #restoreStrongReferences()} is called.
   */
  void releaseStrongReferences();

  /**
   * Restores strong references for all providers in this {@linkplain #scope() scope} that were
   * previously {@linkplain #releaseStrongReferences() released} but whose {@link WeakReference} has
   * not yet been cleared during garbage collection.
   */
  void restoreStrongReferences();
}
