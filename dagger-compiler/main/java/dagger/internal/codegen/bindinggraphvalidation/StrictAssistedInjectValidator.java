/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_FACTORY;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_INJECTION;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XTypeElement;
import dagger.internal.codegen.binding.AssistedElementsRegistry;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/**
 * Validates that {@code @AssistedInject} and {@code @AssistedFactory} bindings have their generated
 * files if they are from a dependency.
 */
final class StrictAssistedInjectValidator extends ValidationBindingGraphPlugin {
  private final CompilerOptions compilerOptions;
  private final AssistedElementsRegistry processedElementsRegistry;
  private final XProcessingEnv processingEnv;

  @Inject
  StrictAssistedInjectValidator(
      CompilerOptions compilerOptions,
      AssistedElementsRegistry processedElementsRegistry,
      XProcessingEnv processingEnv) {
    this.compilerOptions = compilerOptions;
    this.processedElementsRegistry = processedElementsRegistry;
    this.processingEnv = processingEnv;
  }

  @Override
  public String pluginName() {
    return "Dagger/StrictAssistedInject";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (!compilerOptions.strictAssistedInjectValidation()) {
      return;
    }

    for (Binding binding : bindingGraph.bindings()) {
      if (binding.kind().equals(ASSISTED_INJECTION)) {
        validateAssistedInject(binding, diagnosticReporter);
      } else if (binding.kind().equals(ASSISTED_FACTORY)) {
        validateAssistedFactory(binding, diagnosticReporter);
      }
    }
  }

  private void validateAssistedInject(Binding binding, DiagnosticReporter diagnosticReporter) {
    XTypeElement assistedClass =
        checkNotNull(
            binding.key().type().xprocessing().getTypeElement(),
            "Expected a type element for assisted-injected binding: %s",
            binding);

    // If it was processed in this compilation, we know it's fine (it will be generated).
    if (processedElementsRegistry.isAssistedInjectClassProcessed(assistedClass)) {
      return;
    }

    // Otherwise, it's from a dependency. Check if the factory exists.
    XClassName factoryClassName = generatedClassNameForBinding(((BindingNode) binding).delegate());
    if (processingEnv.findTypeElement(factoryClassName) == null) {
      diagnosticReporter.reportBinding(
          Diagnostic.Kind.ERROR,
          binding,
          String.format(
              "The @AssistedInject-annotated class %s is in a dependency, but its factory %s "
                  + "was not found. Make sure the Dagger annotation processor is applied to the "
                  + "library.",
              assistedClass.getQualifiedName(), factoryClassName.getCanonicalName()));
    }
  }

  private void validateAssistedFactory(Binding binding, DiagnosticReporter diagnosticReporter) {
    XTypeElement factoryInterface =
        checkNotNull(
            binding.key().type().xprocessing().getTypeElement(),
            "Expected a type element for assisted-factory binding: %s",
            binding);

    // If it was processed in this compilation, we know it's fine.
    if (processedElementsRegistry.isAssistedFactoryProcessed(factoryInterface)) {
      return;
    }

    // Otherwise, it's from a dependency. Check if the impl exists.
    XClassName implClassName = generatedClassNameForBinding(((BindingNode) binding).delegate());
    if (processingEnv.findTypeElement(implClassName) == null) {
      diagnosticReporter.reportBinding(
          Diagnostic.Kind.ERROR,
          binding,
          String.format(
              "The @AssistedFactory-annotated interface %s is in a dependency, but its "
                  + "implementation %s was not found. Make sure the Dagger annotation "
                  + "processor is applied to the library.",
              factoryInterface.getQualifiedName(), implClassName.getCanonicalName()));
    }
  }
}
