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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import java.util.EnumSet

/**
 * A [Detector] that checks that user code does not manually call dagger provider code, as these
 * methods are intended to only be called from generated Dagger code.
 */
@Suppress("UnstableApiUsage")
class DoNotCallProvidersDetector : Detector(), SourceCodeScanner {

  companion object {
    // We use the overloaded constructor that takes a varargs of `Scope` as the last param.
    // This is to enable on-the-fly IDE checks. We are telling lint to run on both
    // JAVA and TEST_SOURCES in the `scope` parameter but by providing the `analysisScopes`
    // params, we're indicating that this check can run on either JAVA or TEST_SOURCES and
    // doesn't require both of them together.
    // From discussion on lint-dev https://groups.google.com/d/msg/lint-dev/ULQMzW1ZlP0/1dG4Vj3-AQAJ
    // This was supposed to be fixed in AS 3.4 but still required as recently as 3.6.
    private val SCOPES = Implementation(
        DoNotCallProvidersDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
        EnumSet.of(Scope.JAVA_FILE),
        EnumSet.of(Scope.TEST_SOURCES)
    )

    val ISSUE: Issue = Issue.create(
        "DoNotCallProviders",
        "Dagger provider methods should not be called directly by user code.",
        """
          Dagger provider methods should not be called directly by user code. These are intended solely for use by 
          Dagger-generated code and it is programmer error to call them from user code.
        """.trimIndent(),
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        SCOPES
    )

    private val PROVIDER_ANNOTATIONS = setOf("dagger.Binds", "dagger.Provides", "dagger.producers.Produces")
    private val GENERATED_ANNOTATIONS = setOf("javax.annotation.Generated", "javax.annotation.processing.Generated")
  }

  override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
    override fun visitCallExpression(node: UCallExpression) {
      val enclosingClass = node.getParentOfType<UClass>() ?: return
      if (GENERATED_ANNOTATIONS.any(enclosingClass::hasAnnotation)) return
      val method = node.resolve() ?: return
      if (PROVIDER_ANNOTATIONS.any(method::hasAnnotation)) {
        context.report(
            ISSUE,
            context.getLocation(node),
            ISSUE.getBriefDescription(TextFormat.TEXT)
        )
      }
    }
  }
}
