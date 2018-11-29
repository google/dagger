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

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 */
// TODO(gak): add some error handling for bad source files
final class InjectProcessingStep extends TypeCheckingProcessingStep<Element> {
  private final ElementVisitor<Void, Void> visitor;

  @Inject
  InjectProcessingStep(InjectBindingRegistry injectBindingRegistry) {
    super(e -> e);
    this.visitor =
        new ElementKindVisitor8<Void, Void>() {
          @Override
          public Void visitExecutableAsConstructor(
              ExecutableElement constructorElement, Void aVoid) {
            injectBindingRegistry.tryRegisterConstructor(constructorElement);
            return null;
          }

          @Override
          public Void visitVariableAsField(VariableElement fieldElement, Void aVoid) {
            injectBindingRegistry.tryRegisterMembersInjectedType(
                MoreElements.asType(fieldElement.getEnclosingElement()));
            return null;
          }

          @Override
          public Void visitExecutableAsMethod(ExecutableElement methodElement, Void aVoid) {
            injectBindingRegistry.tryRegisterMembersInjectedType(
                MoreElements.asType(methodElement.getEnclosingElement()));
            return null;
          }
        };
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Inject.class);
  }

  @Override
  protected void process(
      Element injectElement, ImmutableSet<Class<? extends Annotation>> annotations) {
    injectElement.accept(visitor, null);
  }
}
