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

import static dagger.internal.codegen.DaggerElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Set;
import javax.inject.Inject;

/**
 * Reports an error for any component that uses bindings with scopes that are not assigned to the
 * component.
 */
final class IncompatiblyScopedBindingsValidation implements BindingGraphPlugin {

  private final MethodSignatureFormatter methodSignatureFormatter;

  @Inject
  IncompatiblyScopedBindingsValidation(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override
  public String pluginName() {
    return "Dagger/IncompatiblyScopedBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap.Builder<ComponentNode, BindingNode> incompatibleBindingNodes =
        ImmutableSetMultimap.builder();
    for (BindingNode bindingNode : bindingGraph.bindingNodes()) {
      bindingNode
          .binding()
          .scope()
          .ifPresent(
              scope -> {
                if (scope.isReusable()) {
                  return;
                }
                ComponentNode componentNode =
                    bindingGraph.componentNode(bindingNode.componentPath()).get();
                if (!componentNode.scopes().contains(scope)) {
                  incompatibleBindingNodes.put(componentNode, bindingNode);
                }
              });
    }
    Multimaps.asMap(incompatibleBindingNodes.build())
        .forEach(
            (componentNode, bindingNodes) ->
                diagnosticReporter.reportComponent(
                    ERROR,
                    componentNode,
                    incompatibleBindingScopesError(componentNode, bindingNodes)));
  }

  private String incompatibleBindingScopesError(
      ComponentNode componentNode, Set<BindingNode> bindingNodes) {
    StringBuilder message =
        new StringBuilder(componentNode.componentPath().currentComponent().getQualifiedName());
    if (!componentNode.scopes().isEmpty()) {
      message
          .append(" scoped with ")
          .append(
              componentNode.scopes().stream().map(Scopes::getReadableSource).collect(joining(" ")))
          .append(" may not reference bindings with different scopes:\n");
    } else {
      message.append(" (unscoped) may not reference scoped bindings:\n");
    }
    // TODO(ronshapiro): Should we group by scope?
    for (BindingNode bindingNode : bindingNodes) {
      message.append(INDENT);

      Binding binding = bindingNode.binding();
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
          throw new AssertionError(bindingNode);
      }

      message.append("\n");
    }
    return message.toString();
  }
}
