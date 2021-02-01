package dagger.hilt.android.plugin.util

import com.android.Version

/**
 * Simple Android Gradle Plugin version class since there is no public API one. b/175816217
 */
internal data class SimpleAGPVersion(
  val major: Int,
  val minor: Int,
) : Comparable<SimpleAGPVersion> {

  override fun compareTo(other: SimpleAGPVersion): Int {
    return compareValuesBy(
      this,
      other,
      compareBy(SimpleAGPVersion::major).thenBy(SimpleAGPVersion::minor)
    ) { it }
  }

  companion object {

    val ANDROID_GRADLE_PLUGIN_VERSION by lazy { parse(Version.ANDROID_GRADLE_PLUGIN_VERSION) }

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
  }
}
