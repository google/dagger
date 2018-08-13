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

import android.support.v4.app.Fragment;
import dagger.Module;
import dagger.android.AndroidInjectionModule;
import dagger.android.AndroidInjector;
import dagger.internal.Beta;
import dagger.multibindings.Multibinds;
import java.util.Map;

/**
 * Configures bindings to ensure the usability of {@code dagger.android} and {@code
 * dagger.android.support} framework classes. This module should be installed in the root-most
 * component which will use these types.
 */
@Beta
@Module(includes = AndroidInjectionModule.class)
public abstract class AndroidSupportInjectionModule {
  @Multibinds
  abstract Map<Class<? extends Fragment>, AndroidInjector.Factory<? extends Fragment>>
      supportFragmentInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends Fragment>>
      supportFragmentInjectorFactoriesWithStringKeys();

  private AndroidSupportInjectionModule() {}
}
