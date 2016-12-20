/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.internal.codegen;

import static dagger.internal.codegen.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.ErrorMessages.BINDS_INSTANCE_NOT_IN_BUILDER;
import static dagger.internal.codegen.Util.toImmutableSet;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.BindsInstance;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
final class BindsInstanceProcessingStep implements ProcessingStep {

  private static final ImmutableSet<Class<? extends Annotation>> VALID_CONTAINING_ANNOTATIONS =
      Stream.of(ComponentDescriptor.Kind.values())
          .map(ComponentDescriptor.Kind::builderAnnotationType)
          .collect(toImmutableSet());

  private final Messager messager;

  BindsInstanceProcessingStep(Messager messager) {
    this.messager = messager;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(BindsInstance.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(BindsInstance.class)) {
      if (!isAnyAnnotationPresent(element.getEnclosingElement(), VALID_CONTAINING_ANNOTATIONS)) {
        messager.printMessage(Kind.ERROR, BINDS_INSTANCE_NOT_IN_BUILDER, element);
      }
    }
    return ImmutableSet.of();
  }
}
