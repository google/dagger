package dagger.hilt.android.plugin.util

import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.gradle.api.Project

/* Checks if a file is a .class file. */
fun File.isClassFile() = this.isFile && this.extension == "class"

/* Checks if a Zip entry is a .class file. */
fun ZipEntry.isClassFile() = !this.isDirectory && this.name.endsWith(".class")

/* Checks if a file is a .jar file. */
fun File.isJarFile() = this.isFile && this.extension == "jar"

/* Executes the given [block] function over each [ZipEntry] in this [ZipInputStream]. */
fun ZipInputStream.forEachZipEntry(block: (InputStream, ZipEntry) -> Unit) = use {
  var inputEntry = nextEntry
  while (inputEntry != null) {
    block(this, inputEntry)
    inputEntry = nextEntry
  }
}

/* Gets the Android Sdk Path. */
fun Project.getSdkPath(): File {
  val localPropsFile = rootProject.projectDir.resolve("local.properties")
  if (localPropsFile.exists()) {
    val localProps = Properties()
    localPropsFile.inputStream().use { localProps.load(it) }
    val localSdkDir = localProps["sdk.dir"]?.toString()
    if (localSdkDir != null) {
      val sdkDirectory = File(localSdkDir)
      if (sdkDirectory.isDirectory) {
        return sdkDirectory
      }
    }
  }
  return getSdkPathFromEnvironmentVariable()
}

private fun getSdkPathFromEnvironmentVariable(): File {
  // Check for environment variables, in the order AGP checks.
  listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach {
    val envValue = System.getenv(it)
    if (envValue != null) {
      val sdkDirectory = File(envValue)
      if (sdkDirectory.isDirectory) {
        return sdkDirectory
      }
    }
  }
  // Only print the error for SDK ROOT since ANDROID_HOME is deprecated but we first check
  // it because it is prioritized according to the documentation.
  error("ANDROID_SDK_ROOT environment variable is not set")
}
