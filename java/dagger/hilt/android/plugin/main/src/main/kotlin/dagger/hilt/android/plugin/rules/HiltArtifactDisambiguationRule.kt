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

import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails

/**
 * A disambiguation rule for Hilt artifact types. This rule is used to disambiguate between
 * "android-classes" when the consumer value is "hilt-all-classes".
 */
abstract class HiltArtifactDisambiguationRule : AttributeDisambiguationRule<String> {
  override fun execute(details: MultipleCandidatesDetails<String>) {
    if (
      details.consumerValue == "hilt-all-classes" &&
        details.candidateValues.contains("android-classes-jar")
    ) {
      details.closestMatch("android-classes-jar")
    }
  }
}
