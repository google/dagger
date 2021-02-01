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

package dagger.hilt.android.processor.internal.viewmodel

import com.google.auto.common.MoreTypes.asElement
import com.google.auto.service.AutoService
import com.google.common.graph.EndpointPair
import com.google.common.graph.ImmutableNetwork
import com.squareup.javapoet.ClassName
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.Processors.hasAnnotation
import dagger.model.Binding
import dagger.model.BindingGraph
import dagger.model.BindingGraph.Edge
import dagger.model.BindingGraph.Node
import dagger.model.BindingKind
import dagger.spi.BindingGraphPlugin
import dagger.spi.DiagnosticReporter
import javax.tools.Diagnostic.Kind

/**
 * Plugin to validate users do not inject @HiltViewModel classes.
 */
@AutoService(BindingGraphPlugin::class)
class ViewModelValidationPlugin : BindingGraphPlugin {

  override fun visitGraph(bindingGraph: BindingGraph, diagnosticReporter: DiagnosticReporter) {
    if (bindingGraph.rootComponentNode().isSubcomponent()) {
      // This check does not work with partial graphs since it needs to take into account the source
      // component.
      return
    }

    val network: ImmutableNetwork<Node, Edge> = bindingGraph.network()
    bindingGraph.dependencyEdges().forEach { edge ->
      val pair: EndpointPair<Node> = network.incidentNodes(edge)
      val target: Node = pair.target()
      val source: Node = pair.source()
      if (target is Binding &&
        isHiltViewModelBinding(target) &&
        !isInternalHiltViewModelUsage(source)
      ) {
        diagnosticReporter.reportDependency(
          Kind.ERROR,
          edge,
          "\nInjection of an @HiltViewModel class is prohibited since it does not create a " +
            "ViewModel instance correctly.\nAccess the ViewModel via the Android APIs " +
            "(e.g. ViewModelProvider) instead." +
            "\nInjected ViewModel: ${target.key().type()}\n"
        )
      }
    }
  }

  private fun isHiltViewModelBinding(target: Binding): Boolean {
    // Make sure this is from an @Inject constructor rather than an overridden binding like an
    // @Provides and that the class is annotated with @HiltViewModel.
    return target.kind() == BindingKind.INJECTION &&
      hasAnnotation(asElement(target.key().type()), AndroidClassNames.HILT_VIEW_MODEL)
  }

  private fun isInternalHiltViewModelUsage(source: Node): Boolean {
    // We expect @HiltViewModel classes to be bound into a map with an @Binds like
    // @Binds
    // @IntoMap
    // @StringKey(...)
    // @HiltViewModelMap
    // abstract ViewModel bindViewModel(FooViewModel vm)
    //
    // So we check that it is a multibinding contribution with the internal qualifier.
    // TODO(erichang): Should we check for even more things?
    return source is Binding &&
      source.key().qualifier().isPresent() &&
      ClassName.get(source.key().qualifier().get().getAnnotationType()) ==
      AndroidClassNames.HILT_VIEW_MODEL_MAP_QUALIFIER &&
      source.key().multibindingContributionIdentifier().isPresent()
  }
}
