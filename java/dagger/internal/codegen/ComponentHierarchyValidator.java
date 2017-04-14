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

import static com.google.common.base.Functions.constant;
import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

/** Validates the relationships between parent components and subcomponents. */
final class ComponentHierarchyValidator {
  private final CompilerOptions compilerOptions;
  private final Elements elements;

  ComponentHierarchyValidator(CompilerOptions compilerOptions, Elements elements) {
    this.compilerOptions = compilerOptions;
    this.elements = elements;
  }

  ValidationReport<TypeElement> validate(ComponentDescriptor componentDescriptor) {
    ValidationReport.Builder<TypeElement> report =
        ValidationReport.about(componentDescriptor.componentDefinitionType());
    validateSubcomponentMethods(
        report,
        componentDescriptor,
        Maps.toMap(
            componentDescriptor.transitiveModuleTypes(),
            constant(componentDescriptor.componentDefinitionType())));

    if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
      validateScopeHierarchy(
          report, componentDescriptor, LinkedHashMultimap.<ComponentDescriptor, Scope>create());
    }
    return report.build();
  }

  private void validateSubcomponentMethods(
      ValidationReport.Builder<?> report,
      ComponentDescriptor componentDescriptor,
      ImmutableMap<TypeElement, TypeElement> existingModuleToOwners) {
    for (Map.Entry<ComponentMethodDescriptor, ComponentDescriptor> subcomponentEntry :
        componentDescriptor.subcomponentsByFactoryMethod().entrySet()) {
      ComponentMethodDescriptor subcomponentMethodDescriptor = subcomponentEntry.getKey();
      ComponentDescriptor subcomponentDescriptor = subcomponentEntry.getValue();
      // validate the way that we create subcomponents
      for (VariableElement factoryMethodParameter :
          subcomponentMethodDescriptor.methodElement().getParameters()) {
        TypeElement moduleType = MoreTypes.asTypeElement(factoryMethodParameter.asType());
        TypeElement originatingComponent = existingModuleToOwners.get(moduleType);
        if (originatingComponent != null) {
          /* Factory method tries to pass a module that is already present in the parent.
           * This is an error. */
          report.addError(
              String.format(
                  "%s is present in %s. A subcomponent cannot use an instance of a "
                      + "module that differs from its parent.",
                  moduleType.getSimpleName(), originatingComponent.getQualifiedName()),
              factoryMethodParameter);
        }
      }
      validateSubcomponentMethods(
          report,
          subcomponentDescriptor,
          new ImmutableMap.Builder<TypeElement, TypeElement>()
              .putAll(existingModuleToOwners)
              .putAll(
                  Maps.toMap(
                      Sets.difference(
                          subcomponentDescriptor.transitiveModuleTypes(),
                          existingModuleToOwners.keySet()),
                      constant(subcomponentDescriptor.componentDefinitionType())))
              .build());
    }
  }

  /**
   * Checks that components do not have any scopes that are also applied on any of their ancestors.
   */
  private void validateScopeHierarchy(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor subject,
      SetMultimap<ComponentDescriptor, Scope> scopesByComponent) {
    scopesByComponent.putAll(subject, subject.scopes());

    for (ComponentDescriptor child : subject.subcomponents()) {
      validateScopeHierarchy(report, child, scopesByComponent);
    }

    scopesByComponent.removeAll(subject);

    Predicate<Scope> subjectScopes =
        subject.kind().isProducer()
            // TODO(beder): validate that @ProductionScope is only applied on production components
            ? and(in(subject.scopes()), not(equalTo(Scope.productionScope(elements))))
            : in(subject.scopes());
    SetMultimap<ComponentDescriptor, Scope> overlappingScopes =
        Multimaps.filterValues(scopesByComponent, subjectScopes);
    if (!overlappingScopes.isEmpty()) {
      StringBuilder error =
          new StringBuilder()
              .append(subject.componentDefinitionType().getQualifiedName())
              .append(" has conflicting scopes:");
      for (Map.Entry<ComponentDescriptor, Scope> entry : overlappingScopes.entries()) {
        Scope scope = entry.getValue();
        error.append("\n  ")
            .append(entry.getKey().componentDefinitionType().getQualifiedName())
            .append(" also has ")
            .append(scope.getReadableSource());
      }
      report.addItem(
          error.toString(),
          compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
          subject.componentDefinitionType());
    }
  }
}
