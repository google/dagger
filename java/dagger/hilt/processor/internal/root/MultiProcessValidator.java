/*
 * Copyright (C) 2022 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.difference;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Validator for module usages in a multiprocess application. */
final class MultiProcessValidator {

  /**
   * Check if modules annotated with {@link dagger.hilt.android.multiprocess.RequiredInAllProcesses}
   * are installed across all processes.
   */
  public static void validateModulesInstalledOnAllProcesses(
      ImmutableMap<String, PerProcessRootPropagatedDataMetadata> perProcessRootMetadata,
      ProcessingEnvironment processingEnv) {
    ImmutableSetMultimap<String, TypeElement> modulesPerProcess =
        processToAllProcessesModules(perProcessRootMetadata, processingEnv);
    ImmutableSet<TypeElement> allModules = ImmutableSet.copyOf(modulesPerProcess.values());
    for (Map.Entry<String, PerProcessRootPropagatedDataMetadata> metadata :
        perProcessRootMetadata.entrySet()) {
      Set<TypeElement> diffModules =
          difference(allModules, modulesPerProcess.get(metadata.getKey()));
      checkState(
          diffModules.isEmpty(),
          "Modules annotated with @RequiredInAllProcesses should be installed into all processes,"
              + " %s are not installed in process root %s",
          diffModules,
          processRoot(metadata.getValue()));
    }
  }

  /**
   * Returns a map mapping a process name to module on that process which has a {@link
   * dagger.hilt.android.multiprocess.RequiredInAllProcesses} annotation on it.
   */
  private static ImmutableSetMultimap<String, TypeElement> processToAllProcessesModules(
      ImmutableMap<String, PerProcessRootPropagatedDataMetadata> perProcessRootMetadata,
      ProcessingEnvironment processingEnv) {

    ImmutableSetMultimap.Builder<String, TypeElement> allProcessesModules =
        ImmutableSetMultimap.builder();
    perProcessRootMetadata.values().stream()
        .map(
            metadata ->
                ComponentTreeDepsMetadata.from(
                    processingEnv
                        .getElementUtils()
                        .getTypeElement(processRoot(metadata) + "_ComponentTreeDeps"),
                    processingEnv.getElementUtils()))
        .forEach(
            componentTreeDepsMetadata ->
                allProcessesModules.putAll(
                    processName(componentTreeDepsMetadata, processingEnv),
                    allProcessesModules(
                        componentTreeDepsMetadata, processingEnv.getElementUtils())));

    return allProcessesModules.build();
  }

  private static String processRoot(PerProcessRootPropagatedDataMetadata perProcessRootMetadata) {
    return perProcessRootMetadata
        .singletonComponent()
        .getEnclosingElement()
        .toString()
        .replace("_HiltComponents", "");
  }

  private static String processName(
      ComponentTreeDepsMetadata componentTreeDepsMetadata, ProcessingEnvironment processingEnv) {
    TypeElement rootElement =
        getOnlyElement(
                AggregatedRootMetadata.from(
                    componentTreeDepsMetadata.aggregatedRootDeps(), processingEnv))
            .rootElement();
    return AnnotationValues.getString(
        Processors.getAnnotationValues(
                processingEnv.getElementUtils(),
                Processors.getAnnotationMirror(rootElement, ClassNames.PER_PROCESS_ROOT))
            .get("value"));
  }

  private static ImmutableSet<TypeElement> allProcessesModules(
      ComponentTreeDepsMetadata componentTreeDepsMetadata, Elements elements) {
    return componentTreeDepsMetadata.modules(elements).stream()
        .map(
            module -> {
              // Wrapper modules are generated for package private modules, and
              // @RequiredInAllProcesses is not copied over it, so we need to unwrapped it first.
              if (module.getSimpleName().toString().startsWith("HiltWrapper_")) {
                module =
                    getOnlyElement(
                        AnnotationValues.getTypeElements(
                            Processors.getAnnotationValues(
                                    elements,
                                    Processors.getAnnotationMirror(module, ClassNames.MODULE))
                                .get("includes")));
              }
              return module;
            })
        .filter(module -> Processors.hasAnnotation(module, ClassNames.REQUIRED_IN_ALL_PROCESSES))
        .collect(toImmutableSet());
  }

  private MultiProcessValidator() {}
}
