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
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.Variant

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getKaptConfigName(variant: com.android.build.gradle.api.BaseVariant) =
  getConfigName(prefix = "kapt", variant = variant)

@Suppress("DEPRECATION") // Older variant API is deprecated
internal fun getKspConfigName(variant: com.android.build.gradle.api.BaseVariant) =
  getConfigName(prefix = "ksp", variant = variant)

@Suppress("DEPRECATION") // Older variant API is deprecated
private fun getConfigName(
  prefix: String,
  mode: VariantNameMode = VariantNameMode.FULL,
  variant: com.android.build.gradle.api.BaseVariant,
) =
  getConfigName(
    prefix = prefix,
    mode = mode,
    variantFullName = variant.name,
    variantFlavorName = variant.flavorName,
    isAndroidTest = variant is com.android.build.gradle.api.TestVariant,
    isUnitTest = variant is com.android.build.gradle.api.UnitTestVariant,
  )

internal fun getKaptConfigNames(variant: Variant) =
  VariantNameMode.entries.map { mode ->
    getConfigName(prefix = "kapt", mode = mode, variant = variant)
  }

internal fun getKspConfigNames(variant: Variant) =
  VariantNameMode.entries.map { mode ->
    getConfigName(prefix = "ksp", mode = mode, variant = variant)
  }

private fun getConfigName(
  prefix: String,
  mode: VariantNameMode = VariantNameMode.FULL,
  variant: Variant,
) =
  getConfigName(
    prefix = prefix,
    mode = mode,
    variantFullName = variant.name,
    variantFlavorName = variant.flavorName,
    isAndroidTest = variant is AndroidTest,
    isUnitTest = variant is TestVariant,
  )

private fun getConfigName(
  prefix: String,
  mode: VariantNameMode,
  variantFullName: String,
  variantFlavorName: String?,
  isAndroidTest: Boolean,
  isUnitTest: Boolean,
): String {
  // Config names don't follow the usual task name conventions:
  // <Variant Name>   -> <Config Name>
  // debug            -> <prefix>Debug
  // debugAndroidTest -> <prefix>AndroidTestDebug
  // debugUnitTest    -> <prefix>TestDebug
  // release          -> <prefix>Release
  // releaseUnitTest  -> <prefix>TestRelease
  return buildString {
    append(prefix)
    if (isAndroidTest) {
      append("AndroidTest")
    } else if (isUnitTest) {
      append("Test")
    }
    append(
      when (mode) {
        VariantNameMode.BASE -> ""
        VariantNameMode.FLAVOR -> checkNotNull(variantFlavorName)
        VariantNameMode.FULL ->
          when {
            isAndroidTest -> variantFullName.substringBeforeLast("AndroidTest")
            isUnitTest -> variantFullName.substringBeforeLast("UnitTest")
            else -> variantFullName
          }
      }.capitalize()
    )
  }
}

private enum class VariantNameMode {
  BASE,
  FLAVOR,
  FULL,
}
