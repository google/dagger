/*
 * Copyright (C) 2014 The Dagger Authors.
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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 */
final class InjectProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final InjectBindingRegistry injectBindingRegistry;

  @Inject
  InjectProcessingStep(InjectBindingRegistry injectBindingRegistry) {
    this.injectBindingRegistry = injectBindingRegistry;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(Inject.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();
    // TODO(gak): add some error handling for bad source files

    for (Element injectElement : elementsByAnnotation.get(Inject.class)) {
      try {
        injectElement.accept(
            new ElementKindVisitor6<Void, Void>() {
              @Override
              public Void visitExecutableAsConstructor(
                  ExecutableElement constructorElement, Void v) {
                injectBindingRegistry.tryRegisterConstructor(constructorElement);
                return null;
              }

              @Override
              public Void visitVariableAsField(VariableElement fieldElement, Void p) {
                injectBindingRegistry.tryRegisterMembersInjectedType(
                    MoreElements.asType(fieldElement.getEnclosingElement()));
                return null;
              }

              @Override
              public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
                injectBindingRegistry.tryRegisterMembersInjectedType(
                    MoreElements.asType(methodElement.getEnclosingElement()));
                return null;
              }
            },
            null);
      } catch (TypeNotPresentException e) {
        rejectedElements.add(injectElement);
      }
    }

    return rejectedElements.build();
  }
}
