/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;

/** Defines a {@link HiltAndroidApp} for {@link MyAppPreviousCompilationTest}. */
public final class MyAppPreviousCompilation {

  @HiltAndroidApp(Application.class)
  public static final class MyApp extends Hilt_MyAppPreviousCompilation_MyApp {}

  private MyAppPreviousCompilation() {}
}
