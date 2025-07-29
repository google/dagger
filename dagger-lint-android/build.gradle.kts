import dagger.gradle.build.SoftwareType

plugins {
  alias(libs.plugins.daggerBuild)
  id(libs.plugins.android.library.get().pluginId)
}

dependencies { lintPublish(project(":dagger-lint")) }

daggerBuild {
  type = SoftwareType.ANDROID_LIBRARY
  isPublished = true
}

android { namespace = "dagger.lint" }
