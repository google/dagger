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

package dagger.hilt.android.plugin.util

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.component.AndroidTest
import com.android.build.api.component.UnitTest
import com.android.build.api.extension.AndroidComponentsExtension
import com.android.build.api.extension.VariantSelector
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.LibraryVariant

/**
 * Compatibility method to go over each Variant as Component (the newer
 * 'android.build.api.variant.Variant' version, not the older one
 * 'com.android.build.gradle.api.BaseVariant')
 */
@Suppress("UnstableApiUsage")
fun AndroidComponentsExtension<*, *, *>.allTestVariants(
  onAndroidTest: (AndroidTest) -> Unit,
  onUnitTest: (UnitTest) -> Unit
) {
  if (
    findClass("com.android.build.api.AndroidPluginVersion") != null &&
    this.pluginVersion >= AndroidPluginVersion(7, 0).beta(1)
  ) {
    this.onVariants { variant ->
      when (variant) {
        is ApplicationVariant -> variant.androidTest
        is LibraryVariant -> variant.androidTest
        else -> null
      }?.let { onAndroidTest(it) }
      variant.unitTest?.let { onUnitTest(it) }
    }
  } else {
    // This methods were removed in 7.0.0-beta01 but are available in 4.2.0
    AndroidComponentsExtension::class.java.getDeclaredMethod(
      "androidTests", VariantSelector::class.java, Function1::class.java
    ).invoke(this, this.selector().all(), onAndroidTest)
    AndroidComponentsExtension::class.java.getDeclaredMethod(
      "unitTests", VariantSelector::class.java, Function1::class.java
    ).invoke(this, this.selector().all(), onUnitTest)
  }
}

fun findClass(fqName: String) = try {
  Class.forName(fqName)
} catch (ex: ClassNotFoundException) {
  null
}
