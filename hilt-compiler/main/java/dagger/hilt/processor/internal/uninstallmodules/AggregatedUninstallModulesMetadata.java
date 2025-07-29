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

package dagger.hilt.processor.internal.uninstallmodules;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.root.ir.AggregatedUninstallModulesIr;
import java.util.stream.Collectors;

/**
 * A class that represents the values stored in an
 * {@link dagger.hilt.android.internal.uninstallmodules.AggregatedUninstallModules} annotation.
 */
@AutoValue
public abstract class AggregatedUninstallModulesMetadata {

  /** Returns the aggregating element */
  public abstract XTypeElement aggregatingElement();

  /** Returns the test annotated with {@link dagger.hilt.android.testing.UninstallModules}. */
  public abstract XTypeElement testElement();

  /**
   * Returns the list of uninstall modules in {@link dagger.hilt.android.testing.UninstallModules}.
   */
  public abstract ImmutableList<XTypeElement> uninstallModuleElements();

  /** Returns metadata for all aggregated elements in the aggregating package. */
  public static ImmutableSet<AggregatedUninstallModulesMetadata> from(XProcessingEnv env) {
    return from(
        AggregatedElements.from(
            ClassNames.AGGREGATED_UNINSTALL_MODULES_PACKAGE,
            ClassNames.AGGREGATED_UNINSTALL_MODULES,
            env));
  }

  /** Returns metadata for each aggregated element. */
  public static ImmutableSet<AggregatedUninstallModulesMetadata> from(
      ImmutableSet<XTypeElement> aggregatedElements) {
    return aggregatedElements.stream()
        .map(aggregatedElement -> create(aggregatedElement, getProcessingEnv(aggregatedElement)))
        .collect(toImmutableSet());
  }

  public static AggregatedUninstallModulesIr toIr(AggregatedUninstallModulesMetadata metadata) {
    return new AggregatedUninstallModulesIr(
        metadata.aggregatingElement().getClassName(),
        metadata.testElement().getClassName().canonicalName(),
        metadata.uninstallModuleElements().stream()
            .map(XTypeElement::getClassName)
            .map(ClassName::canonicalName)
            .collect(Collectors.toList()));
  }

  private static AggregatedUninstallModulesMetadata create(
      XTypeElement element, XProcessingEnv env) {
    XAnnotation annotationMirror = element.getAnnotation(ClassNames.AGGREGATED_UNINSTALL_MODULES);

    return new AutoValue_AggregatedUninstallModulesMetadata(
        element,
        env.requireTypeElement(annotationMirror.getAsString("test")),
        annotationMirror.getAsStringList("uninstallModules").stream()
            .map(env::requireTypeElement)
            .collect(toImmutableList()));
  }
}
