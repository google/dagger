/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.kotlin;

import dagger.Component;
import javax.inject.Singleton;

/**
 * A single-use provider of {@link KotlinMetadataUtil}. Since the returned util has a cache, it is
 * better to reuse the same instance as much as possible, except for going across processor rounds
 * because the cache contains elements.
 */
// TODO(erichang):  Revert this, should be wrapped with a Dagger module.
public final class KotlinMetadataUtils {

  @Singleton
  @Component
  interface MetadataComponent {
    KotlinMetadataUtil get();
  }

  /** Gets the metadata util. */
  public static KotlinMetadataUtil getMetadataUtil() {
    return DaggerKotlinMetadataUtils_MetadataComponent.create().get();
  }

  private KotlinMetadataUtils() {}
}
