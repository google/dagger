/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElements;
import static dagger.internal.codegen.ComponentRequirement.Kind.MODULE;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;

/** Reports errors in the shape of the binding graph. */
final class BindingGraphValidator {

  private final DaggerTypes types;

  @Inject
  BindingGraphValidator(DaggerTypes types) {
    this.types = types;
  }

  ValidationReport<TypeElement> validate(BindingGraph graph) {
    ComponentValidation validation = new ComponentValidation(graph);
    validation.traverseComponents();
    return validation.buildReport();
  }

  private final class ComponentValidation extends ComponentTreeTraverser {
    final BindingGraph rootGraph;
    final Map<ComponentDescriptor, ValidationReport.Builder<TypeElement>> reports =
        new LinkedHashMap<>();

    ComponentValidation(BindingGraph rootGraph) {
      super(rootGraph);
      this.rootGraph = rootGraph;
    }

    /** Returns a report that contains all validation messages found during traversal. */
    ValidationReport<TypeElement> buildReport() {
      ValidationReport.Builder<TypeElement> report =
          ValidationReport.about(rootGraph.componentType());
      reports.values().forEach(subreport -> report.addSubreport(subreport.build()));
      return report.build();
    }

    /** Returns the report builder for a (sub)component. */
    private ValidationReport.Builder<TypeElement> report(ComponentDescriptor component) {
      return reentrantComputeIfAbsent(
          reports,
          component,
          descriptor -> ValidationReport.about(descriptor.componentDefinitionType()));
    }

    @Override
    protected void visitSubcomponentFactoryMethod(
        BindingGraph graph, BindingGraph parent, ExecutableElement factoryMethod) {
      Set<TypeElement> missingModules =
          graph
              .componentRequirements()
              .stream()
              .filter(componentRequirement -> componentRequirement.kind().equals(MODULE))
              .map(ComponentRequirement::typeElement)
              .filter(
                  moduleType ->
                      !subgraphFactoryMethodParameters(parent, factoryMethod).contains(moduleType))
              .filter(moduleType -> !componentCanMakeNewInstances(moduleType))
              .collect(toSet());
      if (!missingModules.isEmpty()) {
        report(parent.componentDescriptor())
            .addError(
                String.format(
                    "%s requires modules which have no visible default constructors. "
                        + "Add the following modules as parameters to this method: %s",
                    graph.componentType().getQualifiedName(),
                    missingModules.stream().map(Object::toString).collect(joining(", "))),
                factoryMethod);
      }
    }

    private ImmutableSet<TypeElement> subgraphFactoryMethodParameters(
        BindingGraph parent, ExecutableElement childFactoryMethod) {
      DeclaredType componentType = asDeclared(parent.componentType().asType());
      ExecutableType factoryMethodType =
          asExecutable(types.asMemberOf(componentType, childFactoryMethod));
      return asTypeElements(factoryMethodType.getParameterTypes());
    }
  }
}
