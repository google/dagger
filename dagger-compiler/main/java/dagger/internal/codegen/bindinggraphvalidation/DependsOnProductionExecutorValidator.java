/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XProcessingEnv;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.MaybeBinding;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/**
 * Reports an error on all bindings that depend explicitly on the {@code @Production Executor} key.
 */
// TODO(dpb,beder): Validate this during @Inject/@Provides/@Produces validation.
final class DependsOnProductionExecutorValidator extends ValidationBindingGraphPlugin {
  private final XProcessingEnv processingEnv;
  private final KeyFactory keyFactory;

  @Inject
  DependsOnProductionExecutorValidator(XProcessingEnv processingEnv, KeyFactory keyFactory) {
    this.processingEnv = processingEnv;
    this.keyFactory = keyFactory;
  }

  @Override
  public String pluginName() {
    return "Dagger/DependsOnProductionExecutor";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (!usesProducers()) {
      return;
    }

    Key productionImplementationExecutorKey = keyFactory.forProductionImplementationExecutor();
    Key productionExecutorKey = keyFactory.forProductionExecutor();

    bindingGraph.network().nodes().stream()
        .flatMap(instancesOf(MaybeBinding.class))
        .filter(node -> node.key().equals(productionExecutorKey))
        .flatMap(productionExecutor -> bindingGraph.requestingBindings(productionExecutor).stream())
        .filter(binding -> !binding.key().equals(productionImplementationExecutorKey))
        .forEach(binding -> reportError(diagnosticReporter, binding));
  }

  private void reportError(DiagnosticReporter diagnosticReporter, Binding binding) {
    diagnosticReporter.reportBinding(
        ERROR, binding, "%s may not depend on the production executor", binding.key());
  }

  private boolean usesProducers() {
    return processingEnv.findTypeElement(XTypeNames.PRODUCES) != null;
  }
}
