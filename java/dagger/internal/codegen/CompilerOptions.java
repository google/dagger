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

import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** A collection of options that dictate how the compiler will run. */
abstract class CompilerOptions {
  abstract boolean usesProducers();

  /**
   * Returns true if the fast initialization flag, {@code fastInit}, is enabled.
   *
   * <p>If enabled, the generated code will attempt to optimize for fast component initialization.
   * This is done by reducing the number of factory classes loaded during initialization and the
   * number of eagerly initialized fields at the cost of potential memory leaks and higher
   * per-provision instantiation time.
   */
  abstract boolean fastInit();

  abstract boolean formatGeneratedSource();

  abstract boolean writeProducerNameInToken();

  abstract Diagnostic.Kind nullableValidationKind();

  final boolean doCheckForNulls() {
    return nullableValidationKind().equals(Diagnostic.Kind.ERROR);
  }

  abstract Diagnostic.Kind privateMemberValidationKind();

  abstract Diagnostic.Kind staticMemberValidationKind();

  /**
   * If {@code true}, Dagger will generate factories and components even if some members-injected
   * types have {@code private} or {@code static} {@code @Inject}-annotated members.
   *
   * <p>This should only ever be enabled by the TCK tests. Disabling this validation could lead to
   * generating code that does not compile.
   */
  abstract boolean ignorePrivateAndStaticInjectionForComponent();

  abstract ValidationType scopeCycleValidationType();

  abstract boolean warnIfInjectionFactoryNotGeneratedUpstream();

  abstract boolean headerCompilation();

  abstract boolean useGradleIncrementalProcessing();

  /**
   * Returns the validation that should be done for the full binding graph for the element.
   *
   * @throws IllegalArgumentException if {@code element} is not a module or (sub)component
   */
  abstract ValidationType fullBindingGraphValidationType(TypeElement element);

  abstract Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind();

  abstract ValidationType explicitBindingConflictsWithInjectValidationType();
}
