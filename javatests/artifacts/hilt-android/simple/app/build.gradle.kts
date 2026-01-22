/*
 * Copyright (C) 2020 The Dagger Authors.
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

import java.io.File

plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
}

data class AgpVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val qualifierName: String?,
  val qualifierVersion: Int?,
): Comparable<AgpVersion> {
  override fun compareTo(other: AgpVersion): Int {
    val versionComparison = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    return if (versionComparison != 0) {
       versionComparison
    } else {
      // If the versions are equal then compare qualifiers:
      when {
        qualifierName == null && other.qualifierName == null -> 0
        qualifierName == null && other.qualifierName != null -> -1
        qualifierName != null && other.qualifierName == null -> 1
        else -> compareValuesBy(
          this, other,
          { it.qualifierName!! },
          { it.qualifierVersion!! },
        )
      }
    }
  }

  companion object {
    val VERSION_REGEX = Regex(
      "(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)" +
          "(?:-(?<qualifierName>[a-zA-Z]+)(?<qualifierVersion>\\d+))?"
    )

    fun parse(versionStr: String): AgpVersion {
      return VERSION_REGEX.matchEntire(versionStr)?.let { match ->
        AgpVersion(
            major = match.groups["major"]!!.value.toInt(),
            minor = match.groups["minor"]!!.value.toInt(),
            patch = match.groups["patch"]!!.value.toInt(),
            qualifierName = match.groups["qualifierName"]?.value,
            qualifierVersion = match.groups["qualifierVersion"]?.value?.toInt()
        )
      } ?: error("Version string has incorrect format: $versionStr")
    }
  }
}

// Gets additional test directories to be added to test and androidTest source
// sets. If the directory name is appended with '-agp-x.x.x' then the directory
// is conditionally added based on the AGP version of the project.
fun getAdditionalTestDirs(variant: String): List<String> {
    val testDirs = mutableMapOf(
        "androidTest" to mutableListOf<String>(),
        "sharedTest" to mutableListOf("src/sharedTest/java"),
        "test" to mutableListOf<String>()
    )
    val suffix = "-agp-"

    val agpVersionString = properties["agp_version"] as String
    val agpVersion = AgpVersion.parse(agpVersionString)

    File("${project.projectDir.absolutePath}/src").listFiles()?.forEach { file ->
        if (file.isDirectory) {
            val indexOf = file.name.indexOf(suffix)
            if (indexOf != -1) {
                try {
                    val dirAgpVersionStr = file.name.substring(indexOf + suffix.length)
                    val dirAgpVersion = AgpVersion.parse(dirAgpVersionStr)
                    if (agpVersion >= dirAgpVersion) {
                        val dirName = file.name.substring(0, indexOf)
                        testDirs[dirName]?.add("src/${file.name}/java")
                    }
                } catch (e: IllegalArgumentException) {
                    // Handle cases where the version string is not valid
                    println("Warning: Could not parse version from directory name: ${file.name}")
                }
            }
        }
    }
    return (testDirs[variant] ?: emptyList()) + (testDirs["sharedTest"] ?: emptyList())
}

android {
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dagger.hilt.android.simple"
        minSdk = 16
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "dagger.hilt.android.simple.SimpleEmulatorTestRunner"
    }
    namespace = "dagger.hilt.android.simple"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        checkReleaseBuilds = false
    }
    sourceSets {
        named("test").configure {
            java.srcDirs(getAdditionalTestDirs("test"))
        }
        named("androidTest").configure {
            java.srcDirs(getAdditionalTestDirs("androidTest"))
        }
    }
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
        }
        create("pro") {
            dimension = "tier"
            matchingFallbacks += "free"
        }
    }
}

hilt {
    enableAggregatingTask = true
}

val dagger_version: String by project

configurations.all {
    resolutionStrategy.eachDependency {
        if (dagger_version == "LOCAL-SNAPSHOT" && requested.group == "com.google.dagger") {
            useVersion("LOCAL-SNAPSHOT")
            because("LOCAL-SNAPSHOT should act as latest version.")
        }
    }
}

dependencies {
    implementation(project(":feature"))
    implementation(project(":lib"))
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.dagger:hilt-android:$dagger_version")
    annotationProcessor("com.google.dagger:hilt-compiler:$dagger_version")

    testImplementation("com.google.truth:truth:1.0.1")
    testImplementation("junit:junit:4.13")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.core:core:1.3.2")
    testImplementation("androidx.test.ext:junit:1.1.3")
    testImplementation("androidx.test:runner:1.4.0")
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
    testImplementation("com.google.dagger:hilt-android-testing:$dagger_version")
    testAnnotationProcessor("com.google.dagger:hilt-compiler:$dagger_version")

    androidTestImplementation("com.google.truth:truth:1.0.1")
    androidTestImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$dagger_version")
    androidTestAnnotationProcessor("com.google.dagger:hilt-compiler:$dagger_version")

    // To help us catch usages of Guava APIs for Java 8 in the '-jre' variant.
    annotationProcessor("com.google.guava:guava:28.1-android")
    testAnnotationProcessor("com.google.guava:guava:28.1-android")
    androidTestAnnotationProcessor("com.google.guava:guava:28.1-android")

    // To help us catch version skew related issues in hilt extensions.
    // TODO(bcorso): Add examples testing the actual API.
    implementation("androidx.hilt:hilt-work:1.0.0")
    annotationProcessor("androidx.hilt:hilt-compiler:1.0.0")
    testAnnotationProcessor("androidx.hilt:hilt-compiler:1.0.0")
    androidTestAnnotationProcessor("androidx.hilt:hilt-compiler:1.0.0")
}
