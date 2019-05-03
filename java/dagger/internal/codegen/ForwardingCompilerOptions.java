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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** A {@link CompilerOptions} object that delegates to another one. */
class ForwardingCompilerOptions extends CompilerOptions {

  private final CompilerOptions delegate;

  ForwardingCompilerOptions(CompilerOptions delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  boolean usesProducers() {
    return delegate.usesProducers();
  }

  @Override
  boolean fastInit() {
    return delegate.fastInit();
  }

  @Override
  boolean formatGeneratedSource() {
    return delegate.formatGeneratedSource();
  }

  @Override
  boolean writeProducerNameInToken() {
    return delegate.writeProducerNameInToken();
  }

  @Override
  Diagnostic.Kind nullableValidationKind() {
    return delegate.nullableValidationKind();
  }

  @Override
  Diagnostic.Kind privateMemberValidationKind() {
    return delegate.privateMemberValidationKind();
  }

  @Override
  Diagnostic.Kind staticMemberValidationKind() {
    return delegate.staticMemberValidationKind();
  }

  @Override
  boolean ignorePrivateAndStaticInjectionForComponent() {
    return delegate.ignorePrivateAndStaticInjectionForComponent();
  }

  @Override
  ValidationType scopeCycleValidationType() {
    return delegate.scopeCycleValidationType();
  }

  @Override
  boolean warnIfInjectionFactoryNotGeneratedUpstream() {
    return delegate.warnIfInjectionFactoryNotGeneratedUpstream();
  }

  @Override
  boolean headerCompilation() {
    return delegate.headerCompilation();
  }

  @Override
  boolean aheadOfTimeSubcomponents() {
    return delegate.aheadOfTimeSubcomponents();
  }

  @Override
  boolean forceUseSerializedComponentImplementations() {
    return delegate.forceUseSerializedComponentImplementations();
  }

  @Override
  boolean emitModifiableMetadataAnnotations() {
    return delegate.emitModifiableMetadataAnnotations();
  }

  @Override
  boolean useGradleIncrementalProcessing() {
    return delegate.useGradleIncrementalProcessing();
  }

  @Override
  ValidationType fullBindingGraphValidationType(TypeElement element) {
    return delegate.fullBindingGraphValidationType(element);
  }

  @Override
  Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
    return delegate.moduleHasDifferentScopesDiagnosticKind();
  }

  @Override
  ValidationType explicitBindingConflictsWithInjectValidationType() {
    return delegate.explicitBindingConflictsWithInjectValidationType();
  }
}
