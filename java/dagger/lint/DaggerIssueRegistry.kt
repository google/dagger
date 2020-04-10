package dagger.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.google.auto.service.AutoService

@AutoService(IssueRegistry::class)
@Suppress("unused", "UnstableApiUsage")
class DaggerIssueRegistry : IssueRegistry() {
  override val api: Int = CURRENT_API
  override val issues: List<Issue> = DaggerKotlinIssuesDetector.issues
}