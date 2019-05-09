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

package dagger.internal.codegen;

import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;
import static dagger.model.BindingKind.INJECTION;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/**
 * Reports an error for any component that uses bindings with scopes that are not assigned to the
 * component.
 */
final class IncompatiblyScopedBindingsValidator implements BindingGraphPlugin {

  private final MethodSignatureFormatter methodSignatureFormatter;
  private final CompilerOptions compilerOptions;

  @Inject
  IncompatiblyScopedBindingsValidator(
      MethodSignatureFormatter methodSignatureFormatter, CompilerOptions compilerOptions) {
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.compilerOptions = compilerOptions;
  }

  @Override
  public String pluginName() {
    return "Dagger/IncompatiblyScopedBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap.Builder<ComponentNode, dagger.model.Binding> incompatibleBindings =
        ImmutableSetMultimap.builder();
    for (dagger.model.Binding binding : bindingGraph.bindings()) {
      binding
          .scope()
          .filter(scope -> !scope.isReusable())
          .ifPresent(
              scope -> {
                ComponentNode componentNode =
                    bindingGraph.componentNode(binding.componentPath()).get();
                if (!componentNode.scopes().contains(scope)) {
                  // @Inject bindings in module or subcomponent binding graphs will appear at the
                  // properly scoped ancestor component, so ignore them here.
                  if (binding.kind().equals(INJECTION)
                      && (bindingGraph.rootComponentNode().isSubcomponent()
                          || !bindingGraph.rootComponentNode().isRealComponent())) {
                    return;
                  }
                  incompatibleBindings.put(componentNode, binding);
                }
              });
    }
    Multimaps.asMap(incompatibleBindings.build())
        .forEach((componentNode, bindings) -> report(componentNode, bindings, diagnosticReporter));
  }

  private void report(
      ComponentNode componentNode,
      Set<Binding> bindings,
      DiagnosticReporter diagnosticReporter) {
    Diagnostic.Kind diagnosticKind = ERROR;
    StringBuilder message =
        new StringBuilder(componentNode.componentPath().currentComponent().getQualifiedName());

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

      // TODO(dpb): Use BindingDeclarationFormatter.
      // But that doesn't print scopes for @Inject-constructed types.
      switch (binding.kind()) {
        case DELEGATE:
        case PROVISION:
          message.append(
              methodSignatureFormatter.format(
                  MoreElements.asExecutable(binding.bindingElement().get())));
          break;

        case INJECTION:
          message
              .append(getReadableSource(binding.scope().get()))
              .append(" class ")
              .append(
                  closestEnclosingTypeElement(binding.bindingElement().get()).getQualifiedName());
          break;

        default:
          throw new AssertionError(binding);
      }
    }
    diagnosticReporter.reportComponent(diagnosticKind, componentNode, message.toString());
  }
}
