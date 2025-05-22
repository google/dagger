import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
  id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

dependencies {
  api(project(":dagger"))
  api(libs.javax.inject)
  api(libs.jakarta.inject)
  implementation(libs.findBugs)

  annotationProcessor(project(":hilt-compiler", "unshaded"))
  annotationProcessor(libs.auto.common)
  annotationProcessor(files(project.findXProcessingJar()))
}

daggerBuild {
  type = SoftwareType.JVM_LIBRARY
  isPublished = true
}

kotlin { explicitApi() }
