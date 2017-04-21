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
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.lang.annotation.Annotation;

/**
 * A {@link TypedReleasableReferenceManager} that decorates another {@link
 * ReleasableReferenceManager} with a metadata annotation.
 *
 * <p>For each scope that requires a {@link ReleasableReferenceManager}, the generated component
 * implementation has a field that implements that manager. For every {@link
 * TypedReleasableReferenceManager} that is required for that scope, the component uses this class
 * to decorate the field with the metadata annotation.
 *
 * @param <M> the type of the metadata annotation
 */
@GwtIncompatible
public final class TypedReleasableReferenceManagerDecorator<M extends Annotation>
    implements TypedReleasableReferenceManager<M> {

  private final ReleasableReferenceManager delegate;
  private final M metadata;

  /**
   * Constructs a manager that delegates {@link #releaseStrongReferences()} and {@link
   * #releaseStrongReferences()} to {@code delegate}.
   */
  public TypedReleasableReferenceManagerDecorator(ReleasableReferenceManager delegate, M metadata) {
    this.delegate = checkNotNull(delegate);
    this.metadata = checkNotNull(metadata);
  }

  @Override
  public Class<? extends Annotation> scope() {
    return delegate.scope();
  }

  @Override
  public M metadata() {
    return metadata;
  }

  @Override
  public void releaseStrongReferences() {
    delegate.releaseStrongReferences();
  }

  @Override
  public void restoreStrongReferences() {
    delegate.restoreStrongReferences();
  }
}
