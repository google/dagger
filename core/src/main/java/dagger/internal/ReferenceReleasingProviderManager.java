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

import dagger.releasablereferences.ReleasableReferenceManager;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link ReleasableReferenceManager} that forwards calls to a dynamic set of weakly-referenced
 * {@link ReferenceReleasingProvider}s.
 *
 * @see <a href="http://google.github.io/dagger/users-guide.html#releasable-references">Releasable
 *     references</a>
 */
@GwtIncompatible
public final class ReferenceReleasingProviderManager implements ReleasableReferenceManager {

  private final Class<? extends Annotation> scope;
  private final Queue<WeakReference<ReferenceReleasingProvider<?>>> providers =
      new ConcurrentLinkedQueue<WeakReference<ReferenceReleasingProvider<?>>>();

  public ReferenceReleasingProviderManager(Class<? extends Annotation> scope) {
    this.scope = checkNotNull(scope);
  }

  /**
   * Adds a weak reference to {@code provider}.
   */
  public void addProvider(ReferenceReleasingProvider<?> provider) {
    providers.add(new WeakReference<ReferenceReleasingProvider<?>>(provider));
  }

  @Override
  public Class<? extends Annotation> scope() {
    return scope;
  }

  /**
   * {@inheritDoc} Calls {@link ReferenceReleasingProvider#releaseStrongReference()} on all
   * providers that have been {@linkplain #addProvider(ReferenceReleasingProvider) added} and that
   * are still weakly referenced.
   */
  @Override
  public void releaseStrongReferences() {
    execute(Operation.RELEASE);
  }

  /**
   * {@inheritDoc} Calls {@link ReferenceReleasingProvider#restoreStrongReference()} on all
   * providers that have been {@linkplain #addProvider(ReferenceReleasingProvider) added} and that
   * are still weakly referenced.
   */
  @Override
  public void restoreStrongReferences() {
    execute(Operation.RESTORE);
  }

  private void execute(Operation operation) {
    Iterator<WeakReference<ReferenceReleasingProvider<?>>> iterator = providers.iterator();
    while (iterator.hasNext()) {
      ReferenceReleasingProvider<?> provider = iterator.next().get();
      if (provider == null) {
        iterator.remove();
      } else {
        operation.execute(provider);
      }
    }
  }

  private enum Operation {
    RELEASE {
      @Override
      void execute(ReferenceReleasingProvider<?> provider) {
        provider.releaseStrongReference();
      }
    },
    RESTORE {
      @Override
      void execute(ReferenceReleasingProvider<?> provider) {
        provider.restoreStrongReference();
      }
    },
    ;

    abstract void execute(ReferenceReleasingProvider<?> provider);
  }
}
