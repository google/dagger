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

package dagger.internal.codegen.compileroption;

import androidx.room.compiler.processing.XTypeElement;
import javax.tools.Diagnostic;

/** A collection of options that dictate how the compiler will run. */
public abstract class CompilerOptions {
  public abstract boolean usesProducers();

  /**
   * Returns true if the experimental Android mode is enabled.
   *
   * <p><b>Warning: Do Not use! This flag is for internal, experimental use only!</b>
   *
   * <p>Issues related to this flag will not be supported. This flag could break your build, cause
   * memory leaks in your app, or cause other unknown issues at runtime.
   */
  public abstract boolean experimentalMergedMode(XTypeElement element);

  /**
   * Returns true if the fast initialization flag, {@code fastInit}, is enabled.
   *
   * <p>If enabled, the generated code will attempt to optimize for fast component initialization.
   * This is done by reducing the number of factory classes loaded during initialization and the
   * number of eagerly initialized fields at the cost of potential memory leaks and higher
   * per-provision instantiation time.
   */
  public abstract boolean fastInit(XTypeElement element);

  public abstract boolean formatGeneratedSource();

  public abstract boolean writeProducerNameInToken();

  public abstract Diagnostic.Kind nullableValidationKind();

  public final boolean doCheckForNulls() {
    return nullableValidationKind().equals(Diagnostic.Kind.ERROR);
  }

  public abstract Diagnostic.Kind privateMemberValidationKind();

  public abstract Diagnostic.Kind staticMemberValidationKind();

  /**
   * Returns {@code true} if the stacktrace should be included in the deferred error message.
   *
   * <p>The default for this option is {@code false}. The stacktrace is mostly useful for special
   * debugging purposes to gather more information about where the exception was thrown from within
   * Dagger's own processors.
   */
  public abstract boolean includeStacktraceWithDeferredErrorMessages();

  /**
   * If {@code true}, Dagger will generate factories and components even if some members-injected
   * types have {@code private} or {@code static} {@code @Inject}-annotated members.
   *
   * <p>This should only ever be enabled by the TCK tests. Disabling this validation could lead to
   * generating code that does not compile.
   */
  public abstract boolean ignorePrivateAndStaticInjectionForComponent();

  public abstract ValidationType scopeCycleValidationType();

  /**
   * If {@code true}, Dagger will validate all transitive component dependencies of a component.
   * Otherwise, Dagger will only validate the direct component dependencies.
   *
   * <p>Note: this is different from scopeCycleValidationType, which lets you silence errors of
   * transitive component dependencies, but still requires the full transitive dependencies in the
   * classpath.
   *
   * <p>The main motivation for this flag is to prevent requiring the transitive component
   * dependencies in the classpath to speed up builds. See
   * https://github.com/google/dagger/issues/970.
   */
  public abstract boolean validateTransitiveComponentDependencies();

  public abstract boolean warnIfInjectionFactoryNotGeneratedUpstream();

  public abstract boolean headerCompilation();

  public abstract ValidationType fullBindingGraphValidationType();

  /**
   * If {@code true}, each plugin will visit the full binding graph for the given element.
   *
   * @throws IllegalArgumentException if {@code element} is not a module or (sub)component
   */
  public abstract boolean pluginsVisitFullBindingGraphs(XTypeElement element);

  public abstract Diagnostic.Kind moduleHasDifferentScopesDiagnosticKind();

  public abstract ValidationType explicitBindingConflictsWithInjectValidationType();

  public abstract boolean experimentalDaggerErrorMessages();

  /**
   * Returns {@code true} if strict superficial validation is enabled.
   *
   * <p>This option is enabled by default and allows Dagger to detect and fail if an element that
   * supports being annotated with a scope or qualifier annotation is annotated with any
   * unresolvable annotation types. This option is considered "strict" because in most cases we must
   * fail for any unresolvable annotation types, not just scopes and qualifiers. In particular, if
   * an annotation type is not resolvable, we don't have enough information to tell if it's a scope
   * or qualifier, so we must fail for all unresolvable annotations.
   *
   * <p>This option can be disabled to allow easier migration from the legacy behavior of Dagger
   * (i.e. versions less than or equal to 2.40.5). However, we will remove this option in a future
   * version of Dagger.
   *
   * <p>Warning:Disabling this option means that Dagger may miss a scope or qualifier on a binding,
   * leading to a (wrong) unscoped binding or a (wrong) unqualified binding, respectively.
   */
  public abstract boolean strictSuperficialValidation();

  /**
   * Returns {@code true} if the Dagger generated class should extend the {@code @Component}
   * annotated interface/class.
   *
   * <p>The default value is {@code false}. This flag was introduced in Dagger 2.42 to allow users
   * to switch back to the previous behavior ({@code true}) so that they can migrate incrementally.
   * This flag will be removed in a future release.
   */
  public abstract boolean generatedClassExtendsComponent();

  /** Returns the number of bindings allowed per shard. */
  public int keysPerComponentShard(XTypeElement component) {
    return 3500;
  }

  /**
   * This option enables a fix to an issue where Dagger previously would erroneously allow
   * multibinding contributions in a component to have dependencies on child components. This will
   * eventually become the default and enforced.
   */
  public abstract boolean strictMultibindingValidation();
}
