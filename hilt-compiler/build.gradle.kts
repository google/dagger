import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findBootstrapCompilerJar
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
  id(libs.plugins.kapt.get().pluginId)
  id(libs.plugins.shadow.get().pluginId)
}

dependencies {
  implementation(project(":dagger"))
  implementation(project(":dagger-compiler", "unshaded"))
  implementation(project(":dagger-spi", "unshaded"))

  implementation(libs.auto.value.annotations)
  kapt(libs.auto.value.compiler)
  implementation(libs.auto.service.annotations)
  kapt(libs.auto.service.compiler)
  implementation(libs.findBugs)
  implementation(libs.gradleIncap.annotations)
  kapt(libs.gradleIncap.compiler)
  implementation(libs.guava.failureAccess)
  implementation(libs.guava.jre)
  implementation(libs.javaPoet)
  implementation(libs.ksp.api)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinPoet.javaPoet)

  kapt(files(project.findBootstrapCompilerJar()))

  // These dependencies are shaded into dagger-spi
  compileOnly(libs.auto.common)
  compileOnly(files(project.findXProcessingJar()))
}

daggerBuild {
  type = SoftwareType.PROCESSOR
  isPublished = true

  shading {
    relocate("com.google.auto.common", "dagger.spi.internal.shaded.auto.common")
    relocate("androidx.room", "dagger.spi.internal.shaded.androidx.room")
  }
}
