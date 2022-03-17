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
import static dagger.hilt.processor.internal.AnnotationValues.getTypeElements;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

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
  abstract ImmutableSet<TypeElement> aggregatedRootDeps();

  /** Returns the {@link dagger.hilt.internal.definecomponent.DefineComponentClasses} deps. */
  abstract ImmutableSet<TypeElement> defineComponentDeps();

  /** Returns the {@link dagger.hilt.internal.aliasof.AliasOfPropagatedData} deps. */
  abstract ImmutableSet<TypeElement> aliasOfDeps();

  /** Returns the {@link dagger.hilt.internal.aggregateddeps.AggregatedDeps} deps. */
  abstract ImmutableSet<TypeElement> aggregatedDeps();

  /** Returns the {@link dagger.hilt.android.uninstallmodules.AggregatedUninstallModules} deps. */
  abstract ImmutableSet<TypeElement> aggregatedUninstallModulesDeps();

  /** Returns the {@link dagger.hilt.android.earlyentrypoint.AggregatedEarlyEntryPoint} deps. */
  abstract ImmutableSet<TypeElement> aggregatedEarlyEntryPointDeps();

  static ComponentTreeDepsMetadata from(TypeElement element, Elements elements) {
    checkArgument(Processors.hasAnnotation(element, ClassNames.COMPONENT_TREE_DEPS));
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.COMPONENT_TREE_DEPS);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    return create(
        ClassName.get(element),
        unwrapProxies(getTypeElements(values.get("rootDeps")), elements),
        unwrapProxies(getTypeElements(values.get("defineComponentDeps")), elements),
        unwrapProxies(getTypeElements(values.get("aliasOfDeps")), elements),
        unwrapProxies(getTypeElements(values.get("aggregatedDeps")), elements),
        unwrapProxies(getTypeElements(values.get("uninstallModulesDeps")), elements),
        unwrapProxies(getTypeElements(values.get("earlyEntryPointDeps")), elements));
  }

  static ComponentTreeDepsMetadata from(ComponentTreeDepsIr ir, Elements elements) {
    return create(
        ir.getName(),
        ir.getRootDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getDefineComponentDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getAliasOfDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getAggregatedDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getUninstallModulesDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()),
        ir.getEarlyEntryPointDeps().stream()
            .map(it -> elements.getTypeElement(it.canonicalName()))
            .collect(toImmutableSet()));
  }

  /** Returns all modules included in a component tree deps. */
  public ImmutableSet<TypeElement> modules(Elements elements) {
    return AggregatedDepsMetadata.from(aggregatedDeps(), elements).stream()
        .filter(AggregatedDepsMetadata::isModule)
        .map(AggregatedDepsMetadata::dependency)
        .collect(toImmutableSet());
  }

  /** Returns all entry points included in a component tree deps. */
  public ImmutableSet<TypeElement> entrypoints(Elements elements) {
    return AggregatedDepsMetadata.from(aggregatedDeps(), elements).stream()
        .filter(dependency -> !dependency.isModule())
        .map(AggregatedDepsMetadata::dependency)
        .collect(toImmutableSet());
  }

  static ComponentTreeDepsMetadata create(
      ClassName name,
      ImmutableSet<TypeElement> aggregatedRootDeps,
      ImmutableSet<TypeElement> defineComponentDeps,
      ImmutableSet<TypeElement> aliasOfDeps,
      ImmutableSet<TypeElement> aggregatedDeps,
      ImmutableSet<TypeElement> aggregatedUninstallModulesDeps,
      ImmutableSet<TypeElement> aggregatedEarlyEntryPointDeps) {
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
