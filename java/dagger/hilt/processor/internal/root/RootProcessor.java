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
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import java.util.Set;
import java.util.stream.Collectors;
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
  private GeneratesRootInputs generatesRootInputs;
  private ClassName rootName;
  private boolean processed;
  private boolean preprocessed;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    generatesRootInputs = new GeneratesRootInputs(processingEnvironment);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.copyOf(RootType.values()).stream()
        .map(rootType -> rootType.className().toString())
        .collect(Collectors.toSet());
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    ProcessorErrors.checkState(
        rootName == null, element, "More than one root found: [%s, %s]", rootName, element);

    rootName = ClassName.get(MoreElements.asType(element));
  }

  @Override
  public void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
    if (processed) {
      Set<Element> newElements = generatesRootInputs.getElementsToWaitFor(roundEnv);
      checkState(
          newElements.isEmpty(),
          "Found extra modules after compilation: %s\n"
              + "(If you are adding an annotation processor that generates root input for hilt, "
              + "the annotation must be annotated with @dagger.hilt.GeneratesRootInput.\n)",
          newElements);
    } else if (rootName != null && generatesRootInputs.getElementsToWaitFor(roundEnv).isEmpty()) {
      // We create a new root element each round to avoid the jdk8 bug where TypeElement.equals does
      // not work for elements across processing rounds.
      TypeElement rootElement = getElementUtils().getTypeElement(rootName.toString());
      Root root = Root.create(rootElement, getProcessingEnv());

      processed = true;
      RootGenerator.generate(root, getProcessingEnv());
    }
  }
}
