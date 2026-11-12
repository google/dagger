/*
 * Copyright (C) 2024 The Dagger Authors.
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

@file:OptIn(ExperimentalProcessingApi::class)

package dagger.hilt.android.processor.internal.worker

import androidx.room3.compiler.processing.ExperimentalProcessingApi
import androidx.room3.compiler.processing.XProcessingEnv
import androidx.room3.compiler.processing.XProcessingEnv.Companion.create
import com.google.auto.service.AutoService
import com.google.common.graph.EndpointPair
import com.google.common.graph.ImmutableNetwork
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.hasAnnotation
import dagger.spi.model.Binding
import dagger.spi.model.BindingGraph
import dagger.spi.model.BindingGraph.Edge
import dagger.spi.model.BindingGraph.Node
import dagger.spi.model.BindingGraphPlugin
import dagger.spi.model.BindingKind
import dagger.spi.model.DaggerProcessingEnv
import dagger.spi.model.DiagnosticReporter
import javax.tools.Diagnostic.Kind

/**
 * Plugin to validate users do not inject @HiltWorker classes directly.
 */
@AutoService(BindingGraphPlugin::class)
class HiltWorkerValidationPlugin : BindingGraphPlugin {

  private lateinit var env: XProcessingEnv
  private lateinit var daggerProcessingEnv: DaggerProcessingEnv

  override fun init(processingEnv: DaggerProcessingEnv, options: MutableMap<String, String>) {
    daggerProcessingEnv = processingEnv
  }

  override fun onProcessingRoundBegin() {
    env = daggerProcessingEnv.toXProcessingEnv()
  }

  override fun visitGraph(bindingGraph: BindingGraph, diagnosticReporter: DiagnosticReporter) {
    if (bindingGraph.rootComponentNode().isSubcomponent()) {
      return
    }

    val network: ImmutableNetwork<Node, Edge> = bindingGraph.network()
    bindingGraph.dependencyEdges().forEach { edge ->
      val pair: EndpointPair<Node> = network.incidentNodes(edge)
      val target: Node = pair.target()
      val source: Node = pair.source()
      if (target !is Binding) {
        return@forEach
      }
      if (isHiltWorkerBinding(target) && !isInternalHiltWorkerUsage(source)) {
        diagnosticReporter.reportDependency(
          Kind.ERROR,
          edge,
          "\nInjection of an @HiltWorker class is prohibited since it does not create a " +
            "Worker instance correctly.\nAccess the Worker via the WorkManager APIs " +
            "(e.g. WorkManager.enqueue()) instead." +
            "\nInjected Worker: ${target.key().type()}\n",
        )
      }
    }
  }

  private fun isHiltWorkerBinding(target: Binding): Boolean {
    return target.kind() == BindingKind.INJECTION &&
      target.key().type().hasAnnotation(AndroidClassNames.HILT_WORKER)
  }

  private fun isInternalHiltWorkerUsage(source: Node): Boolean {
    return source is Binding &&
      source.key().qualifier().isPresent() &&
      source.key().qualifier().get().getQualifiedName() ==
        AndroidClassNames.HILT_WORKER_MAP_QUALIFIER.canonicalName() &&
      source.key().multibindingContributionIdentifier().isPresent()
  }
}

private fun DaggerProcessingEnv.toXProcessingEnv(): XProcessingEnv {
  return when (backend()) {
    DaggerProcessingEnv.Backend.JAVAC -> create(javac())
    DaggerProcessingEnv.Backend.KSP -> create(ksp(), resolver())
    else -> error("Backend ${backend()} not supported yet.")
  }
}
