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

/**
 * Simple Android Gradle Plugin version class since there is no public API one. b/175816217
 */
data class SimpleAGPVersion(
  val major: Int,
  val minor: Int,
) : Comparable<SimpleAGPVersion> {

  override fun toString(): String {
    return "$major.$minor"
  }

  override fun compareTo(other: SimpleAGPVersion): Int {
    return compareValuesBy(
      this,
      other,
      compareBy(SimpleAGPVersion::major).thenBy(SimpleAGPVersion::minor)
    ) { it }
  }

  companion object {

    // TODO(danysantiago): Migrate to AndroidPluginVersion once it is available (b/175816217)
    val ANDROID_GRADLE_PLUGIN_VERSION by lazy {
      val clazz =
        findClass("com.android.Version")
          ?: findClass("com.android.builder.model.Version")
      if (clazz != null) {
        return@lazy parse(clazz.getField("ANDROID_GRADLE_PLUGIN_VERSION").get(null) as String)
      }
      error(
        "Unable to obtain AGP version. It is likely that the AGP version being used is too old."
      )
    }

    fun parse(version: String?) =
      tryParse(version) ?: error("Unable to parse AGP version: $version")

    private fun tryParse(version: String?): SimpleAGPVersion? {
      if (version == null) {
        return null
      }

      val parts = version.split('.')
      if (parts.size == 1) {
        return SimpleAGPVersion(parts[0].toInt(), 0)
      }

      return SimpleAGPVersion(parts[0].toInt(), parts[1].toInt())
    }

    private fun findClass(fqName: String) =
      try {
        Class.forName(fqName)
      } catch (ex: ClassNotFoundException) {
        null
      }
  }
}
