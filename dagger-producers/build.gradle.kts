import dagger.gradle.build.ProjectType

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
  type = ProjectType.LIBRARY
  isPublished = true
}

kotlin { explicitApi() }
