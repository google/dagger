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

fun includeProject(name: String, path: String) {
  include(name)
  project(name).projectDir = File(path)
}

includeProject(":dagger", "dagger-runtime")

includeProject(":dagger-compiler", "gradle-projects/dagger-compiler")

includeProject(
  ":dagger-compiler-functional-tests",
  "gradle-projects/dagger-compiler-functional-tests",
)

includeProject(":dagger-producers", "gradle-projects/dagger-producers")

includeProject(":dagger-spi", "gradle-projects/dagger-spi")

includeProject(":dagger-testing", "gradle-projects/dagger-testing")
