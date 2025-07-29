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

package dagger.hilt.processor.internal.aggregateddeps;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.AggregatedDepsIr;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.processor.internal.aggregateddeps.AggregatedDeps} annotation.
 */
@AutoValue
public abstract class AggregatedDepsMetadata {
  private static final String AGGREGATED_DEPS_PACKAGE = "hilt_aggregated_deps";

  enum DependencyType {
    MODULE,
    ENTRY_POINT,
    COMPONENT_ENTRY_POINT
  }

  /** Returns the aggregating element */
  public abstract XTypeElement aggregatingElement();

  public abstract Optional<XTypeElement> testElement();

  public abstract ImmutableSet<XTypeElement> componentElements();

  abstract DependencyType dependencyType();

  public abstract XTypeElement dependency();

  public abstract ImmutableSet<XTypeElement> replacedDependencies();

  public boolean isModule() {
    return dependencyType() == DependencyType.MODULE;
  }

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AggregatedDepsMetadata> from(XProcessingEnv env) {
    return from(AggregatedElements.from(AGGREGATED_DEPS_PACKAGE, ClassNames.AGGREGATED_DEPS, env));
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AggregatedDepsMetadata> from(
      ImmutableSet<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, getProcessingEnv(aggregatedElement)))
        .collect(toImmutableSet());
  }

  public static AggregatedDepsIr toIr(AggregatedDepsMetadata metadata) {
    return new AggregatedDepsIr(
        metadata.aggregatingElement().getClassName(),
        metadata.componentElements().stream()
            .map(XTypeElement::getClassName)
            .map(ClassName::canonicalName)
            .collect(Collectors.toList()),
        metadata
            .testElement()
            .map(XTypeElement::getClassName)
            .map(ClassName::canonicalName)
            .orElse(null),
        metadata.replacedDependencies().stream()
            .map(XTypeElement::getClassName)
            .map(ClassName::canonicalName)
            .collect(Collectors.toList()),
        metadata.dependencyType() == DependencyType.MODULE
            ? metadata.dependency().getClassName().canonicalName()
            : null,
        metadata.dependencyType() == DependencyType.ENTRY_POINT
            ? metadata.dependency().getClassName().canonicalName()
            : null,
        metadata.dependencyType() == DependencyType.COMPONENT_ENTRY_POINT
            ? metadata.dependency().getClassName().canonicalName()
            : null);
  }

  private static AggregatedDepsMetadata create(XTypeElement element, XProcessingEnv env) {
    checkState(
        element.hasAnnotation(ClassNames.AGGREGATED_DEPS),
        "Missing @AggregatedDeps annotation on %s",
        element.getClassName().canonicalName());
    XAnnotation annotation = element.getAnnotation(ClassNames.AGGREGATED_DEPS);
    return new AutoValue_AggregatedDepsMetadata(
        element,
        getTestElement(annotation.getAnnotationValue("test"), env),
        getComponents(annotation.getAnnotationValue("components"), env),
        getDependencyType(
            annotation.getAnnotationValue("modules"),
            annotation.getAnnotationValue("entryPoints"),
            annotation.getAnnotationValue("componentEntryPoints")),
        getDependency(
            annotation.getAnnotationValue("modules"),
            annotation.getAnnotationValue("entryPoints"),
            annotation.getAnnotationValue("componentEntryPoints"),
            env),
        getReplacedDependencies(annotation.getAnnotationValue("replaces"), env));
  }

  private static Optional<XTypeElement> getTestElement(
      XAnnotationValue testValue, XProcessingEnv env) {
    checkNotNull(testValue);
    String test = testValue.asString();
    return test.isEmpty() ? Optional.empty() : Optional.of(env.findTypeElement(test));
  }

  private static ImmutableSet<XTypeElement> getComponents(
      XAnnotationValue componentsValue, XProcessingEnv env) {
    checkNotNull(componentsValue);
    ImmutableSet<XTypeElement> componentNames =
        componentsValue.asStringList().stream()
            .map(
                // This is a temporary hack to map the old ApplicationComponent to the new
                // SingletonComponent. Technically, this is only needed for backwards compatibility
                // with libraries using the old processor since new processors should convert to the
                // new SingletonComponent when generating the metadata class.
                componentName ->
                    componentName.contentEquals(
                            "dagger.hilt.android.components.ApplicationComponent")
                        ? ClassNames.SINGLETON_COMPONENT.canonicalName()
                        : componentName)
            .map(env::requireTypeElement)
            .collect(toImmutableSet());
    checkState(!componentNames.isEmpty());
    return componentNames;
  }

  private static DependencyType getDependencyType(
      XAnnotationValue modulesValue,
      XAnnotationValue entryPointsValue,
      XAnnotationValue componentEntryPointsValue) {
    checkNotNull(modulesValue);
    checkNotNull(entryPointsValue);
    checkNotNull(componentEntryPointsValue);

    ImmutableSet.Builder<DependencyType> dependencyTypes = ImmutableSet.builder();
    if (!modulesValue.asAnnotationValueList().isEmpty()) {
      dependencyTypes.add(DependencyType.MODULE);
    }
    if (!entryPointsValue.asAnnotationValueList().isEmpty()) {
      dependencyTypes.add(DependencyType.ENTRY_POINT);
    }
    if (!componentEntryPointsValue.asAnnotationValueList().isEmpty()) {
      dependencyTypes.add(DependencyType.COMPONENT_ENTRY_POINT);
    }
    return getOnlyElement(dependencyTypes.build());
  }

  private static XTypeElement getDependency(
      XAnnotationValue modulesValue,
      XAnnotationValue entryPointsValue,
      XAnnotationValue componentEntryPointsValue,
      XProcessingEnv env) {
    checkNotNull(modulesValue);
    checkNotNull(entryPointsValue);
    checkNotNull(componentEntryPointsValue);

    String dependencyName =
        getOnlyElement(
                ImmutableSet.<XAnnotationValue>builder()
                    .addAll(modulesValue.asAnnotationValueList())
                    .addAll(entryPointsValue.asAnnotationValueList())
                    .addAll(componentEntryPointsValue.asAnnotationValueList())
                    .build())
            .asString();
    XTypeElement dependency = env.findTypeElement(dependencyName);
    checkNotNull(dependency, "Could not get element for %s", dependencyName);
    return dependency;
  }

  private static ImmutableSet<XTypeElement> getReplacedDependencies(
      XAnnotationValue replacedDependenciesValue, XProcessingEnv env) {
    // Allow null values to support libraries using a Hilt version before @TestInstallIn was added
    return replacedDependenciesValue == null
        ? ImmutableSet.of()
        : replacedDependenciesValue.asStringList().stream()
            .map(env::requireTypeElement)
            .map(replacedDep -> getPublicDependency(replacedDep, env))
            .collect(toImmutableSet());
  }

  /** Returns the public Hilt wrapper module, or the module itself if its already public. */
  private static XTypeElement getPublicDependency(XTypeElement dependency, XProcessingEnv env) {
    return PkgPrivateMetadata.of(dependency, ClassNames.MODULE)
        .map(metadata -> env.requireTypeElement(metadata.generatedClassName().toString()))
        .orElse(dependency);
  }
}
