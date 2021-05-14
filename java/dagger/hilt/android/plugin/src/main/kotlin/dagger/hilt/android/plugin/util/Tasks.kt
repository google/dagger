package dagger.hilt.android.plugin.util

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Gets [KotlinCompile] task of an Android variant.
 */
@Suppress("UNCHECKED_CAST")
internal fun getCompileKotlin(variant: BaseVariant, project: Project) =
  project.tasks.named(
    "compile${variant.name.capitalize()}Kotlin"
  ) as TaskProvider<KotlinCompile>
