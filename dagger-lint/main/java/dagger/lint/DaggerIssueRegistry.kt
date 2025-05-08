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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.google.auto.service.AutoService

/**
 * Dagger Lint Issues Registry.
 *
 * A META-INF/services entry is added for this class that Lint will discover and call into for
 * detecting issues.
 */
@AutoService(IssueRegistry::class)
@Suppress("unused")
class DaggerIssueRegistry : IssueRegistry() {
  // The minApi is set to the Api this registry was compiled with, if a user has an older Api, Lint
  // will show a warning asking users to upgrade.
  override val minApi: Int = CURRENT_API
  // The api is meant to be the current api for which this registry was compiled, but we set a
  // higher number without depending on a newer Lint to avoid Lint warning users of custom checks
  // that might not work. This value eventually has to be updated as newer Api become available.
  override val api: Int = 11
  override val issues: List<Issue> = DaggerKotlinIssueDetector.issues
  override val vendor =
    Vendor(
      vendorName = "Google",
      identifier = "com.google.dagger:dagger-lint",
      feedbackUrl = "https://github.com/google/dagger/issues",
      contact = "https://github.com/google/dagger",
    )
}
