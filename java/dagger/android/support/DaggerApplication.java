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

package dagger.android.support;

import dagger.android.AndroidInjector;

/**
 * An {@link Application} that injects its members and can be used to inject classes that the
 * Android framework instantiates. Injection is performed in {@link #onCreate()} or the first call
 * to {@link AndroidInjection#inject(ContentProvider)}, whichever happens first.
 */
// TODO(ronshapiro): deprecate and remove this class
public abstract class DaggerApplication extends dagger.android.DaggerApplication {
  @Override
  protected abstract AndroidInjector<? extends DaggerApplication> applicationInjector();
}
