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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentTree;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.aggregateddeps.ComponentDependencies;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dagger components based on transitive build deps. */
@IncrementalAnnotationProcessor(AGGREGATING)
@AutoService(Processor.class)
public final class RootProcessor extends BaseProcessor {
  private final List<ClassName> rootNames = new ArrayList<>();
  private final Set<ClassName> processed = new HashSet<>();
  private boolean isTestEnv;
  private GeneratesRootInputs generatesRootInputs;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    generatesRootInputs = new GeneratesRootInputs(processingEnvironment);
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
    boolean isTestRoot = RootType.of(getProcessingEnv(), rootElement).isTestRoot();
    checkState(
        rootNames.isEmpty() || isTestEnv == isTestRoot,
        "Cannot mix test roots with non-test roots:"
            + "\n\tNon-Test Roots: %s"
            + "\n\tTest Roots: %s",
        isTestRoot ? rootNames : rootElement,
        isTestRoot ? rootElement : rootNames);
    isTestEnv = isTestRoot;

    rootNames.add(ClassName.get(rootElement));
    if (isTestEnv) {
      new TestInjectorGenerator(
          getProcessingEnv(),
          TestRootMetadata.of(getProcessingEnv(), rootElement)).generate();
    } else {
      ProcessorErrors.checkState(
          rootNames.size() <= 1, element, "More than one root found: %s", rootNames);
    }
  }

  @Override
  public void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
    Set<Element> newElements = generatesRootInputs.getElementsToWaitFor(roundEnv);
    if (!processed.isEmpty()) {
      checkState(
          newElements.isEmpty(),
          "Found extra modules after compilation: %s\n"
              + "(If you are adding an annotation processor that generates root input for hilt, "
              + "the annotation must be annotated with @dagger.hilt.GeneratesRootInput.\n)",
          newElements);
    }

    if (!newElements.isEmpty()) {
      // Skip further processing since there's new elements that generate root inputs in this round.
      return;
    }

    ImmutableList<Root> rootsToProcess =
        rootNames.stream()
            .filter(rootName -> !processed.contains(rootName))
            // We create a new root element each round to avoid the jdk8 bug where
            // TypeElement.equals does not work for elements across processing rounds.
            .map(rootName -> getElementUtils().getTypeElement(rootName.toString()))
            .map(rootElement -> Root.create(rootElement, getProcessingEnv()))
            .collect(toImmutableList());

    if (rootsToProcess.isEmpty()) {
      // Skip further processing since there's no roots that need processing.
      return;
    }

    // TODO(user): Currently, if there's an exception in any of the roots we stop processing
    // all roots. We should consider if it's worth trying to continue processing for other
    // roots. At the moment, I think it's rare that if one root failed the others would not.
    try {
      ImmutableList<ComponentDescriptor> descriptors =
          DefineComponents.componentDescriptors(getElementUtils());
      ComponentTree tree = ComponentTree.from(descriptors);
      ComponentDependencies deps = ComponentDependencies.from(descriptors, getElementUtils());
      ImmutableList<RootMetadata> rootMetadatas =
          rootsToProcess.stream()
              .map(root -> RootMetadata.create(root, tree, deps, getProcessingEnv()))
              .collect(toImmutableList());

      for (RootMetadata rootMetadata : rootMetadatas) {
        setProcessingState(rootMetadata.root());
        generateComponents(rootMetadata);
      }

      if (isTestEnv) {
        generateTestComponentData(rootMetadatas);
      }
    } catch (Exception e) {
      for (Root root : rootsToProcess) {
        processed.add(root.classname());
      }
      throw e;
    }
  }

  private void setProcessingState(Root root) {
    processed.add(root.classname());
  }

  private void generateComponents(RootMetadata rootMetadata) throws IOException {
    RootGenerator.generate(rootMetadata, getProcessingEnv());
  }

  private void generateTestComponentData(ImmutableList<RootMetadata> rootMetadatas)
      throws IOException {
    for (RootMetadata rootMetadata : rootMetadatas) {
      new TestComponentDataGenerator(getProcessingEnv(), rootMetadata).generate();
    }
    new TestComponentDataSupplierGenerator(getProcessingEnv(), rootMetadatas).generate();
  }
}
