import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar
import dagger.gradle.build.findXProcessingTestingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
}

dependencies {
  implementation(project(":dagger"))
  implementation(project(":dagger-spi", "unshaded"))
  implementation(project(":dagger-compiler", "unshaded"))
  implementation(files(project.findXProcessingJar()))
  implementation(files(project.findXProcessingTestingJar()))
  implementation(libs.auto.value.annotations)
  annotationProcessor(libs.auto.value.compiler)
  implementation(libs.checkerFramework)
  implementation(libs.guava.jre)
  implementation(libs.javaCompileTesting)
  implementation(libs.javax.inject)
  implementation(libs.junit)
  implementation(libs.ksp.api)
  implementation(libs.truth)
}

daggerBuild {
  type = SoftwareType.PROCESSOR
  isPublished = false
}
