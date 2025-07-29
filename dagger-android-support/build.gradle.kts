import dagger.gradle.build.SoftwareType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
}

dependencies {
  api(project(":dagger"))
  api(project(":dagger-android"))
  implementation(project(":dagger-lint-aar"))
  api(libs.androidx.annotations)
  api(libs.androidx.activity)
  api(libs.androidx.appcompat)
  api(libs.androidx.fragment)
  compileOnly(libs.errorprone.annotations)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.robolectric)
}

daggerBuild {
  type = SoftwareType.ANDROID_LIBRARY
  isPublished = true
}

android { namespace = "dagger.android.support" }
