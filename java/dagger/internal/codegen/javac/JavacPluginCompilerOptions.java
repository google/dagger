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

package dagger.internal.codegen.javac;

import static dagger.internal.codegen.compileroption.ValidationType.NONE;
import static javax.tools.Diagnostic.Kind.NOTE;

import androidx.room.compiler.processing.XTypeElement;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/** {@link CompilerOptions} for Javac plugins (e.g. for Dagger statistics or Kythe). */
final class JavacPluginCompilerOptions extends CompilerOptions {

  @Inject
  JavacPluginCompilerOptions() {}

  @Override
  public boolean usesProducers() {
    return true;
  }

  @Override
  public boolean experimentalMergedMode(XTypeElement element) {
    return false;
  }

  @Override
  public boolean fastInit(XTypeElement element) {
    return false;
  }

  @Override
  public boolean formatGeneratedSource() {
    return false;
  }

  @Override
  public boolean writeProducerNameInToken() {
    return true;
  }

  @Override
  public Diagnostic.Kind nullableValidationKind() {
    return NOTE;
  }

  @Override
  public Diagnostic.Kind privateMemberValidationKind() {
    return NOTE;
  }

  @Override
  public Diagnostic.Kind staticMemberValidationKind() {
    return NOTE;
  }

  @Override
  public boolean includeStacktraceWithDeferredErrorMessages() {
    return false;
  }

  @Override
  public boolean ignorePrivateAndStaticInjectionForComponent() {
    return false;
  }

  @Override
  public ValidationType scopeCycleValidationType() {
    return NONE;
  }

  @Override
  public boolean validateTransitiveComponentDependencies() {
    return true;
  }

  @Override
  public boolean warnIfInjectionFactoryNotGeneratedUpstream() {
    return false;
  }

  @Override
  public boolean headerCompilation() {
    return false;
  }

  @Override
  public ValidationType fullBindingGraphValidationType() {
    return NONE;
  }

  @Override
  public boolean pluginsVisitFullBindingGraphs(XTypeElement element) {
    return false;
  }

  @Override
  public Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind() {
    return NOTE;
  }

  @Override
  public ValidationType explicitBindingConflictsWithInjectValidationType() {
    return NONE;
  }

  @Override
  public boolean experimentalDaggerErrorMessages() {
    return false;
  }

  @Override
  public boolean strictMultibindingValidation() {
    return false;
  }

  @Override
  public boolean strictSuperficialValidation() {
    return true;
  }

  @Override
  public boolean generatedClassExtendsComponent() {
    return false;
  }
}
