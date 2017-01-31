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

package dagger.android;

import static android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
import static android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.ComponentCallbacks2;
import android.support.annotation.IntDef;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * Annotates an integer element to indicate that its value should be one of the constants defined in
 * {@link ComponentCallbacks2}.
 *
 * @since 2.8
 */
@Documented
@Retention(SOURCE)
@IntDef({
  TRIM_MEMORY_BACKGROUND,
  TRIM_MEMORY_COMPLETE,
  TRIM_MEMORY_MODERATE,
  TRIM_MEMORY_RUNNING_CRITICAL,
  TRIM_MEMORY_RUNNING_LOW,
  TRIM_MEMORY_RUNNING_MODERATE,
  TRIM_MEMORY_UI_HIDDEN
})
@interface OnTrimMemoryValue {}
