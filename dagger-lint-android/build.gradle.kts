import dagger.gradle.build.ProjectType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
}

dependencies {
  lintPublish(project(":dagger-lint"))
}

daggerBuild {
  type = ProjectType.ANDROID_LIBRARY
  isPublished = true
}

android {
  namespace = "dagger.lint"
}