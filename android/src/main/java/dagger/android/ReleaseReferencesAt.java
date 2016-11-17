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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import dagger.releasablereferences.CanReleaseReferences;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * Annotates {@linkplain javax.inject.Scope scopes} to associate them with a low-memory threshold
 * level, as described in {@link android.app.Application#onTrimMemory(int)}.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @Documented}
 *   {@literal @Retention}(RUNTIME)
 *   {@literal @Target}({TYPE, METHOD})
 *   {@literal @ReleaseReferencesAt}(TRIM_MEMORY_BACKGROUND)
 *   {@literal @Scope}
 *   public {@literal @interface} MyScope {}</pre>
 *
 * <p>Any scope annotated with {@code @ReleaseReferencesAt} can {@linkplain CanReleaseReferences
 * release its references}.
 *
 * <p>In order to release references in low-memory conditions, inject an {@link
 * AndroidMemorySensitiveReferenceManager} into your {@link android.app.Application} and delegate
 * {@link android.app.Application#onTrimMemory(int)} to it.
 *
 * @since 2.8
 */
@Beta
@Documented
@GwtIncompatible
@Target(ANNOTATION_TYPE)
@CanReleaseReferences
public @interface ReleaseReferencesAt {
  /**
   * If {@link AndroidMemorySensitiveReferenceManager#onTrimMemory(int)} is called with a value
   * greater than or equal to this, the scope's references will be released. If it is called with a
   * value less than this, the scope's references will be restored.
   *
   * <p>Use one of the constants defined in {@link android.content.ComponentCallbacks2}.
   */
  @OnTrimMemoryValue
  int value();
}
