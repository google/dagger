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

package dagger.hilt.android.testing.testinstallin;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Bar;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Foo;
import javax.inject.Inject;

/**
 * An application to test {@link dagger.hilt.testing.TestInstallIn} are ignored when using {@link
 * HiltAndroidApp}.
 *
 * <p>This class is used by {@link TestInstallInAppTest}.
 */
@HiltAndroidApp(Application.class)
public class TestInstallInApp extends Hilt_TestInstallInApp {
  @Inject Foo foo;
  @Inject Bar bar;
}
