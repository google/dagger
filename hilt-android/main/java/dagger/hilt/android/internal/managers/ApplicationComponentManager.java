/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.android.internal.managers;

import dagger.hilt.internal.GeneratedComponentManager;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the Application.
 */
public final class ApplicationComponentManager implements GeneratedComponentManager<Object> {
  private volatile Object component;
  private final Object componentLock = new Object();
  private final ComponentSupplier componentCreator;

  public ApplicationComponentManager(ComponentSupplier componentCreator) {
    this.componentCreator = componentCreator;
  }

  @Override
  public Object generatedComponent() {
    if (component == null) {
      synchronized (componentLock) {
        if (component == null) {
          component = componentCreator.get();
        }
      }
    }
    return component;
  }
}
