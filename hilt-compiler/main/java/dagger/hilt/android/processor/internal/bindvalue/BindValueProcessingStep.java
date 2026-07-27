/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.bindvalue;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XRoundEnv;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import dagger.hilt.android.processor.internal.bindvalue.BindValueMetadata.BindValueElement;
import dagger.hilt.processor.internal.BaseProcessingStep;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Provides a test's @BindValue fields to the SINGLETON component. */
public final class BindValueProcessingStep extends BaseProcessingStep {
  private static final ImmutableSet<ClassName> SUPPORTED_ANNOTATIONS =
      ImmutableSet.<ClassName>builder()
          .addAll(BindValueMetadata.BIND_VALUE_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_VALUE_INTO_SET_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_ELEMENTS_INTO_SET_ANNOTATIONS)
          .addAll(BindValueMetadata.BIND_VALUE_INTO_MAP_ANNOTATIONS)
          .build();

  private final List<BindValueElement> bindValueElements = new ArrayList<>();
  private final Set<XElement> processedElements = new HashSet<>();

  public BindValueProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return SUPPORTED_ANNOTATIONS;
  }

  @Override
  protected void preProcess(XProcessingEnv env, XRoundEnv round) {
    bindValueElements.clear();
    processedElements.clear();
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    if (processedElements.add(element)) {
      bindValueElements.add(BindValueElement.create(element));
    }
  }

  @Override
  protected void postProcess(XProcessingEnv env, XRoundEnv round) throws Exception {
    // Generate a module for each testing class with a @BindValue field.
    ImmutableMap<XTypeElement, Collection<BindValueElement>> testRootMap =
        Multimaps.index(bindValueElements, BindValueElement::testElement).asMap();
    for (Map.Entry<XTypeElement, Collection<BindValueElement>> e : testRootMap.entrySet()) {
      BindValueMetadata metadata = BindValueMetadata.create(e.getKey(), e.getValue());
      new BindValueGenerator(processingEnv(), metadata).generate();
    }
  }

  static ImmutableList<ClassName> getBindValueAnnotations(XElement element) {
    return element.getAllAnnotations().stream()
        .map(XAnnotation::getClassName)
        .filter(SUPPORTED_ANNOTATIONS::contains)
        .collect(toImmutableList());
  }
}
