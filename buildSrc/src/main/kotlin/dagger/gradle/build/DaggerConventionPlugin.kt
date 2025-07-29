/*
 * Copyright (C) 2025 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.gradle.build

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper

/**
 * A plugin to configure the Gradle projects. All projects should apply this plugin which will
 * configure the project based on other projects plugins applied, such as the Kotlin JVM plugin
 * (`libs.plugins.kotlinJvm`) or the shadow plugin (`libs.plugins.shadow`).
 *
 * This plugin can be applied using:
 * ```
 * plugins {
 *   alias(libs.plugins.daggerBuild)
 * }
 * ```
 *
 * Source sets for the project are configured by this plugin and should have the following
 * convention:
 * ```
 * <project>
 *   main
 *     java - Library sources
 *     resources - Library resources
 *   test
 *     javatests - Unit test sources
 *     resources - Unit test resources
 * ```
 */
class DaggerConventionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val daggerExtension = project.extensions.create<DaggerBuildExtension>("daggerBuild")

    // Perform different configuration action based on the plugins applied to the project
    project.plugins.configureEach {
      when (this) {
        is LibraryPlugin -> configureWithAndroidLibraryPlugin(project)
        is KotlinBasePluginWrapper -> configureWithKotlinPlugin(project)
        is ShadowPlugin -> configureWithShadowPlugin(project, daggerExtension)
      }
    }

    // Configure archive task for reproducible jar as recommended in
    // https://docs.gradle.org/4.9/userguide/working_with_files.html#sec:reproducible_archives
    project.tasks.withType<AbstractArchiveTask>().configureEach {
      isPreserveFileTimestamps = false
      isReproducibleFileOrder = true
    }

    project.afterEvaluate { configurePublish(project, daggerExtension) }
  }

  private fun configureWithAndroidLibraryPlugin(project: Project) {
    val libraryExtension = project.extensions.getByType<LibraryExtension>()
    libraryExtension.apply {
      configureAndroidSourceSets(sourceSets)
      compileOptions {
        sourceCompatibility = JavaVersion.toVersion(project.getVersionByName("jvmTarget"))
        targetCompatibility = JavaVersion.toVersion(project.getVersionByName("jvmTarget"))
      }
      compileSdk = project.getVersionByName("androidCompileSdk").toInt()
      defaultConfig.minSdk = project.getVersionByName("androidMinSdk").toInt()
    }
  }

  private fun configureAndroidSourceSets(
    sourceSets: NamedDomainObjectContainer<out AndroidSourceSet>
  ) {
    fun setSourceSets(name: String, sourceDir: String, resourceDir: String, resDir: String) {
      sourceSets.named(name).configure {
        java.srcDirs(sourceDir)
        kotlin.srcDirs(sourceDir)
        resources.srcDirs(resourceDir)
        res.srcDirs(resourceDir)
        manifest.srcFile("$name/AndroidManifest.xml")
      }
    }
    setSourceSets(
      name = "main",
      sourceDir = "main/java",
      resourceDir = "main/resources",
      resDir = "main/res",
    )
    setSourceSets(
      name = "test",
      sourceDir = "test/javatests",
      resourceDir = "test/resources",
      resDir = "test/res",
    )
    setSourceSets(
      name = "androidTest",
      sourceDir = "androidTest/javatests",
      resourceDir = "androidTest/resources",
      resDir = "androidTest/res",
    )
  }

  private fun configureWithKotlinPlugin(project: Project) {
    configureKotlinSourceSets(project)
    configureKotlinJvmTarget(project)
  }

  private fun configureKotlinSourceSets(project: Project) {
    val kotlinExtension = project.extensions.findByType<KotlinProjectExtension>()
    val javaExtension = project.extensions.findByType<JavaPluginExtension>()
    fun setSourceSets(name: String, sourceDir: String, resourceDir: String) {
      kotlinExtension?.sourceSets?.findByName(name)?.apply {
        kotlin.srcDirs(sourceDir)
        resources.srcDirs(resourceDir)
      }
      javaExtension?.sourceSets?.findByName(name)?.apply { java.srcDirs(sourceDir) }
    }
    setSourceSets(name = "main", sourceDir = "main/java", resourceDir = "main/resources")
    setSourceSets(name = "test", sourceDir = "test/javatests", resourceDir = "test/resources")
  }

  private fun configureKotlinJvmTarget(project: Project) {
    val kotlinExtension = project.extensions.getByName("kotlin")
    if (kotlinExtension is KotlinJvmProjectExtension) {
      kotlinExtension.jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(project.getVersionByName("jdk")))
      }
    }
    val kotlinCompilerOptions =
      when (kotlinExtension) {
        is KotlinJvmProjectExtension -> kotlinExtension.compilerOptions
        is KotlinAndroidProjectExtension -> kotlinExtension.compilerOptions
        else -> error("Unknown Kotlin project extension: $kotlinExtension")
      }
    kotlinCompilerOptions.apply {
      languageVersion.set(KotlinVersion.fromVersion(project.getVersionByName("kotlinTarget")))
      apiVersion.set(KotlinVersion.fromVersion(project.getVersionByName("kotlinTarget")))
      jvmTarget.set(JvmTarget.fromTarget(project.getVersionByName("jvmTarget")))
    }
    val javaProject = project.extensions.getByType<JavaPluginExtension>()
    javaProject.sourceCompatibility = JavaVersion.toVersion(project.getVersionByName("jvmTarget"))
    javaProject.targetCompatibility = JavaVersion.toVersion(project.getVersionByName("jvmTarget"))
  }

  private fun configureWithShadowPlugin(project: Project, daggerExtension: DaggerBuildExtension) {
    // Configuration for shaded dependencies
    val shadedConfiguration =
      project.configurations.create("shaded") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false // Do not include transitive dependencies of shaded deps
      }

    // Shaded dependencies are compile only dependencies
    project.configurations.named("compileOnly").configure { extendsFrom(shadedConfiguration) }

    val shadowJarTask =
      project.tasks.withType<ShadowJar>().named("shadowJar") {
        // Use no classifier, the shaded jar is the one to be published.
        archiveClassifier.set("")
        // Set the 'shaded' configuration as the dependencies configuration to shade
        configurations = listOf(shadedConfiguration)
        // Enable service files merging
        mergeServiceFiles()
        // Enable package relocation (necessary for project that only relocate but have no
        // shaded deps)
        isEnableRelocation = true

        daggerExtension.relocateRules.forEach { (from, to) -> relocate(from, to) }
      }

    val jarTask =
      project.tasks.withType<Jar>().named("jar") {
        // Change the default jar task classifier to avoid conflicting with the shaded one.
        archiveClassifier.set("before-shade")
      }

    // Configuration for consuming unshaded artifact in Dagger's multi-project setup.
    project.configurations.create("unshaded") {
      isCanBeConsumed = true
      isCanBeResolved = false
      extendsFrom(project.configurations.named("implementation").get())
      outgoing.artifact(jarTask)
    }

    configureOutgoingArtifacts(project, shadowJarTask)
  }

  /**
   * Configure Gradle consumers (that use Gradle publishing metadata) of the project to use the
   * shaded jar.
   *
   * This is necessary so that the publishing Gradle module metadata references the shaded jar. See
   * https://github.com/GradleUp/shadow/issues/847
   */
  private fun configureOutgoingArtifacts(project: Project, task: TaskProvider<ShadowJar>) {
    project.configurations.configureEach {
      if (name == "apiElements" || name == "runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(task)
      }
    }
  }

  private fun configurePublish(project: Project, daggerExtension: DaggerBuildExtension) {
    if (!daggerExtension.isPublished) {
      // Project is to not be published
      return
    }
    project.pluginManager.apply(project.getPluginIdByName("publish"))
    project.plugins.withId(project.getPluginIdByName("publish")) {
      val publishExtension =
        project.extensions.getByName("mavenPublishing") as MavenPublishBaseExtension
      publishExtension.apply {
        when (daggerExtension.type) {
          SoftwareType.ANDROID_LIBRARY ->
            configure(
              AndroidSingleVariantLibrary(
                variant = "release",
                publishJavadocJar = true,
                sourcesJar = true,
              )
            )
          SoftwareType.JVM_LIBRARY ->
            configure(KotlinJvm(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
          SoftwareType.PROCESSOR ->
            configure(KotlinJvm(javadocJar = JavadocJar.None(), sourcesJar = true))
          else -> error("Cannot publish library of type ${daggerExtension.type}.")
        }
        coordinates(
          groupId = "com.google.dagger",
          artifactId = project.name,
          version = project.findProperty("PUBLISH_VERSION").toString(),
        )
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        pom {
          name.set(project.name.asPomName())
          description.set("A fast dependency injector for Android and Java.")
          url.set("https://github.com/google/dagger")
          scm {
            url.set("https://github.com/google/dagger/")
            connection.set("scm:git:git://github.com/google/dagger.git")
          }
          issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/google/dagger/issues")
          }
          licenses {
            license {
              name.set("The Apache Software License, Version 2.0")
              url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
          }
          organization {
            name.set("Google, Inc.")
            url.set("https://www.google.com")
          }
        }
      }
    }
  }

  /**
   * Converts the Gradle project name to a more appropriate name for the POM file.
   *
   * For example: 'dagger-compiler' to 'Dagger Compiler'
   */
  private fun String.asPomName(): String {
    val parts = split("-").map { first().uppercaseChar() + drop(1) }
    return parts.joinToString(separator = " ")
  }
}
