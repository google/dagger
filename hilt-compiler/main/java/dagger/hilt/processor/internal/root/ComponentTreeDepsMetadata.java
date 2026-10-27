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

import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import dagger.internal.codegen.xprocessing.XAnnotations;

/**
 * Represents the values stored in an {@link
 * dagger.hilt.internal.componenttreedeps.ComponentTreeDeps}.
 *
 * <p>This class is used in both writing ({@link ComponentTreeDepsGenerator}) and reading ({@link
 * ComponentTreeDepsProcessingStep}) of the {@code @ComponentTreeDeps} annotation.
 */
@AutoValue
abstract class ComponentTreeDepsMetadata {
  /**
   * Returns the name of the element annotated with {@link
   * dagger.hilt.internal.componenttreedeps.ComponentTreeDeps}.
   */
  abstract ClassName name();

  /** Returns the {@link dagger.hilt.internal.aggregatedroot.AggregatedRoot} deps. */
  abstract ImmutableSet<ClassName> aggregatedRootDeps();

  /** Returns the {@link dagger.hilt.internal.definecomponent.DefineComponentClasses} deps. */
  abstract ImmutableSet<ClassName> defineComponentDeps();

  /** Returns the {@link dagger.hilt.internal.aliasof.AliasOfPropagatedData} deps. */
  abstract ImmutableSet<ClassName> aliasOfDeps();

  /** Returns the {@link dagger.hilt.internal.aggregateddeps.AggregatedDeps} deps. */
  abstract ImmutableSet<ClassName> aggregatedDeps();

  /** Returns the {@link dagger.hilt.android.uninstallmodules.AggregatedUninstallModules} deps. */
  abstract ImmutableSet<ClassName> aggregatedUninstallModulesDeps();

  /** Returns the {@link dagger.hilt.android.earlyentrypoint.AggregatedEarlyEntryPoint} deps. */
  abstract ImmutableSet<ClassName> aggregatedEarlyEntryPointDeps();


  static ComponentTreeDepsMetadata from(ComponentTreeDepsIr ir) {
    return create(
        ir.getName(),
        ImmutableSet.copyOf(ir.getRootDeps()),
        ImmutableSet.copyOf(ir.getDefineComponentDeps()),
        ImmutableSet.copyOf(ir.getAliasOfDeps()),
        ImmutableSet.copyOf(ir.getAggregatedDeps()),
        ImmutableSet.copyOf(ir.getUninstallModulesDeps()),
        ImmutableSet.copyOf(ir.getEarlyEntryPointDeps()));
  }

  static ComponentTreeDepsMetadata from(XTypeElement element) {
    checkArgument(element.hasAnnotation(ClassNames.COMPONENT_TREE_DEPS));
    XAnnotation annotation = element.getAnnotation(ClassNames.COMPONENT_TREE_DEPS);

    return create(
        element.getClassName(),
        getDeps(annotation, "rootDeps"),
        getDeps(annotation, "defineComponentDeps"),
        getDeps(annotation, "aliasOfDeps"),
        getDeps(annotation, "aggregatedDeps"),
        getDeps(annotation, "uninstallModulesDeps"),
        getDeps(annotation, "earlyEntryPointDeps"));
  }

  private static ImmutableSet<ClassName> getDeps(XAnnotation annotation, String valueName) {
    return unwrapProxies(XAnnotations.getAsTypeElementList(annotation, valueName)).stream()
        .map(XTypeElement::getClassName)
        .collect(toImmutableSet());
  }

  static ComponentTreeDepsMetadata create(
      ClassName name,
      ImmutableSet<ClassName> aggregatedRootDeps,
      ImmutableSet<ClassName> defineComponentDeps,
      ImmutableSet<ClassName> aliasOfDeps,
      ImmutableSet<ClassName> aggregatedDeps,
      ImmutableSet<ClassName> aggregatedUninstallModulesDeps,
      ImmutableSet<ClassName> aggregatedEarlyEntryPointDeps) {
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
