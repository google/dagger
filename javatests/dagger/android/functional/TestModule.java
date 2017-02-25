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

package dagger.android.functional;

import dagger.Module;
import dagger.Provides;

@Module
class TestModule {
  int releasedWhenUiHiddenCalls;
  int releasedWhenModerateCalls;

  @Provides
  @ReleaseWhenUiHidden
  @InScope(ReleaseWhenUiHidden.class)
  Object releasedWhenUiHidden() {
    ++releasedWhenUiHiddenCalls;
    return new Object();
  }

  @Provides
  @ReleaseWhenModerate
  @InScope(ReleaseWhenModerate.class)
  Object releasedWhenModerate() {
    ++releasedWhenModerateCalls;
    return new Object();
  }
}
