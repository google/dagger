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

@file:OptIn(ExperimentalProcessingApi::class)

package dagger.hilt.android.processor.internal.viewmodel

import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XProcessingEnv.Companion.create
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XTypeElement
import androidx.room.compiler.processing.compat.XConverters.toXProcessing
import com.google.auto.service.AutoService
import com.google.common.graph.EndpointPair
import com.google.common.graph.ImmutableNetwork
import dagger.hilt.android.processor.internal.AndroidClassNames
import dagger.hilt.processor.internal.getQualifiedName
import dagger.hilt.processor.internal.hasAnnotation
import dagger.internal.codegen.xprocessing.XTypeElements
import dagger.spi.model.Binding
import dagger.spi.model.BindingGraph
import dagger.spi.model.BindingGraph.Edge
import dagger.spi.model.BindingGraph.Node
import dagger.spi.model.BindingGraphPlugin
import dagger.spi.model.BindingKind
import dagger.spi.model.DaggerProcessingEnv
import dagger.spi.model.DaggerType
import dagger.spi.model.DiagnosticReporter
import javax.tools.Diagnostic.Kind

/** Plugin to validate users do not inject @HiltViewModel classes. */
@AutoService(BindingGraphPlugin::class)
class ViewModelValidationPlugin : BindingGraphPlugin {

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
      // This check does not work with partial graphs since it needs to take into account the source
      // component.
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
      if (isHiltViewModelBinding(target) && !isInternalHiltViewModelUsage(source)) {
        diagnosticReporter.reportDependency(
          Kind.ERROR,
          edge,
          "\nInjection of an @HiltViewModel class is prohibited since it does not create a " +
            "ViewModel instance correctly.\nAccess the ViewModel via the Android APIs " +
            "(e.g. ViewModelProvider) instead." +
            "\nInjected ViewModel: ${target.key().type()}\n",
        )
      } else if (
        isViewModelAssistedFactory(target) && !isInternalViewModelAssistedFactoryUsage(source)
      ) {
        diagnosticReporter.reportDependency(
          Kind.ERROR,
          edge,
          "\nInjection of an assisted factory for Hilt ViewModel is prohibited since it " +
            "can not be used to create a ViewModel instance correctly.\nAccess the ViewModel via " +
            "the Android APIs (e.g. ViewModelProvider) instead." +
            "\nInjected factory: ${target.key().type()}\n",
        )
      }
    }
  }

  private fun isHiltViewModelBinding(target: Binding): Boolean {
    // Make sure this is from an @Inject constructor rather than an overridden binding like an
    // @Provides and that the class is annotated with @HiltViewModel.
    return target.kind() == BindingKind.INJECTION &&
      target.key().type().hasAnnotation(AndroidClassNames.HILT_VIEW_MODEL)
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
      source.key().qualifier().get().getQualifiedName() ==
        AndroidClassNames.HILT_VIEW_MODEL_MAP_QUALIFIER.canonicalName() &&
      source.key().multibindingContributionIdentifier().isPresent()
  }

  private fun isViewModelAssistedFactory(target: Binding): Boolean {
    if (target.kind() != BindingKind.ASSISTED_FACTORY) return false
    val factoryType = target.key().type()
    return getAssistedInjectTypeElement(factoryType.toXType(env))
      .hasAnnotation(AndroidClassNames.HILT_VIEW_MODEL)
  }

  private fun getAssistedInjectTypeElement(factoryType: XType): XTypeElement =
    // The factory method and the type element for its return type cannot be
    // null as the BindingGraph won't be created if the
    // @AssistedFactory-annotated class is invalid.
    getAssistedFactoryMethods(factoryType.typeElement)
      .single()
      .asMemberOf(factoryType)
      .returnType
      .typeElement!!

  private fun getAssistedFactoryMethods(factory: XTypeElement?): List<XMethodElement> {
    return XTypeElements.getAllNonPrivateInstanceMethods(factory)
      .filter { it.isAbstract() }
      .filter { !it.isJavaDefault() }
  }

  private fun isInternalViewModelAssistedFactoryUsage(source: Node): Boolean {
    // We expect the only usage of the assisted factory for a Hilt ViewModel is in the
    // code we generate:
    // @Binds
    // @IntoMap
    // @StringKey(...)
    // @HiltViewModelAssistedMap
    // public abstract Object bind(FooFactory factory);
    return source is Binding &&
      source.key().qualifier().isPresent() &&
      source.key().qualifier().get().getQualifiedName() ==
        AndroidClassNames.HILT_VIEW_MODEL_ASSISTED_FACTORY_MAP_QUALIFIER.canonicalName() &&
      source.key().multibindingContributionIdentifier().isPresent()
  }
}

private fun DaggerType.toXType(processingEnv: XProcessingEnv): XType {
  return when (backend()) {
    DaggerProcessingEnv.Backend.JAVAC -> javac().toXProcessing(processingEnv)
    DaggerProcessingEnv.Backend.KSP -> ksp().toXProcessing(processingEnv)
    else -> error("Backend ${ backend() } not supported yet.")
  }
}

private fun DaggerProcessingEnv.toXProcessingEnv(): XProcessingEnv {
  return when (backend()) {
    DaggerProcessingEnv.Backend.JAVAC -> create(javac())
    DaggerProcessingEnv.Backend.KSP -> create(ksp(), resolver())
    else -> error("Backend ${ backend() } not supported yet.")
  }
}
