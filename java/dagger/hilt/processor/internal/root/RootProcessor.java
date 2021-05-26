/*
 * Copyright (C) 2019 The Dagger Authors.
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
import static dagger.hilt.processor.internal.HiltCompilerOptions.isCrossCompilationRootValidationDisabled;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isSharedTestComponentsEnabled;
import static dagger.hilt.processor.internal.HiltCompilerOptions.useAggregatingRootProcessor;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Comparator.comparing;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.DYNAMIC;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.aliasof.AliasOfPropagatedDataMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentClassesMetadata;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import dagger.hilt.processor.internal.root.ir.AggregatedDepsIr;
import dagger.hilt.processor.internal.root.ir.AggregatedEarlyEntryPointIr;
import dagger.hilt.processor.internal.root.ir.AggregatedRootIr;
import dagger.hilt.processor.internal.root.ir.AggregatedUninstallModulesIr;
import dagger.hilt.processor.internal.root.ir.AliasOfPropagatedDataIr;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIrCreator;
import dagger.hilt.processor.internal.root.ir.DefineComponentClassesIr;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dagger components based on transitive build deps. */
@IncrementalAnnotationProcessor(DYNAMIC)
@AutoService(Processor.class)
public final class RootProcessor extends BaseProcessor {
  private static final Comparator<TypeElement> QUALIFIED_NAME_COMPARATOR =
      comparing(TypeElement::getQualifiedName, (n1, n2) -> n1.toString().compareTo(n2.toString()));

  private boolean processed;
  private GeneratesRootInputs generatesRootInputs;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    generatesRootInputs = new GeneratesRootInputs(processingEnvironment);
  }

  @Override
  public ImmutableSet<String> additionalProcessingOptions() {
    return useAggregatingRootProcessor(getProcessingEnv())
        ? ImmutableSet.of(AGGREGATING.getProcessorOption())
        : ImmutableSet.of(ISOLATING.getProcessorOption());
  }

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.<String>builder()
        .addAll(
            Arrays.stream(RootType.values())
                .map(rootType -> rootType.className().toString())
                .collect(toImmutableSet()))
        .build();
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    TypeElement rootElement = MoreElements.asType(element);
    // TODO(bcorso): Move this logic into a separate isolating processor to avoid regenerating it
    // for unrelated changes in Gradle.
    RootType rootType = RootType.of(rootElement);
    if (rootType.isTestRoot()) {
      new TestInjectorGenerator(
              getProcessingEnv(), TestRootMetadata.of(getProcessingEnv(), rootElement))
          .generate();
    }
    TypeElement originatingRootElement =
        Root.create(rootElement, getProcessingEnv()).originatingRootElement();
    new AggregatedRootGenerator(rootElement, originatingRootElement, annotation, getProcessingEnv())
        .generate();
  }

  @Override
  public void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
    if (!useAggregatingRootProcessor(getProcessingEnv())) {
      return;
    }
    Set<Element> newElements = generatesRootInputs.getElementsToWaitFor(roundEnv);
    if (processed) {
      checkState(
          newElements.isEmpty(),
          "Found extra modules after compilation: %s\n"
              + "(If you are adding an annotation processor that generates root input for hilt, "
              + "the annotation must be annotated with @dagger.hilt.GeneratesRootInput.\n)",
          newElements);
    } else if (newElements.isEmpty()) {
      processed = true;

      ImmutableList<TypeElement> rootsToProcess = rootsToProcess();
      if (rootsToProcess.isEmpty()) {
        return;
      }

      // Generate an @ComponentTreeDeps for each unique component tree.
      ComponentTreeDepsGenerator componentTreeDepsGenerator =
          new ComponentTreeDepsGenerator(getProcessingEnv());
      for (ComponentTreeDepsMetadata metadata : componentTreeDepsMetadatas(rootsToProcess)) {
        componentTreeDepsGenerator.generate(metadata);
      }

      // Generate a sentinel for all processed roots.
      for (TypeElement rootElement : rootsToProcess) {
        new ProcessedRootSentinelGenerator(rootElement, getProcessingEnv()).generate();
      }
    }
  }

  private ImmutableList<TypeElement> rootsToProcess() {
    ImmutableSet<Root> allRoots =
        AggregatedRootMetadata.from(processingEnv).stream()
            .map(metadata -> Root.create(metadata.rootElement(), getProcessingEnv()))
            .collect(toImmutableSet());

    ImmutableSet<Root> processedRoots =
        ProcessedRootSentinelMetadata.from(getElementUtils()).stream()
            .flatMap(metadata -> metadata.rootElements().stream())
            .map(rootElement -> Root.create(rootElement, getProcessingEnv()))
            .collect(toImmutableSet());

    ImmutableSet<Root> rootsToProcess =
        allRoots.stream().filter(root -> !processedRoots.contains(root)).collect(toImmutableSet());

    ImmutableSet<TypeElement> rootElementsToProcess =
        rootsToProcess.stream()
            .map(Root::element)
            .sorted(QUALIFIED_NAME_COMPARATOR)
            .collect(toImmutableSet());

    ImmutableSet<TypeElement> appRootElementsToProcess =
        rootsToProcess.stream()
            .filter(root -> !root.isTestRoot())
            .map(Root::element)
            .sorted(QUALIFIED_NAME_COMPARATOR)
            .collect(toImmutableSet());

    // Perform validation between roots in this compilation unit.
    if (!appRootElementsToProcess.isEmpty()) {
      ImmutableSet<TypeElement> testRootElementsToProcess =
          rootsToProcess.stream()
              .filter(Root::isTestRoot)
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      ProcessorErrors.checkState(
          testRootElementsToProcess.isEmpty(),
          "Cannot process test roots and app roots in the same compilation unit:"
              + "\n\tApp root in this compilation unit: %s"
              + "\n\tTest roots in this compilation unit: %s",
          appRootElementsToProcess,
          testRootElementsToProcess);

      ProcessorErrors.checkState(
          appRootElementsToProcess.size() == 1,
          "Cannot process multiple app roots in the same compilation unit: %s",
          appRootElementsToProcess);
    }

    // Perform validation across roots previous compilation units.
    if (!isCrossCompilationRootValidationDisabled(rootElementsToProcess, getProcessingEnv())) {
      ImmutableSet<TypeElement> processedTestRootElements =
          allRoots.stream()
              .filter(Root::isTestRoot)
              .filter(root -> !rootsToProcess.contains(root))
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      // TODO(b/185742783): Add an explanation or link to docs to explain why we're forbidding this.
      ProcessorErrors.checkState(
          processedTestRootElements.isEmpty(),
          "Cannot process new roots when there are test roots from a previous compilation unit:"
              + "\n\tTest roots from previous compilation unit: %s"
              + "\n\tAll roots from this compilation unit: %s",
          processedTestRootElements,
          rootElementsToProcess);

      ImmutableSet<TypeElement> processedAppRootElements =
          allRoots.stream()
              .filter(root -> !root.isTestRoot())
              .filter(root -> !rootsToProcess.contains(root))
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      ProcessorErrors.checkState(
          processedAppRootElements.isEmpty() || appRootElementsToProcess.isEmpty(),
          "Cannot process app roots in this compilation unit since there are app roots in a "
              + "previous compilation unit:"
              + "\n\tApp roots in previous compilation unit: %s"
              + "\n\tApp roots in this compilation unit: %s",
          processedAppRootElements,
          appRootElementsToProcess);
    }
    return rootsToProcess.stream().map(Root::element).collect(toImmutableList());
  }

  private ImmutableSet<ComponentTreeDepsMetadata> componentTreeDepsMetadatas(
      ImmutableList<TypeElement> rootElementsToProcess) {
    ImmutableSet<AggregatedRootMetadata> aggregatedRootMetadatas =
        AggregatedRootMetadata.from(processingEnv).stream()
            // Filter to only the root elements that need processing.
            .filter(metadata -> rootElementsToProcess.contains(metadata.rootElement()))
            .collect(toImmutableSet());
    // We should be guaranteed that there are no mixed roots, so check if this is prod or test.
    boolean isTest =
        aggregatedRootMetadatas.stream().anyMatch(metadata -> metadata.rootType().isTestRoot());
    ImmutableSet<AggregatedRootIr> aggregatedRoots =
        aggregatedRootMetadatas.stream()
            .map(AggregatedRootMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<DefineComponentClassesIr> defineComponentDeps =
        DefineComponentClassesMetadata.from(getElementUtils()).stream()
            .map(DefineComponentClassesMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AliasOfPropagatedDataIr> aliasOfDeps =
        AliasOfPropagatedDataMetadata.from(getElementUtils()).stream()
            .map(AliasOfPropagatedDataMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedDepsIr> aggregatedDeps =
        AggregatedDepsMetadata.from(getElementUtils()).stream()
            .map(AggregatedDepsMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedUninstallModulesIr> aggregatedUninstallModulesDeps =
        AggregatedUninstallModulesMetadata.from(getElementUtils()).stream()
            .map(AggregatedUninstallModulesMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedEarlyEntryPointIr> aggregatedEarlyEntryPointDeps =
        AggregatedEarlyEntryPointMetadata.from(getElementUtils()).stream()
            .map(AggregatedEarlyEntryPointMetadata::toIr)
            .collect(toImmutableSet());
    Set<ComponentTreeDepsIr> componentTreeDeps =
        ComponentTreeDepsIrCreator.components(
            isTest,
            isSharedTestComponentsEnabled(processingEnv),
            aggregatedRoots,
            defineComponentDeps,
            aliasOfDeps,
            aggregatedDeps,
            aggregatedUninstallModulesDeps,
            aggregatedEarlyEntryPointDeps);
    return componentTreeDeps.stream()
        .map(it -> ComponentTreeDepsMetadata.from(it, getElementUtils()))
        .collect(toImmutableSet());
  }
}
