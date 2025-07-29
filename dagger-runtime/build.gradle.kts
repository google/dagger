import dagger.gradle.build.SoftwareType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
  id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

dependencies {
  api(libs.javax.inject)
  api(libs.jakarta.inject)
  api(libs.jspecify)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.guava.jre)
}

daggerBuild {
  type = SoftwareType.JVM_LIBRARY
  isPublished = true
}

kotlin { explicitApi() }
