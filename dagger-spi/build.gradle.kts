import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.kotlinJvm.get().pluginId)
  id(libs.plugins.shadow.get().pluginId)
}

dependencies {
  implementation(project(":dagger"))

  implementation(libs.findBugs)
  implementation(libs.auto.value.annotations)
  annotationProcessor(libs.auto.value.compiler)
  implementation(libs.ksp.api)
  implementation(libs.guava.failureAccess)
  implementation(libs.guava.jre)
  implementation(libs.javaPoet)
  implementation(libs.javax.inject)

  // These dependencies will be shaded (included) within the artifact of this project and are
  // shared with other processors, such as dagger-compiler.
  val shaded by configurations.getting
  shaded(libs.auto.common)
  shaded(files(project.findXProcessingJar()))
}

daggerBuild {
  type = SoftwareType.PROCESSOR
  isPublished = true

  shading {
    relocate("com.google.auto.common", "dagger.spi.internal.shaded.auto.common")
    relocate("androidx.room", "dagger.spi.internal.shaded.androidx.room")
  }
}
