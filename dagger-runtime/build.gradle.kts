plugins {
  alias(libs.plugins.dagger.kotlinJvm)
  alias(libs.plugins.dagger.publish)
  alias(libs.plugins.binaryCompatibilityValidator)
}

// TODO: Configure via convention plugin
sourceSets {
  main {
    java.srcDirs("java")
    kotlin.srcDirs("java")
    resources.srcDirs("resources")
  }
  test {
    java.srcDirs("javatests")
    kotlin.srcDirs("javatests")
  }
}

dependencies {
  api(libs.javax.inject)
  api(libs.jakarta.inject)
  api(libs.jspecify)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.guava.jre)
}

kotlin { explicitApi() }
