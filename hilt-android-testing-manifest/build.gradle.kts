import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

dependencies {
  api(project(":hilt-android"))

  api(libs.androidx.activity)
  implementation(libs.kotlin.stdlib)

  annotationProcessor(project(":dagger-compiler", "unshaded"))
  annotationProcessor(project(":hilt-compiler", "unshaded"))
  annotationProcessor(libs.auto.common)
  annotationProcessor(files(project.findXProcessingJar()))
}

daggerBuild {
  type = SoftwareType.ANDROID_LIBRARY
  isPublished = true
}

android {
  namespace = "dagger.hilt.android.testing"
  defaultConfig { minSdk = 16 }
}

kotlin { explicitApi() }
