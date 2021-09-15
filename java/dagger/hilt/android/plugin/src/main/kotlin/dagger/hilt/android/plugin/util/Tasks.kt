package dagger.hilt.android.plugin.util

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Gets [KotlinCompile] task of an Android variant.
 */
@Suppress("UNCHECKED_CAST")
internal fun getCompileKotlin(
  @Suppress("DEPRECATION") variant: com.android.build.gradle.api.BaseVariant,
  project: Project
) = project.tasks.named(
  "compile${variant.name.capitalize()}Kotlin"
) as TaskProvider<KotlinCompile>
