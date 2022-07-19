/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.hilt.android.plugin.util

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getKaptConfigName(variant: com.android.build.gradle.api.BaseVariant): String {
  // KAPT config names don't follow the usual convention:
  // <Variant Name>   -> <Config Name>
  // debug            -> kaptDebug
  // debugAndroidTest -> kaptAndroidTestDebug
  // debugUnitTest    -> kaptTestDebug
  // release          -> kaptRelease
  // releaseUnitTest  -> kaptTestRelease
  return when (variant) {
    is com.android.build.gradle.api.TestVariant ->
      "kaptAndroidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}"
    is com.android.build.gradle.api.UnitTestVariant ->
      "kaptTest${variant.name.substringBeforeLast("UnitTest").capitalize()}"
    else ->
      "kapt${variant.name}"
  }
}