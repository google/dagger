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

package dagger.android;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.Set;
import javax.inject.Inject;

/**
 * Releases references in {@link ReleaseReferencesAt} {@linkplain javax.inject.Scope scopes} in
 * low-memory conditions.
 *
 * <p>In order to release references in low-memory conditions, inject an {@code
 * AndroidMemorySensitiveReferenceManager} into your {@link android.app.Application} and delegate
 * {@link android.app.Application#onTrimMemory(int)} to it.
 *
 * <p>For example:
 *
 * <pre>
 *   class MyApplication extends Application {
 *     {@literal @Inject} AndroidMemorySensitiveReferenceManager manager;
 *
 *     public void onTrimMemory(int level) {
 *       manager.onTrimMemory(level);
 *     }
 *   }</pre>
 *
 * @since 2.8
 */
@Beta
@GwtIncompatible
public final class AndroidMemorySensitiveReferenceManager {

  private final Set<TypedReleasableReferenceManager<ReleaseReferencesAt>> managers;

  @Inject
  AndroidMemorySensitiveReferenceManager(
      Set<TypedReleasableReferenceManager<ReleaseReferencesAt>> managers) {
    this.managers = managers;
  }

  /**
   * Releases references for {@link ReleaseReferencesAt} scopes whose {@link
   * ReleaseReferencesAt#value()} is less than or equal to {@code level}. Restores references for
   * scopes whose {@link ReleaseReferencesAt#value()} is greater than {@code level}.
   *
   * @see android.app.Application#onTrimMemory(int)
   */
  public void onTrimMemory(int level) {
    for (TypedReleasableReferenceManager<ReleaseReferencesAt> manager : managers) {
      if (level >= manager.metadata().value()) {
        manager.releaseStrongReferences();
      } else {
        manager.restoreStrongReferences();
      }
    }
  }
}
