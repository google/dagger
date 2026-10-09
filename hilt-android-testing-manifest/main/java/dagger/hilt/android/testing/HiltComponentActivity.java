/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.hilt.android.testing;

import androidx.activity.ComponentActivity;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * A Hilt-enabled {@link ComponentActivity} for instrumentation tests.
 *
 * <p>Add the {@code hilt-android-testing-manifest} artifact to a debug configuration, then launch
 * this activity from ActivityScenario or a Compose Android test rule.
 */
@AndroidEntryPoint(ComponentActivity.class)
public final class HiltComponentActivity extends Hilt_HiltComponentActivity {}
