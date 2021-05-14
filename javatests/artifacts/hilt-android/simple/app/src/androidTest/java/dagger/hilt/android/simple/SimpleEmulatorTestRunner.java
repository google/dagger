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

package dagger.hilt.android.simple;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import dagger.hilt.android.testing.CustomTestApplication;

/** A custom runner to setup the emulator application class for tests. */
@CustomTestApplication(BaseTestApplication.class)
public final class SimpleEmulatorTestRunner extends AndroidJUnitRunner {

  @Override
  public Application newApplication(ClassLoader cl, String className, Context context)
      throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    return super.newApplication(cl, SimpleEmulatorTestRunner_Application.class.getName(), context);
  }
}
