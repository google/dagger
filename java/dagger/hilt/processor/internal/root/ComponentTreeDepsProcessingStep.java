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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.hilt.processor.internal.HiltCompilerOptions.useAggregatingRootProcessor;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointMetadata;
import dagger.hilt.android.processor.internal.androidentrypoint.ApplicationGenerator;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsMetadata;
import dagger.hilt.processor.internal.aggregateddeps.ComponentDependencies;
import dagger.hilt.processor.internal.aliasof.AliasOfPropagatedDataMetadata;
import dagger.hilt.processor.internal.aliasof.AliasOfs;
import dagger.hilt.processor.internal.definecomponent.DefineComponentClassesMetadata;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import dagger.hilt.processor.internal.earlyentrypoint.AggregatedEarlyEntryPointMetadata;
import dagger.hilt.processor.internal.uninstallmodules.AggregatedUninstallModulesMetadata;
import dagger.internal.codegen.xprocessing.XElements;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Processor that outputs dagger components based on transitive build deps. */
public final class ComponentTreeDepsProcessingStep extends BaseProcessingStep {
  private final Set<ClassName> componentTreeDepNames = new HashSet<>();
  private final Set<ClassName> processed = new HashSet<>();

  public ComponentTreeDepsProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.COMPONENT_TREE_DEPS);
  }

  @Override
  protected void processEach(ClassName annotation, XElement element) {
    componentTreeDepNames.add(XElements.asTypeElement(element).getClassName());
  }

  @Override
  public void postProcess(XProcessingEnv env, XRoundEnv roundEnv) throws Exception {
    ImmutableSet<ComponentTreeDepsMetadata> componentTreeDepsToProcess =
        componentTreeDepNames.stream()
            .filter(className -> !processed.contains(className))
            .map(className -> processingEnv().requireTypeElement(className))
            .map(element -> ComponentTreeDepsMetadata.from(element, processingEnv()))
            .collect(toImmutableSet());

    DefineComponents defineComponents = DefineComponents.create();
    for (ComponentTreeDepsMetadata metadata : componentTreeDepsToProcess) {
      processComponentTreeDeps(metadata, defineComponents);
    }
  }

  private void processComponentTreeDeps(
      ComponentTreeDepsMetadata metadata, DefineComponents defineComponents) throws IOException {
    XTypeElement metadataElement = processingEnv().requireTypeElement(metadata.name());
    try {
      // We choose a name for the generated components/wrapper based off of the originating element
      // annotated with @ComponentTreeDeps. This is close to but isn't necessarily a "real" name of
      // a root, since with shared test components, even for single roots, the component tree deps
      // will be moved to a shared package with a deduped name.
      ClassName renamedRoot = Processors.removeNameSuffix(metadataElement, "_ComponentTreeDeps");
      ComponentNames componentNames = ComponentNames.withRenaming(rootName -> renamedRoot);

      boolean isDefaultRoot = ClassNames.DEFAULT_ROOT.equals(renamedRoot);
      ImmutableSet<Root> roots =
          AggregatedRootMetadata.from(metadata.aggregatedRootDeps(), processingEnv()).stream()
              .map(AggregatedRootMetadata::rootElement)
              .map(rootElement -> Root.create(rootElement, processingEnv()))
              .collect(toImmutableSet());

      // TODO(bcorso): For legacy reasons, a lot of the generating code requires a "root" as input
      // since we used to assume 1 root per component tree. Now that each ComponentTreeDeps may
      // represent multiple roots, we should refactor this logic.
      Root root =
          isDefaultRoot
              ? Root.createDefaultRoot(processingEnv())
              // Non-default roots should only ever be associated with one root element
              : getOnlyElement(roots);

      ImmutableSet<ComponentDescriptor> componentDescriptors =
          defineComponents.getComponentDescriptors(
              DefineComponentClassesMetadata.from(metadata.defineComponentDeps()));
      ComponentTree tree = ComponentTree.from(componentDescriptors);
      ComponentDependencies deps =
          ComponentDependencies.from(
              componentDescriptors,
              AggregatedDepsMetadata.from(metadata.aggregatedDeps()),
              AggregatedUninstallModulesMetadata.from(metadata.aggregatedUninstallModulesDeps()),
              AggregatedEarlyEntryPointMetadata.from(metadata.aggregatedEarlyEntryPointDeps()),
              processingEnv());
      AliasOfs aliasOfs =
          AliasOfs.create(
              AliasOfPropagatedDataMetadata.from(metadata.aliasOfDeps()), componentDescriptors);
      RootMetadata rootMetadata = RootMetadata.create(root, tree, deps, aliasOfs, processingEnv());

      generateComponents(metadata, rootMetadata, componentNames);

        // Generate a creator for the early entry point if there is a default component available
        // and there are early entry points.
        if (isDefaultRoot && !metadata.aggregatedEarlyEntryPointDeps().isEmpty()) {
          EarlySingletonComponentCreatorGenerator.generate(processingEnv());
        }

        if (root.isTestRoot()) {
          // Generate test related classes for each test root that uses this component.
          ImmutableList<RootMetadata> rootMetadatas =
              roots.stream()
                  .map(test -> RootMetadata.create(test, tree, deps, aliasOfs, processingEnv()))
                  .collect(toImmutableList());
          generateTestComponentData(metadataElement, rootMetadatas, componentNames);
        } else {
          generateApplication(root.element());
        }

      setProcessingState(metadata, root);
    } catch (Exception e) {
      processed.add(metadata.name());
      throw e;
    }
  }

  private void setProcessingState(ComponentTreeDepsMetadata metadata, Root root) {
    processed.add(metadata.name());
  }

  private void generateComponents(
      ComponentTreeDepsMetadata metadata, RootMetadata rootMetadata, ComponentNames componentNames)
      throws IOException {
    RootGenerator.generate(metadata, rootMetadata, componentNames, processingEnv());
  }

  private void generateTestComponentData(
      XTypeElement metadataElement,
      ImmutableList<RootMetadata> rootMetadatas,
      ComponentNames componentNames)
      throws IOException {
    for (RootMetadata rootMetadata : rootMetadatas) {
      // TODO(bcorso): Consider moving this check earlier into processEach.
      XTypeElement testElement = rootMetadata.testRootMetadata().testElement();
      ProcessorErrors.checkState(
          testElement.isPublic(),
          testElement,
          "Hilt tests must be public, but found: %s",
          XElements.toStableString(testElement));
      new TestComponentDataGenerator(processingEnv(), metadataElement, rootMetadata, componentNames)
          .generate();
    }
  }

  private void generateApplication(XTypeElement rootElement) throws IOException {
    // The generated application references the generated component so they must be generated
    // in the same build unit. Thus, we only generate the application here if we're using the
    // Hilt Gradle plugin's aggregating task. If we're using the aggregating processor, we need
    // to generate the application within AndroidEntryPointProcessor instead.
    if (!useAggregatingRootProcessor(processingEnv())) {
      AndroidEntryPointMetadata metadata = AndroidEntryPointMetadata.of(rootElement);
      new ApplicationGenerator(processingEnv(), metadata).generate();
    }
  }
}
