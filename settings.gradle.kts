pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "dagger-parent"

gradle.beforeProject {
  // Configures the build directory to avoid conflict with BUILD files.
  layout.buildDirectory.set(layout.projectDirectory.dir("buildOut"))
}

fun includeProject(name: String, path: String) {
  include(name)
  project(name).projectDir = File(path)
}

includeProject(":dagger", "dagger-runtime")

includeProject(":dagger-android", "dagger-android")

includeProject(":dagger-android-support", "dagger-android-support")

includeProject(":dagger-android-processor", "dagger-android-processor")

includeProject(":dagger-compiler", "dagger-compiler")

includeProject(":dagger-lint", "dagger-lint")

includeProject(":dagger-lint-aar", "dagger-lint-android")

includeProject(":dagger-producers", "dagger-producers")

includeProject(":dagger-spi", "dagger-spi")

includeProject(":dagger-testing", "dagger-testing")

includeProject(":hilt-compiler", "hilt-compiler")

includeProject(":hilt-core", "hilt-core")
