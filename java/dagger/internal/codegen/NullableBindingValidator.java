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

package dagger.internal.codegen;

import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import javax.inject.Inject;

/**
 * Reports errors or warnings (depending on the {@code -Adagger.nullableValidation} value) for each
 * non-nullable dependency request that is satisfied by a nullable binding.
 */
final class NullableBindingValidator implements BindingGraphPlugin {

  private final CompilerOptions compilerOptions;

  @Inject
  NullableBindingValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    for (dagger.model.Binding binding : nullableBindings(bindingGraph)) {
      for (DependencyEdge dependencyEdge : nonNullableDependencies(bindingGraph, binding)) {
        diagnosticReporter.reportDependency(
            compilerOptions.nullableValidationKind(),
            dependencyEdge,
            nullableToNonNullable(
                binding.key().toString(),
                binding.toString())); // binding.toString() will include the @Nullable
      }
    }
  }

  @Override
  public String pluginName() {
    return "Dagger/Nullable";
  }

  private ImmutableList<dagger.model.Binding> nullableBindings(BindingGraph bindingGraph) {
    return bindingGraph.bindings().stream()
        .filter(binding -> binding.isNullable())
        .collect(toImmutableList());
  }

  private ImmutableSet<DependencyEdge> nonNullableDependencies(
      BindingGraph bindingGraph, dagger.model.Binding binding) {
    return bindingGraph.network().inEdges(binding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> !edge.dependencyRequest().isNullable())
        .collect(toImmutableSet());
  }

  @VisibleForTesting
  static String nullableToNonNullable(String key, String binding) {
    return String.format("%s is not nullable, but is being provided by %s", key, binding);
  }
}
