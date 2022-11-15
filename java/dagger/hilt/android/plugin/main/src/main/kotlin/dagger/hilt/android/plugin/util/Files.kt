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

/**
 * Get a sequence of files in a platform independent order from walking this
 * file/directory recursively.
 */
fun File.walkInPlatformIndependentOrder() = this.walkTopDown().sortedBy {
  it.toRelativeString(this).replace(File.separatorChar, '/')
}

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
