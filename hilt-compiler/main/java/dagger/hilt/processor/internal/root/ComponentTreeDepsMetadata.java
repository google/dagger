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

package dagger.hilt.processor.internal.root;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.hilt.processor.internal.AggregatedElements.unwrapProxies;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import dagger.internal.codegen.xprocessing.XAnnotations;

/**
 * Represents the values stored in an {@link
 * dagger.hilt.internal.componenttreedeps.ComponentTreeDeps}.
 *
 * <p>This class is used in both writing ({@link ComponentTreeDepsGenerator}) and reading ({@link
 * ComponentTreeDepsProcessor}) of the {@code @ComponentTreeDeps} annotation.
 */
@AutoValue
abstract class ComponentTreeDepsMetadata {
  /**
   * Returns the name of the element annotated with {@link
   * dagger.hilt.internal.componenttreedeps.ComponentTreeDeps}.
   */
  abstract ClassName name();

  /** Returns the {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot} deps. */
  abstract ImmutableSet<XTypeElement> aggregatedRootDeps();

  /** Returns the {@link dagger.hilt.internal.definecomponent.DefineComponentClasses} deps. */
  abstract ImmutableSet<XTypeElement> defineComponentDeps();

  /** Returns the {@link dagger.hilt.internal.aliasof.AliasOfPropagatedData} deps. */
  abstract ImmutableSet<XTypeElement> aliasOfDeps();

  /** Returns the {@link dagger.hilt.internal.aggregateddeps.AggregatedDeps} deps. */
  abstract ImmutableSet<XTypeElement> aggregatedDeps();

  /** Returns the {@link dagger.hilt.android.uninstallmodules.AggregatedUninstallModules} deps. */
  abstract ImmutableSet<XTypeElement> aggregatedUninstallModulesDeps();

  /** Returns the {@link dagger.hilt.android.earlyentrypoint.AggregatedEarlyEntryPoint} deps. */
  abstract ImmutableSet<XTypeElement> aggregatedEarlyEntryPointDeps();

  static ComponentTreeDepsMetadata from(XTypeElement element, XProcessingEnv env) {
    checkArgument(element.hasAnnotation(ClassNames.COMPONENT_TREE_DEPS));
    XAnnotation annotation = element.getAnnotation(ClassNames.COMPONENT_TREE_DEPS);

    return create(
        element.getClassName(),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "rootDeps")),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "defineComponentDeps")),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "aliasOfDeps")),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "aggregatedDeps")),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "uninstallModulesDeps")),
        unwrapProxies(XAnnotations.getAsTypeElementList(annotation, "earlyEntryPointDeps")));
  }

  static ComponentTreeDepsMetadata from(ComponentTreeDepsIr ir, XProcessingEnv env) {
    return create(
        ir.getName(),
        ir.getRootDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getDefineComponentDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getAliasOfDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getAggregatedDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getUninstallModulesDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getEarlyEntryPointDeps().stream()
            .map(it -> env.requireTypeElement(it.canonicalName()))
            .collect(toImmutableSet()));
  }

  /** Returns all modules included in a component tree deps. */
  public ImmutableSet<XTypeElement> modules() {
    return AggregatedDepsMetadata.from(aggregatedDeps()).stream()
        .filter(AggregatedDepsMetadata::isModule)
        .map(AggregatedDepsMetadata::dependency)
        .collect(toImmutableSet());
  }

  /** Returns all entry points included in a component tree deps. */
  public ImmutableSet<XTypeElement> entrypoints() {
    return AggregatedDepsMetadata.from(aggregatedDeps()).stream()
        .filter(dependency -> !dependency.isModule())
        .map(AggregatedDepsMetadata::dependency)
        .collect(toImmutableSet());
  }

  static ComponentTreeDepsMetadata create(
      ClassName name,
      ImmutableSet<XTypeElement> aggregatedRootDeps,
      ImmutableSet<XTypeElement> defineComponentDeps,
      ImmutableSet<XTypeElement> aliasOfDeps,
      ImmutableSet<XTypeElement> aggregatedDeps,
      ImmutableSet<XTypeElement> aggregatedUninstallModulesDeps,
      ImmutableSet<XTypeElement> aggregatedEarlyEntryPointDeps) {
    return new AutoValue_ComponentTreeDepsMetadata(
        name,
        aggregatedRootDeps,
        defineComponentDeps,
        aliasOfDeps,
        aggregatedDeps,
        aggregatedUninstallModulesDeps,
        aggregatedEarlyEntryPointDeps);
  }
}
