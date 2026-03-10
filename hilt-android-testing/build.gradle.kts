import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

dependencies {
  api(project(":dagger"))
  api(project(":hilt-android"))

  api(libs.androidx.activity)
  api(libs.androidx.annotations)
  api(libs.androidx.fragment)
  api(libs.androidx.lifecycle.viewmodel)
  api(libs.androidx.lifecycle.viewmodel.savedstate)
  api(libs.androidx.savedstate)
  api(libs.androidx.test.core)
  api(libs.junit)
  implementation(libs.auto.value.annotations)
  implementation(libs.findBugs)
  implementation(libs.kotlin.stdlib)

  annotationProcessor(project(":dagger-compiler", "unshaded"))
  annotationProcessor(project(":hilt-compiler", "unshaded"))
  annotationProcessor(libs.auto.common)
  annotationProcessor(files(project.findXProcessingJar()))
  annotationProcessor(libs.auto.value.compiler)
}

daggerBuild {
  type = SoftwareType.ANDROID_LIBRARY
  isPublished = true
}

android { namespace = "dagger.hilt.android.testing" }

kotlin { explicitApi() }
