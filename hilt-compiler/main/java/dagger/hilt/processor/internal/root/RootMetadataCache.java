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

package dagger.hilt.processor.internal.root;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room3.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.aliasof.AliasOfPropagatedDataMetadata;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-compilation cache shared between {@link RootProcessingStep} and {@link
 * ComponentTreeDepsProcessingStep} within {@link RootProcessor} / {@link KspRootProcessor}.
 */
final class RootMetadataCache {
  private final XProcessingEnv env;
  private final Map<ClassName, ComponentTreeDepsMetadata> componentTreeDepsByClassName =
      new HashMap<>();
  private final Map<ClassName, AggregatedDepsMetadata> aggregatedDepsByClassName = new HashMap<>();
  private final Map<ClassName, AliasOfPropagatedDataMetadata> aliasOfDepsByClassName =
      new HashMap<>();
  private final Map<ClassName, AggregatedUninstallModulesMetadata> uninstallModulesDepsByClassName =
      new HashMap<>();
  private final Map<ClassName, AggregatedEarlyEntryPointMetadata> earlyEntryPointDepsByClassName =
      new HashMap<>();

  RootMetadataCache(XProcessingEnv env) {
    this.env = env;
  }

  ImmutableSet<AggregatedDepsMetadata> getAggregatedDeps() {
    ImmutableSet<AggregatedDepsMetadata> metadatas = AggregatedDepsMetadata.from(env);
    for (AggregatedDepsMetadata metadata : metadatas) {
      aggregatedDepsByClassName.put(metadata.name(), metadata);
    }
    return metadatas;
  }

  ImmutableSet<AggregatedDepsMetadata> getAggregatedDeps(ImmutableSet<ClassName> names) {
    return names.stream()
        .map(
            name ->
                aggregatedDepsByClassName.computeIfAbsent(
                    name, k -> AggregatedDepsMetadata.from(env.requireTypeElement(k))))
        .collect(toImmutableSet());
  }

  ImmutableSet<AliasOfPropagatedDataMetadata> getAliasOfDeps() {
    ImmutableSet<AliasOfPropagatedDataMetadata> metadatas = AliasOfPropagatedDataMetadata.from(env);
    for (AliasOfPropagatedDataMetadata metadata : metadatas) {
      aliasOfDepsByClassName.put(metadata.name(), metadata);
    }
    return metadatas;
  }

  ImmutableSet<AliasOfPropagatedDataMetadata> getAliasOfDeps(ImmutableSet<ClassName> names) {
    return names.stream()
        .map(
            name ->
                aliasOfDepsByClassName.computeIfAbsent(
                    name, k -> AliasOfPropagatedDataMetadata.from(env.requireTypeElement(k))))
        .collect(toImmutableSet());
  }

  ImmutableSet<AggregatedUninstallModulesMetadata> getAggregatedUninstallModulesDeps() {
    ImmutableSet<AggregatedUninstallModulesMetadata> metadatas =
        AggregatedUninstallModulesMetadata.from(env);
    for (AggregatedUninstallModulesMetadata metadata : metadatas) {
      uninstallModulesDepsByClassName.put(metadata.name(), metadata);
    }
    return metadatas;
  }

  ImmutableSet<AggregatedUninstallModulesMetadata> getAggregatedUninstallModulesDeps(
      ImmutableSet<ClassName> names) {
    return names.stream()
        .map(
            name ->
                uninstallModulesDepsByClassName.computeIfAbsent(
                    name, k -> AggregatedUninstallModulesMetadata.from(env.requireTypeElement(k))))
        .collect(toImmutableSet());
  }

  ImmutableSet<AggregatedEarlyEntryPointMetadata> getAggregatedEarlyEntryPointDeps() {
    ImmutableSet<AggregatedEarlyEntryPointMetadata> metadatas =
        AggregatedEarlyEntryPointMetadata.from(env);
    for (AggregatedEarlyEntryPointMetadata metadata : metadatas) {
      earlyEntryPointDepsByClassName.put(metadata.name(), metadata);
    }
    return metadatas;
  }

  ImmutableSet<AggregatedEarlyEntryPointMetadata> getAggregatedEarlyEntryPointDeps(
      ImmutableSet<ClassName> names) {
    return names.stream()
        .map(
            name ->
                earlyEntryPointDepsByClassName.computeIfAbsent(
                    name, k -> AggregatedEarlyEntryPointMetadata.from(env.requireTypeElement(k))))
        .collect(toImmutableSet());
  }

  ComponentTreeDepsMetadata getComponentTreeDeps(ComponentTreeDepsIr ir) {
    ComponentTreeDepsMetadata metadata = ComponentTreeDepsMetadata.from(ir);
    componentTreeDepsByClassName.put(metadata.name(), metadata);
    return metadata;
  }

  ComponentTreeDepsMetadata getComponentTreeDeps(ClassName name) {
    return componentTreeDepsByClassName.computeIfAbsent(
        name, k -> ComponentTreeDepsMetadata.from(env.requireTypeElement(k)));
  }
}
