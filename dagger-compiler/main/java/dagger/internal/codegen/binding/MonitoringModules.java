/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import androidx.room.compiler.codegen.XClassName;
import dagger.internal.codegen.base.ClearableCache;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Keeps track of modules generated in the current round by {@link MonitoringModuleGenerator}. */
@Singleton
public final class MonitoringModules implements ClearableCache {
  Set<XClassName> cache = new HashSet<>();

  @Inject
  MonitoringModules() {}

  public void add(XClassName module) {
    cache.add(module);
  }

  public boolean isEmpty() {
    return cache.isEmpty();
  }

  @Override
  public void clearCache() {
    cache.clear();
  }
}
