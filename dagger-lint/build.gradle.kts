import dagger.gradle.build.SoftwareType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
}

dependencies {
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)
  compileOnly(libs.auto.service.annotations)
  testImplementation(libs.junit)
  testImplementation(libs.lint.checks)
  testImplementation(libs.lint.tests)
}

daggerBuild {
  type = SoftwareType.JVM_LIBRARY
  isPublished = true
}
