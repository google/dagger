/*
 * Copyright (C) 2020 The Dagger Authors.
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
package dagger.lint

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.isKotlin
import dagger.lint.DaggerKotlinIssueDetector.Companion.ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION
import dagger.lint.DaggerKotlinIssueDetector.Companion.ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT
import dagger.lint.DaggerKotlinIssueDetector.Companion.ISSUE_MODULE_COMPANION_OBJECTS
import dagger.lint.DaggerKotlinIssueDetector.Companion.ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT
import java.util.EnumSet
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType
import org.jetbrains.uast.toUElement

/**
 * This is a simple lint check to catch common Dagger+Kotlin usage issues.
 *
 * - [ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION] covers using `field:` site targets for member
 * injections, which are redundant as of Dagger 2.25.
 * - [ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT] covers using `@JvmStatic` for object
 * `@Provides`-annotated functions, which are redundant as of Dagger 2.25. @JvmStatic on companion
 * object functions are redundant as of Dagger 2.26.
 * - [ISSUE_MODULE_COMPANION_OBJECTS] covers annotating companion objects with `@Module`, as they
 * are now part of the enclosing module class's API in Dagger 2.26. This will also error if the
 * enclosing class is _not_ in a `@Module`-annotated class, as this object just should be moved to a
 * top-level object to avoid confusion.
 * - [ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT] covers annotating companion objects with
 * `@Module` when the parent class is _not_ also annotated with `@Module`. While technically legal,
 * these should be moved up to top-level objects to avoid confusion.
 */
@Suppress(
  "UnstableApiUsage" // Lots of Lint APIs are marked with @Beta.
)
class DaggerKotlinIssueDetector : Detector(), SourceCodeScanner {

  companion object {
    // We use the overloaded constructor that takes a varargs of `Scope` as the last param.
    // This is to enable on-the-fly IDE checks. We are telling lint to run on both
    // JAVA and TEST_SOURCES in the `scope` parameter but by providing the `analysisScopes`
    // params, we're indicating that this check can run on either JAVA or TEST_SOURCES and
    // doesn't require both of them together.
    // From discussion on lint-dev https://groups.google.com/d/msg/lint-dev/ULQMzW1ZlP0/1dG4Vj3-AQAJ
    // This was supposed to be fixed in AS 3.4 but still required as recently as 3.6.
    private val SCOPES = Implementation(
      DaggerKotlinIssueDetector::class.java,
      EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
      EnumSet.of(Scope.JAVA_FILE),
      EnumSet.of(Scope.TEST_SOURCES)
    )

    private val ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT: Issue = Issue.create(
      id = "JvmStaticProvidesInObjectDetector",
      briefDescription = "@JvmStatic used for @Provides function in an object class",
      explanation =
        """
        It's redundant to annotate @Provides functions in object classes with @JvmStatic.
        """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = SCOPES
    )

    private val ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION: Issue = Issue.create(
      id = "FieldSiteTargetOnQualifierAnnotation",
      briefDescription = "Redundant 'field:' used for Dagger qualifier annotation.",
      explanation =
        """
        It's redundant to use 'field:' site-targets for qualifier annotations.
        """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = SCOPES
    )

    private val ISSUE_MODULE_COMPANION_OBJECTS: Issue = Issue.create(
      id = "ModuleCompanionObjects",
      briefDescription = "Module companion objects should not be annotated with @Module.",
      explanation =
        """
        Companion objects in @Module-annotated classes are considered part of the API.
        """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = SCOPES
    )

    private val ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT: Issue = Issue.create(
      id = "ModuleCompanionObjectsNotInModuleParent",
      briefDescription = "Companion objects should not be annotated with @Module.",
      explanation =
        """
        Companion objects in @Module-annotated classes are considered part of the API. This
        companion object is not a companion to an @Module-annotated class though, and should be
        moved to a top-level object declaration instead otherwise Dagger will ignore companion
        object.
        """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = SCOPES
    )

    private const val PROVIDES_ANNOTATION = "dagger.Provides"
    private const val JVM_STATIC_ANNOTATION = "kotlin.jvm.JvmStatic"
    private const val INJECT_ANNOTATION = "javax.inject.Inject"
    private const val QUALIFIER_ANNOTATION = "javax.inject.Qualifier"
    private const val MODULE_ANNOTATION = "dagger.Module"

    val issues: List<Issue> = listOf(
      ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT,
      ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION,
      ISSUE_MODULE_COMPANION_OBJECTS,
      ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? {
    return listOf(UMethod::class.java, UField::class.java, UClass::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.psiFile)) {
      // This is only relevant for Kotlin files.
      return null
    }
    return object : UElementHandler() {
      override fun visitField(node: UField) {
        if (!context.evaluator.isLateInit(node)) {
          return
        }
        // Can't use hasAnnotation because it doesn't capture all annotations!
        val injectAnnotation =
          node.uAnnotations.find { it.qualifiedName == INJECT_ANNOTATION } ?: return
        // Look for qualifier annotations
        node.uAnnotations.forEach { annotation ->
          if (annotation === injectAnnotation) {
            // Skip the inject annotation
            return@forEach
          }
          // Check if it's a FIELD site target
          val sourcePsi = annotation.sourcePsi
          if (sourcePsi is KtAnnotationEntry &&
            sourcePsi.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FIELD
          ) {
            // Check if this annotation is a qualifier annotation
            if (annotation.resolve()?.hasAnnotation(QUALIFIER_ANNOTATION) == true) {
              context.report(
                ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION,
                context.getLocation(annotation),
                ISSUE_FIELD_SITE_TARGET_ON_QUALIFIER_ANNOTATION
                  .getBriefDescription(TextFormat.TEXT),
                LintFix.create()
                  .name("Remove 'field:'")
                  .replace()
                  .text("field:")
                  .with("")
                  .autoFix()
                  .build()
              )
            }
          }
        }
      }

      override fun visitMethod(node: UMethod) {
        if (!node.isConstructor &&
          node.hasAnnotation(PROVIDES_ANNOTATION) &&
          node.hasAnnotation(JVM_STATIC_ANNOTATION)
        ) {
          val containingClass = node.containingClass?.toUElement(UClass::class.java) ?: return
          if (containingClass.isObject()) {
            val annotation = node.findAnnotation(JVM_STATIC_ANNOTATION)!!
            context.report(
              ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT,
              context.getLocation(annotation),
              ISSUE_JVM_STATIC_PROVIDES_IN_OBJECT.getBriefDescription(TextFormat.TEXT),
              LintFix.create()
                .name("Remove @JvmStatic")
                .replace()
                .pattern("(@(kotlin\\.jvm\\.)?JvmStatic)")
                .with("")
                .autoFix()
                .build()
            )
          }
        }
      }

      override fun visitClass(node: UClass) {
        if (node.hasAnnotation(MODULE_ANNOTATION) && node.isCompanionObject(context.evaluator)) {
          val parent = node.getUastParentOfType(UClass::class.java, false)!!
          if (parent.hasAnnotation(MODULE_ANNOTATION)) {
            context.report(
              ISSUE_MODULE_COMPANION_OBJECTS,
              context.getLocation(node as UElement),
              ISSUE_MODULE_COMPANION_OBJECTS.getBriefDescription(TextFormat.TEXT),
              LintFix.create()
                .name("Remove @Module")
                .replace()
                .pattern("(@(dagger\\.)?Module)")
                .with("")
                .autoFix()
                .build()

            )
          } else {
            context.report(
              ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT,
              context.getLocation(node as UElement),
              ISSUE_MODULE_COMPANION_OBJECTS_NOT_IN_MODULE_PARENT
                .getBriefDescription(TextFormat.TEXT)
            )
          }
        }
      }
    }
  }

  /** @return whether or not the [this] is a Kotlin `companion object` type. */
  private fun UClass.isCompanionObject(evaluator: JavaEvaluator): Boolean {
    return isObject() && evaluator.hasModifier(this, KtTokens.COMPANION_KEYWORD)
  }

  /** @return whether or not the [this] is a Kotlin `object` type. */
  private fun UClass.isObject(): Boolean {
    return sourcePsi is KtObjectDeclaration
  }
}
