/*
 * Copyright (C) 2014 Google, Inc.
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
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final InjectConstructorValidator constructorValidator;
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private final InjectBindingRegistry injectBindingRegistry;

  InjectProcessingStep(Messager messager,
      InjectConstructorValidator constructorValidator,
      InjectFieldValidator fieldValidator,
      InjectMethodValidator methodValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      MembersInjectionBinding.Factory membersInjectionBindingFactory,
      InjectBindingRegistry factoryRegistrar) {
    this.messager = messager;
    this.constructorValidator = constructorValidator;
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
    this.provisionBindingFactory = provisionBindingFactory;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
    this.injectBindingRegistry = factoryRegistrar;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(Inject.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    // TODO(gak): add some error handling for bad source files
    final ImmutableSet.Builder<ProvisionBinding> provisions = ImmutableSet.builder();
    // TODO(gak): instead, we should collect reports by type and check later
    final ImmutableSet.Builder<DeclaredType> membersInjectedTypes = ImmutableSet.builder();

    for (Element injectElement : elementsByAnnotation.get(Inject.class)) {
      injectElement.accept(
          new ElementKindVisitor6<Void, Void>() {
            @Override
            public Void visitExecutableAsConstructor(
                ExecutableElement constructorElement, Void v) {
              ValidationReport<ExecutableElement> report =
                  constructorValidator.validate(constructorElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                provisions.add(provisionBindingFactory.forInjectConstructor(constructorElement,
                    Optional.<TypeMirror>absent()));
              }

              return null;
            }

            @Override
            public Void visitVariableAsField(VariableElement fieldElement, Void p) {
              ValidationReport<VariableElement> report = fieldValidator.validate(fieldElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjectedTypes.add(
                    MoreTypes.asDeclared(fieldElement.getEnclosingElement().asType()));
              }

              return null;
            }

            @Override
            public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
              ValidationReport<ExecutableElement> report =
                  methodValidator.validate(methodElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjectedTypes.add(
                    MoreTypes.asDeclared(methodElement.getEnclosingElement().asType()));
              }

              return null;
            }
          }, null);
    }

    for (DeclaredType injectedType : membersInjectedTypes.build()) {
      injectBindingRegistry.registerBinding(membersInjectionBindingFactory.forInjectedType(
          injectedType, Optional.<TypeMirror>absent()));
    }

    for (ProvisionBinding binding : provisions.build()) {
      injectBindingRegistry.registerBinding(binding);
    }
    return ImmutableSet.of();
  }
}
