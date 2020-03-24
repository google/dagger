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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toSet;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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
  private final List<ClassName> testRootNames = new ArrayList<>();
  private final Set<ClassName> processed = new HashSet<>();
  private GeneratesRootInputs generatesRootInputs;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    generatesRootInputs = new GeneratesRootInputs(processingEnvironment);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Arrays.stream(RootType.values())
        .map(rootType -> rootType.className().toString())
        .collect(toSet());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    TypeElement rootElement = MoreElements.asType(element);
    if (RootType.of(getProcessingEnv(), rootElement).isTestRoot()) {
      testRootNames.add(ClassName.get(rootElement));
    } else {
      rootNames.add(ClassName.get(rootElement));
    }

    ProcessorErrors.checkState(
        rootNames.size() <= 1, element, "More than one root found: %s", rootNames);

    ProcessorErrors.checkState(
        testRootNames.isEmpty() || rootNames.isEmpty(),
        element,
        "Cannot have both test roots and non-test roots in the same build compilation:"
            + "\n\tRoots: %s"
            + "\n\tTestRoots: %s",
        rootNames,
        testRootNames);
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

    if (newElements.isEmpty()) {
      ImmutableList<Root> rootsToProcess =
          Stream.concat(rootNames.stream(), testRootNames.stream())
              .filter(rootName -> !processed.contains(rootName))
              // We create a new root element each round to avoid the jdk8 bug where
              // TypeElement.equals does not work for elements across processing rounds.
              .map(rootName -> getElementUtils().getTypeElement(rootName.toString()))
              .map(rootElement -> Root.create(rootElement, getProcessingEnv()))
              .collect(toImmutableList());

      for (Root root : rootsToProcess) {
        processRoot(root);
      }
    }
  }

  private void processRoot(Root root) throws IOException {
    processed.add(root.classname());
    RootGenerator.generate(root, getProcessingEnv());
  }
}
