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

import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.Component
import com.android.build.api.variant.UnitTest

internal fun getKaptConfigName(variant: Component) = getConfigName(variant, "kapt")

internal fun getKspConfigName(variant: Component) = getConfigName(variant, "ksp")

internal fun getConfigName(variant: Component, prefix: String? = null): String {
  // Config names don't follow the usual task name conventions:
  // <Variant Name>   -> <Config Name>
  // debug            -> <prefix>Debug
  // debugAndroidTest -> <prefix>AndroidTestDebug
  // debugUnitTest    -> <prefix>TestDebug
  // release          -> <prefix>Release
  // releaseUnitTest  -> <prefix>TestRelease
  return when (variant) {
    is AndroidTest -> "androidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}"
    is UnitTest -> "test${variant.name.substringBeforeLast("UnitTest").capitalize()}"
    else -> variant.name
  }.let { name ->
    prefix?.let { "$prefix${name.capitalize()}" } ?: name
  }
}

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getKaptConfigName(variant: com.android.build.gradle.api.BaseVariant)
  = getConfigName(variant, "kapt")

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getKspConfigName(variant: com.android.build.gradle.api.BaseVariant)
  = getConfigName(variant, "ksp")

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getConfigName(
  variant: com.android.build.gradle.api.BaseVariant,
  prefix: String? = null,
): String {
  // Config names don't follow the usual task name conventions:
  // <Variant Name>   -> <Config Name>
  // debug            -> <prefix>Debug
  // debugAndroidTest -> <prefix>AndroidTestDebug
  // debugUnitTest    -> <prefix>TestDebug
  // release          -> <prefix>Release
  // releaseUnitTest  -> <prefix>TestRelease
  return when (variant) {
    is com.android.build.gradle.api.TestVariant ->
      "androidTest${variant.name.substringBeforeLast("AndroidTest").capitalize()}"
    is com.android.build.gradle.api.UnitTestVariant ->
      "test${variant.name.substringBeforeLast("UnitTest").capitalize()}"
    else -> variant.name
  }.let { name ->
    prefix?.let { "$prefix${name.capitalize()}" } ?: name
  }
}