/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Multibindings;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Valdiates types annotated with {@link Multibindings @Multibindings} that haven't already been
 * validated while processing their enclosing modules.
 */
class MultibindingsProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final MultibindingsValidator multibindingsValidator;

  MultibindingsProcessingStep(Messager messager, MultibindingsValidator multibindingsValidator) {
    this.messager = messager;
    this.multibindingsValidator = multibindingsValidator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Multibindings.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (TypeElement element : typesIn(elementsByAnnotation.values())) {
      if (!multibindingsValidator.wasAlreadyValidated(element)) {
        multibindingsValidator.validate(element).printMessagesTo(messager);
      }
    }
    return ImmutableSet.of();
  }
}
