/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.root.ir

import com.squareup.javapoet.ClassName
import kotlin.jvm.Throws

// Validates roots being processed.
object AggregatedRootIrValidator {
  @JvmStatic
  @Throws(InvalidRootsException::class)
  fun rootsToProcess(
    isCrossCompilationRootValidationDisabled: Boolean,
    processedRootSentinels: Set<ProcessedRootSentinelIr>,
    aggregatedRoots: Set<AggregatedRootIr>,
  ): Set<AggregatedRootIr> {
    val processedRootNames = processedRootSentinels.flatMap { it.roots }.toSet()
    val processedRoots = aggregatedRoots
        .filter { processedRootNames.contains(it.root.canonicalName()) }
    val rootsToProcess =
      aggregatedRoots
        .filterNot { processedRootNames.contains(it.root.canonicalName()) }
        .sortedBy { it.root.canonicalName() }
    val testRootsToProcess = rootsToProcess.filter { it.isTestRoot }
    val appRootsToProcess = rootsToProcess - testRootsToProcess
    fun Collection<AggregatedRootIr>.rootsToString() = map { it.root }.joinToString()
    if (appRootsToProcess.size > 1) {
      throw InvalidRootsException(
        "Cannot process multiple app roots in the same compilation unit: " +
          appRootsToProcess.rootsToString()
      )
    }
    if (testRootsToProcess.isNotEmpty() && appRootsToProcess.isNotEmpty()) {
      throw InvalidRootsException(
        """
        Cannot process test roots and app roots in the same compilation unit:
          App root in this compilation unit: ${appRootsToProcess.rootsToString()}
          Test roots in this compilation unit: ${testRootsToProcess.rootsToString()}
        """
          .trimIndent()
      )
    }
    // Perform validation across roots previous compilation units.
    if (!isCrossCompilationRootValidationDisabled) {
      val alreadyProcessedTestRoots = processedRoots.filter { it.isTestRoot }
      // TODO(b/185742783): Add an explanation or link to docs to explain why we're forbidding this.
      if (alreadyProcessedTestRoots.isNotEmpty() && rootsToProcess.isNotEmpty()) {
        throw InvalidRootsException(
          """
          Cannot process new roots when there are test roots from a previous compilation unit:
            Test roots from previous compilation unit: ${alreadyProcessedTestRoots.rootsToString()}
            All roots from this compilation unit: ${rootsToProcess.rootsToString()}
          """
            .trimIndent()
        )
      }

      val alreadyProcessedAppRoots =
        processedRoots.filter {
            !it.isTestRoot
        }
      if (alreadyProcessedAppRoots.isNotEmpty() && appRootsToProcess.isNotEmpty()) {
        throw InvalidRootsException(
          """
          Cannot process new app roots when there are app roots from a previous compilation unit:
            App roots in previous compilation unit: ${alreadyProcessedAppRoots.rootsToString()}
            App roots in this compilation unit: ${appRootsToProcess.rootsToString()}
          """
            .trimIndent()
        )
      }
    }
    return rootsToProcess.toSet()
  }
}

// An exception thrown when root validation fails.
class InvalidRootsException(msg: String) : Exception(msg)
