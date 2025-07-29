import dagger.gradle.build.SoftwareType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
}

dependencies {
  api(project(":dagger"))
  implementation(libs.checkerFramework)
  implementation(libs.guava.jre)
}

daggerBuild {
  type = SoftwareType.JVM_LIBRARY
  isPublished = true
}

kotlin { explicitApi() }
