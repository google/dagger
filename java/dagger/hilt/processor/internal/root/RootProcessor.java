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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isCrossCompilationRootValidationDisabled;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isSharedTestComponentsEnabled;
import static dagger.hilt.processor.internal.HiltCompilerOptions.useAggregatingRootProcessor;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Comparator.comparing;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.DYNAMIC;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.aliasof.AliasOfPropagatedDataMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponentClassesMetadata;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
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
    new AggregatedRootGenerator(rootElement, annotation, getProcessingEnv()).generate();
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
        AggregatedRootMetadata.from(getElementUtils()).stream()
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
        AggregatedRootMetadata.from(getElementUtils()).stream()
            // Filter to only the root elements that need processing.
            .filter(metadata -> rootElementsToProcess.contains(metadata.rootElement()))
            .collect(toImmutableSet());

    // We should be guaranteed that there are no mixed roots, so check if this is prod or test.
    return aggregatedRootMetadatas.stream().anyMatch(metadata -> metadata.rootType().isTestRoot())
        ? testComponentTreeDepsMetadatas(aggregatedRootMetadatas)
        : prodComponentTreeDepsMetadatas(aggregatedRootMetadatas);
  }

  private ImmutableSet<ComponentTreeDepsMetadata> prodComponentTreeDepsMetadatas(
      ImmutableSet<AggregatedRootMetadata> aggregatedRootMetadatas) {
    // There should only be one prod root in a given build, so get the only element.
    AggregatedRootMetadata aggregatedRootMetadata = getOnlyElement(aggregatedRootMetadatas);
    ClassName rootName = ClassName.get(aggregatedRootMetadata.rootElement());
    return ImmutableSet.of(
        ComponentTreeDepsMetadata.create(
            ComponentNames.withoutRenaming().generatedComponentTreeDeps(rootName),
            ImmutableSet.of(aggregatedRootMetadata.aggregatingElement()),
            defineComponentDeps(getElementUtils()),
            aliasOfDeps(getElementUtils()),
            AggregatedDepsMetadata.from(getElementUtils()).stream()
                // @AggregatedDeps with non-empty replacedDependencies are from @TestInstallIn and
                // should not be installed in production components
                .filter(metadata -> metadata.replacedDependencies().isEmpty())
                .map(AggregatedDepsMetadata::aggregatingElement)
                .collect(toImmutableSet()),
            /* aggregatedUninstallModulesDeps= */ ImmutableSet.of(),
            /* aggregatedEarlyEntryPointDeps= */ ImmutableSet.of()));
  }

  private ImmutableSet<ComponentTreeDepsMetadata> testComponentTreeDepsMetadatas(
      ImmutableSet<AggregatedRootMetadata> aggregatedRootMetadatas) {
    ImmutableSet<AggregatedDepsMetadata> aggregatedDepsMetadatas =
        AggregatedDepsMetadata.from(getElementUtils());

    ImmutableSet<AggregatedUninstallModulesMetadata> uninstallModulesMetadatas =
        AggregatedUninstallModulesMetadata.from(getElementUtils());

    ImmutableSet<AggregatedEarlyEntryPointMetadata> earlyEntryPointMetadatas =
        AggregatedEarlyEntryPointMetadata.from(getElementUtils());

    ImmutableSet<TypeElement> rootsUsingSharedComponent =
        rootsUsingSharedComponent(
            aggregatedRootMetadatas, aggregatedDepsMetadatas, uninstallModulesMetadatas);

    ImmutableMap<TypeElement, TypeElement> aggregatedRootsByRoot =
        aggregatedRootMetadatas.stream()
            .collect(
                toImmutableMap(
                    AggregatedRootMetadata::rootElement,
                    AggregatedRootMetadata::aggregatingElement));

    ImmutableSetMultimap<TypeElement, TypeElement> aggregatedDepsByRoot =
        aggregatedDepsByRoot(
            aggregatedRootMetadatas,
            aggregatedDepsMetadatas,
            rootsUsingSharedComponent,
            !earlyEntryPointMetadatas.isEmpty());

    ImmutableMap<TypeElement, TypeElement> uninstallModuleDepsByRoot =
        uninstallModulesMetadatas.stream()
            .collect(
                toImmutableMap(
                    AggregatedUninstallModulesMetadata::testElement,
                    AggregatedUninstallModulesMetadata::aggregatingElement));

    ComponentNames componentNames =
        isSharedTestComponentsEnabled(getProcessingEnv())
            ? ComponentNames.withRenamingIntoPackage(
                ClassNames.DEFAULT_ROOT.packageName(), aggregatedDepsByRoot.keySet())
            : ComponentNames.withoutRenaming();

    ImmutableSet.Builder<ComponentTreeDepsMetadata> builder = ImmutableSet.builder();
    for (TypeElement rootElement : aggregatedDepsByRoot.keySet()) {
      boolean isDefaultRoot = ClassNames.DEFAULT_ROOT.equals(ClassName.get(rootElement));
      boolean isEarlyEntryPointRoot = isDefaultRoot && rootsUsingSharedComponent.isEmpty();
      // We want to base the generated name on the user written root rather than a generated root.
      ClassName rootName = Root.create(rootElement, getProcessingEnv()).originatingRootClassname();
      builder.add(
          ComponentTreeDepsMetadata.create(
              componentNames.generatedComponentTreeDeps(rootName),
              // Non-default component: the root
              // Shared component: all roots sharing the component
              // EarlyEntryPoint component: empty
              isDefaultRoot
                  ? rootsUsingSharedComponent.stream()
                      .map(aggregatedRootsByRoot::get)
                      .collect(toImmutableSet())
                  : ImmutableSet.of(aggregatedRootsByRoot.get(rootElement)),
              defineComponentDeps(getElementUtils()),
              aliasOfDeps(getElementUtils()),
              aggregatedDepsByRoot.get(rootElement),
              uninstallModuleDepsByRoot.containsKey(rootElement)
                  ? ImmutableSet.of(uninstallModuleDepsByRoot.get(rootElement))
                  : ImmutableSet.of(),
              isEarlyEntryPointRoot
                  ? earlyEntryPointMetadatas.stream()
                      .map(AggregatedEarlyEntryPointMetadata::aggregatingElement)
                      .collect(toImmutableSet())
                  : ImmutableSet.of()));
    }
    return builder.build();
  }

  private ImmutableSetMultimap<TypeElement, TypeElement> aggregatedDepsByRoot(
      ImmutableSet<AggregatedRootMetadata> aggregatedRootMetadatas,
      ImmutableSet<AggregatedDepsMetadata> aggregatedDepsMetadatas,
      ImmutableSet<TypeElement> rootsUsingSharedComponent,
      boolean hasEarlyEntryPoints) {
    ListMultimap<TypeElement, TypeElement> testDepsByRoot = ArrayListMultimap.create();
    ListMultimap<TypeElement, TypeElement> globalEntryPointsByComponent =
        ArrayListMultimap.create();
    List<TypeElement> globalModules = new ArrayList<>();
    for (AggregatedDepsMetadata metadata : aggregatedDepsMetadatas) {
      if (metadata.testElement().isPresent()) {
        testDepsByRoot.put(metadata.testElement().get(), metadata.aggregatingElement());
      } else {
        if (metadata.isModule()) {
          globalModules.add(metadata.aggregatingElement());
        } else {
          for (TypeElement component : metadata.componentElements()) {
            globalEntryPointsByComponent.put(component, metadata.aggregatingElement());
          }
        }
      }
    }

    ImmutableSetMultimap.Builder<TypeElement, TypeElement> builder = ImmutableSetMultimap.builder();
    for (AggregatedRootMetadata aggregatedRootMetadata : aggregatedRootMetadatas) {
      TypeElement rootElement = aggregatedRootMetadata.rootElement();
      if (!rootsUsingSharedComponent.contains(rootElement)) {
        builder.putAll(rootElement, globalModules);
        builder.putAll(rootElement, globalEntryPointsByComponent.values());
        builder.putAll(rootElement, testDepsByRoot.get(rootElement));
      }
    }

    // Add the Default/EarlyEntryPoint root if necessary.
    TypeElement defaultRoot = getElementUtils().getTypeElement(ClassNames.DEFAULT_ROOT.toString());
    if (!rootsUsingSharedComponent.isEmpty()) {
      builder.putAll(defaultRoot, globalModules);
      builder.putAll(defaultRoot, globalEntryPointsByComponent.values());
      for (TypeElement rootElement : rootsUsingSharedComponent) {
        builder.putAll(defaultRoot, testDepsByRoot.get(rootElement));
      }
    } else if (hasEarlyEntryPoints) {
      builder.putAll(defaultRoot, globalModules);
      TypeElement singletonComponent =
          getElementUtils().getTypeElement(ClassNames.SINGLETON_COMPONENT.toString());
      for (TypeElement component : globalEntryPointsByComponent.keySet()) {
        if (!component.equals(singletonComponent)) {
          // Skip all singleton component entry points. These will be replaced by eager entry points
          builder.putAll(defaultRoot, globalEntryPointsByComponent.get(component));
        }
      }
    }

    return builder.build();
  }

  private ImmutableSet<TypeElement> rootsUsingSharedComponent(
      ImmutableSet<AggregatedRootMetadata> aggregatedRootMetadatas,
      ImmutableSet<AggregatedDepsMetadata> aggregatedDepsMetadatas,
      ImmutableSet<AggregatedUninstallModulesMetadata> uninstallModulesMetadatas) {
    if (!isSharedTestComponentsEnabled(getProcessingEnv())) {
      return ImmutableSet.of();
    }
    ImmutableSet<TypeElement> hasLocalModuleDependencies =
        ImmutableSet.<TypeElement>builder()
            .addAll(
                aggregatedDepsMetadatas.stream()
                    .filter(metadata -> metadata.isModule() && metadata.testElement().isPresent())
                    .map(metadata -> metadata.testElement().get())
                    .collect(toImmutableSet()))
            .addAll(
                uninstallModulesMetadatas.stream()
                    .map(AggregatedUninstallModulesMetadata::testElement)
                    .collect(toImmutableSet()))
            .build();

    return aggregatedRootMetadatas.stream()
        .map(AggregatedRootMetadata::rootElement)
        .map(rootElement -> Root.create(rootElement, getProcessingEnv()))
        .filter(Root::isTestRoot)
        .map(Root::element)
        .filter(rootElement -> !hasLocalModuleDependencies.contains(rootElement))
        .collect(toImmutableSet());
  }

  private static ImmutableSet<TypeElement> defineComponentDeps(Elements elements) {
    return DefineComponentClassesMetadata.from(elements).stream()
        .map(DefineComponentClassesMetadata::aggregatingElement)
        .collect(toImmutableSet());
  }

  private static ImmutableSet<TypeElement> aliasOfDeps(Elements elements) {
    return AliasOfPropagatedDataMetadata.from(elements).stream()
        .map(AliasOfPropagatedDataMetadata::aggregatingElement)
        .collect(toImmutableSet());
  }
}
