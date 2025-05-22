import dagger.gradle.build.SoftwareType
import dagger.gradle.build.findXProcessingJar

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
  id(libs.plugins.kotlinAndroid.get().pluginId)
  id(libs.plugins.binaryCompatibilityValidator.get().pluginId)
}

dependencies {
  api(project(":dagger"))
  implementation(project(":dagger-lint-aar"))
  api(project(":hilt-core"))

  api(libs.androidx.activity)
  api(libs.androidx.annotations)
  api(libs.androidx.fragment)
  api(libs.androidx.lifecycle.viewmodel)
  api(libs.androidx.lifecycle.viewmodel.savedstate)
  api(libs.androidx.savedstate)
  implementation(libs.findBugs)
  implementation(libs.kotlin.stdlib)

  annotationProcessor(project(":dagger-compiler", "unshaded"))
  annotationProcessor(project(":hilt-compiler", "unshaded"))
  annotationProcessor(libs.auto.common)
  annotationProcessor(files(project.findXProcessingJar()))
}

android {
  buildTypes {
    defaultConfig {
      proguardFiles("$projectDir/main/resources/META-INF/com.android.tools/r8/dagger-android.pro")
    }
  }
}

daggerBuild {
  type = SoftwareType.ANDROID_LIBRARY
  isPublished = true
}

android { namespace = "dagger.android" }

kotlin { explicitApi() }
