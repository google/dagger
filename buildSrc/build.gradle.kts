plugins { `kotlin-dsl` }

kotlin { jvmToolchain { languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of)) } }

dependencies {
  implementation(gradleApi())
  implementation(libs.kotlin.gradlePlugin)
  implementation(libs.publishPlugin)
  implementation(libs.shadowPlugin)
  implementation(libs.binaryCompatibilityValidatorPlugin)
}

gradlePlugin {
  plugins {
    register("build") {
      id = libs.plugins.daggerBuild.get().pluginId
      implementationClass = "dagger.gradle.build.DaggerConventionPlugin"
    }
  }
}
