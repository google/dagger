import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
  id(libs.plugins.shadow.get().pluginId)
}

dependencies {
  implementation(project(":dagger"))
  implementation(project(":dagger-compiler", "unshaded"))
  implementation(project(":dagger-spi", "unshaded"))

  implementation(libs.auto.value.annotations)
  annotationProcessor(libs.auto.value.compiler)
  implementation(libs.auto.service.annotations)
  annotationProcessor(libs.auto.service.compiler)
  implementation(libs.findBugs)
  implementation(libs.javaPoet)
  implementation(libs.gradleIncap.annotations)
  annotationProcessor(libs.gradleIncap.compiler)
  implementation(libs.guava.failureAccess)
  implementation(libs.guava.jre)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinPoet)
  implementation(libs.ksp.api)

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
