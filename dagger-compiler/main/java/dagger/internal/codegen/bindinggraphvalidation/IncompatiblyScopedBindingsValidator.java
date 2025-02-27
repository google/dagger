/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.base.Scopes.getReadableSource;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.base.Scopes;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.validation.DiagnosticMessageGenerator;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/**
 * Reports an error for any component that uses bindings with scopes that are not assigned to the
 * component.
 */
final class IncompatiblyScopedBindingsValidator extends ValidationBindingGraphPlugin {
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final CompilerOptions compilerOptions;
  private final DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory;

  @Inject
  IncompatiblyScopedBindingsValidator(
      MethodSignatureFormatter methodSignatureFormatter,
      CompilerOptions compilerOptions,
      DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory) {
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.compilerOptions = compilerOptions;
    this.diagnosticMessageGeneratorFactory = diagnosticMessageGeneratorFactory;
  }

  @Override
  public String pluginName() {
    return "Dagger/IncompatiblyScopedBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    DiagnosticMessageGenerator diagnosticMessageGenerator =
        diagnosticMessageGeneratorFactory.create(bindingGraph);
    bindingGraph.bindings().stream()
        .filter(binding -> hasIncompatibleScope(bindingGraph, binding))
        .collect(groupingBy(binding -> owningComponent(bindingGraph, binding)))
        .forEach((owningComponent, bindings) ->
            report(owningComponent, bindings, diagnosticReporter, diagnosticMessageGenerator));
  }

  private static boolean hasIncompatibleScope(BindingGraph bindingGraph, Binding binding) {
    if (binding.scope().isEmpty()
            || binding.scope().get().isReusable()
            // @Inject bindings in module or subcomponent binding graphs will appear at the
            // properly scoped ancestor component, so ignore them here.
            || (binding.kind() == INJECTION && isSubcomponentOrModuleRoot(bindingGraph))) {
      return false;
    }
    return !owningComponent(bindingGraph, binding).scopes().contains(binding.scope().get());
  }

  private static boolean isSubcomponentOrModuleRoot(BindingGraph bindingGraph) {
    ComponentNode rootComponent = bindingGraph.rootComponentNode();
     return rootComponent.isSubcomponent() || !rootComponent.isRealComponent();
  }

  private static ComponentNode owningComponent(BindingGraph bindingGraph, Binding binding) {
    return bindingGraph.componentNode(binding.componentPath()).get();
  }

  private void report(
      ComponentNode componentNode,
      List<Binding> bindings,
      DiagnosticReporter diagnosticReporter,
      DiagnosticMessageGenerator diagnosticMessageGenerator) {
    Diagnostic.Kind diagnosticKind = ERROR;
    StringBuilder message =
        new StringBuilder(
            componentNode.componentPath().currentComponent().xprocessing().getQualifiedName());

    if (!componentNode.isRealComponent()) {
      // If the "component" is really a module, it will have no scopes attached. We want to report
      // if there is more than one scope in that component.
      if (bindings.stream().map(Binding::scope).map(Optional::get).distinct().count() <= 1) {
        return;
      }
      message.append(" contains bindings with different scopes:");
      diagnosticKind = compilerOptions.moduleHasDifferentScopesDiagnosticKind();
    } else if (componentNode.scopes().isEmpty()) {
      message.append(" (unscoped) may not reference scoped bindings:");
    } else {
      message
          .append(" scoped with ")
          .append(
              componentNode.scopes().stream().map(Scopes::getReadableSource).collect(joining(" ")))
          .append(" may not reference bindings with different scopes:");
    }

    // TODO(ronshapiro): Should we group by scope?
    for (Binding binding : bindings) {
      message.append('\n').append(INDENT);

      // TODO(dpb): Use DeclarationFormatter.
      // But that doesn't print scopes for @Inject-constructed types.
      switch (binding.kind()) {
        case DELEGATE:
        case PROVISION:
          message.append(
              methodSignatureFormatter.format(
                  asExecutable(binding.bindingElement().get().xprocessing())));
          break;

        case INJECTION:
          message
              .append(getReadableSource(binding.scope().get()))
              .append(" class ")
              .append(
                  closestEnclosingTypeElement(binding.bindingElement().get().xprocessing())
                      .getQualifiedName())
              .append(diagnosticMessageGenerator.getMessage(binding));

          break;

        default:
          throw new AssertionError(binding);
      }

      message.append('\n');
    }
    diagnosticReporter.reportComponent(diagnosticKind, componentNode, message.toString());
  }
}
