/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.hilt.android.plugin.rules

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails

/**
 * A compatibility rule for Hilt artifact types. This rule is used to allow the "hilt-all-classes"
 * artifact type to be compatible with "android-classes" and "android-classes-jar".
 */
abstract class HiltArtifactCompatibilityRule : AttributeCompatibilityRule<String> {
  override fun execute(details: CompatibilityCheckDetails<String>) {
    if (
      details.consumerValue == "hilt-all-classes" && details.producerValue == "android-classes-jar"
    ) {
      details.compatible()
    }
  }
}
