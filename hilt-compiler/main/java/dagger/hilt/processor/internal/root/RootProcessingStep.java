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
import static java.util.Arrays.stream;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BadInputException;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.aliasof.AliasOfPropagatedDataMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentClassesMetadata;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import dagger.hilt.processor.internal.root.ir.AggregatedDepsIr;
import dagger.hilt.processor.internal.root.ir.AggregatedEarlyEntryPointIr;
import dagger.hilt.processor.internal.root.ir.AggregatedRootIr;
import dagger.hilt.processor.internal.root.ir.AggregatedRootIrValidator;
import dagger.hilt.processor.internal.root.ir.AggregatedUninstallModulesIr;
import dagger.hilt.processor.internal.root.ir.AliasOfPropagatedDataIr;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIr;
import dagger.hilt.processor.internal.root.ir.ComponentTreeDepsIrCreator;
import dagger.hilt.processor.internal.root.ir.DefineComponentClassesIr;
import dagger.hilt.processor.internal.root.ir.InvalidRootsException;
import dagger.hilt.processor.internal.root.ir.ProcessedRootSentinelIr;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Set;

/** Processor that outputs dagger components based on transitive build deps. */
public final class RootProcessingStep extends BaseProcessingStep {

  private boolean processed;
  // TODO(b/297889547) do not run preProcess and postProcess if supported annotation isn't present
  // in the environment.
  private boolean hasElementsToProcess = false;
  private GeneratesRootInputs generatesRootInputs;

  public RootProcessingStep(XProcessingEnv env) {
    super(env);
    generatesRootInputs = new GeneratesRootInputs(processingEnv());
  }

  private Mode getMode() {
    return useAggregatingRootProcessor(processingEnv()) ? Mode.Aggregating : Mode.Isolating;
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return stream(RootType.values()).map(RootType::className).collect(toImmutableSet());
  }

  @Override
  public void processEach(ClassName annotation, XElement element) throws Exception {
    hasElementsToProcess = true;
    XTypeElement rootElement = XElements.asTypeElement(element);
    // TODO(bcorso): Move this logic into a separate isolating processor to avoid regenerating it
    // for unrelated changes in Gradle.
    RootType rootType = RootType.of(rootElement);
    if (rootType.isTestRoot()) {
      TestRootMetadata testRootMetadata = TestRootMetadata.of(processingEnv(), rootElement);
      if (testRootMetadata.skipTestInjectionAnnotation().isEmpty()) {
        new TestInjectorGenerator(processingEnv(), testRootMetadata).generate();
      }
    }

    Root root = Root.create(rootElement, processingEnv());
    new AggregatedRootGenerator(
            rootElement,
            root.originatingRootElement(),
            processingEnv().requireTypeElement(annotation),
            root.rootComponentName())
        .generate();
  }

  @Override
  protected void postProcess(XProcessingEnv env, XRoundEnv roundEnv) throws Exception {
    if (!hasElementsToProcess) {
      return;
    }
    if (!useAggregatingRootProcessor(processingEnv())) {
      return;
    }
    ImmutableSet<XElement> newElements =
        generatesRootInputs.getElementsToWaitFor(roundEnv).stream().collect(toImmutableSet());
    if (processed) {
      checkState(
          newElements.isEmpty(),
          "Found extra modules after compilation: %s\n"
              + "(If you are adding an annotation processor that generates root input for hilt, "
              + "the annotation must be annotated with @dagger.hilt.GeneratesRootInput.\n)",
          newElements.stream().map(XElements::toStableString).collect(toImmutableList()));
    } else if (newElements.isEmpty()) {
      processed = true;

      ImmutableSet<AggregatedRootIr> rootsToProcess = rootsToProcess();
      if (rootsToProcess.isEmpty()) {
        return;
      }
      // Generate an @ComponentTreeDeps for each unique component tree.
      ComponentTreeDepsGenerator componentTreeDepsGenerator =
          new ComponentTreeDepsGenerator(processingEnv(), getMode());
      for (ComponentTreeDepsMetadata metadata : componentTreeDepsMetadatas(rootsToProcess)) {
        componentTreeDepsGenerator.generate(metadata);
      }

      // Generate a sentinel for all processed roots.
      for (AggregatedRootIr ir : rootsToProcess) {
        XTypeElement rootElement = processingEnv().requireTypeElement(ir.getRoot().canonicalName());
        new ProcessedRootSentinelGenerator(rootElement, getMode()).generate();
      }
    }
  }

  private ImmutableSet<AggregatedRootIr> rootsToProcess() {
    ImmutableSet<ProcessedRootSentinelIr> processedRoots =
        ProcessedRootSentinelMetadata.from(processingEnv()).stream()
            .map(ProcessedRootSentinelMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedRootIr> aggregatedRoots =
        AggregatedRootMetadata.from(processingEnv()).stream()
            .map(AggregatedRootMetadata::toIr)
            .collect(toImmutableSet());

    boolean isCrossCompilationRootValidationDisabled =
        isCrossCompilationRootValidationDisabled(
            aggregatedRoots.stream()
                .map(ir -> processingEnv().requireTypeElement(ir.getRoot().canonicalName()))
                .collect(toImmutableSet()),
            processingEnv());
    try {
      return ImmutableSet.copyOf(
          AggregatedRootIrValidator.rootsToProcess(
              isCrossCompilationRootValidationDisabled, processedRoots, aggregatedRoots));
    } catch (InvalidRootsException ex) {
      throw new BadInputException(ex.getMessage());
    }
  }

  private ImmutableSet<ComponentTreeDepsMetadata> componentTreeDepsMetadatas(
      ImmutableSet<AggregatedRootIr> aggregatedRoots) {
    ImmutableSet<DefineComponentClassesIr> defineComponentDeps =
        DefineComponentClassesMetadata.from(processingEnv()).stream()
            .map(DefineComponentClassesMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AliasOfPropagatedDataIr> aliasOfDeps =
        AliasOfPropagatedDataMetadata.from(processingEnv()).stream()
            .map(AliasOfPropagatedDataMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedDepsIr> aggregatedDeps =
        AggregatedDepsMetadata.from(processingEnv()).stream()
            .map(AggregatedDepsMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedUninstallModulesIr> aggregatedUninstallModulesDeps =
        AggregatedUninstallModulesMetadata.from(processingEnv()).stream()
            .map(AggregatedUninstallModulesMetadata::toIr)
            .collect(toImmutableSet());
    ImmutableSet<AggregatedEarlyEntryPointIr> aggregatedEarlyEntryPointDeps =
        AggregatedEarlyEntryPointMetadata.from(processingEnv()).stream()
            .map(AggregatedEarlyEntryPointMetadata::toIr)
            .collect(toImmutableSet());

    Set<ComponentTreeDepsIr> componentTreeDeps =
        ComponentTreeDepsIrCreator.components(
            isSharedTestComponentsEnabled(processingEnv()),
            aggregatedRoots,
            defineComponentDeps,
            aliasOfDeps,
            aggregatedDeps,
            aggregatedUninstallModulesDeps,
            aggregatedEarlyEntryPointDeps);
    return componentTreeDeps.stream()
        .map(it -> ComponentTreeDepsMetadata.from(it, processingEnv()))
        .collect(toImmutableSet());
  }
}
