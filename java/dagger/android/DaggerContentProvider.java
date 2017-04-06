/*
 * Copyright (C) 2017 The Dagger Authors.
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

import android.content.ContentProvider;
import android.support.annotation.CallSuper;
import dagger.internal.Beta;

/** A {@link ContentProvider} that injects its members in {@link #onCreate()}. */
@Beta
public abstract class DaggerContentProvider extends ContentProvider {
  @CallSuper
  @Override
  public boolean onCreate() {
    AndroidInjection.inject(this);
    return true;
  }
}
