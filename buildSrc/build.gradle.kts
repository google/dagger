plugins {
  `kotlin-dsl`
  alias(libs.plugins.lint)
}

kotlin { jvmToolchain { languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of)) } }

dependencies {
  implementation(gradleApi())
  implementation(libs.androidGradlePlugin)
  implementation(libs.kotlin.gradlePlugin)
  implementation(libs.publishPlugin)
  implementation(libs.shadowPlugin)
  implementation(libs.binaryCompatibilityValidatorPlugin)

  lintChecks(libs.androidx.lint)
}

gradlePlugin {
  plugins {
    register("build") {
      id = libs.plugins.daggerBuild.get().pluginId
      implementationClass = "dagger.gradle.build.DaggerConventionPlugin"
    }
  }
}

lint {
  baseline = file("lint-baseline.xml")
}
