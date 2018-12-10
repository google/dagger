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
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static dagger.internal.codegen.Scopes.getReadableSource;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.Scope;
import java.util.Map;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Validates the relationships between parent components and subcomponents. */
final class ComponentHierarchyValidator {
  private final CompilerOptions compilerOptions;

  @Inject
  ComponentHierarchyValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  ValidationReport<TypeElement> validate(ComponentDescriptor componentDescriptor) {
    ValidationReport.Builder<TypeElement> report =
        ValidationReport.about(componentDescriptor.typeElement());
    validateSubcomponentMethods(
        report,
        componentDescriptor,
        Maps.toMap(componentDescriptor.moduleTypes(), constant(componentDescriptor.typeElement())));

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
    componentDescriptor
        .childComponentsDeclaredByFactoryMethods()
        .forEach(
            (method, childComponent) -> {
              if (childComponent.hasCreator()) {
                report.addError(
                    "Components may not have factory methods for subcomponents that define a "
                        + "builder.",
                    method.methodElement());
              } else {
                validateFactoryMethodParameters(report, method, existingModuleToOwners);
              }

              validateSubcomponentMethods(
                  report,
                  childComponent,
                  new ImmutableMap.Builder<TypeElement, TypeElement>()
                      .putAll(existingModuleToOwners)
                      .putAll(
                          Maps.toMap(
                              Sets.difference(
                                  childComponent.moduleTypes(), existingModuleToOwners.keySet()),
                              constant(childComponent.typeElement())))
                      .build());
            });
  }

  private void validateFactoryMethodParameters(
      ValidationReport.Builder<?> report,
      ComponentMethodDescriptor subcomponentMethodDescriptor,
      ImmutableMap<TypeElement, TypeElement> existingModuleToOwners) {
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
  }

  /**
   * Checks that components do not have any scopes that are also applied on any of their ancestors.
   */
  private void validateScopeHierarchy(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor subject,
      SetMultimap<ComponentDescriptor, Scope> scopesByComponent) {
    scopesByComponent.putAll(subject, subject.scopes());

    for (ComponentDescriptor childComponent : subject.childComponents()) {
      validateScopeHierarchy(report, childComponent, scopesByComponent);
    }

    scopesByComponent.removeAll(subject);

    Predicate<Scope> subjectScopes =
        subject.kind().isProducer()
            // TODO(beder): validate that @ProductionScope is only applied on production components
            ? and(in(subject.scopes()), not(Scope::isProductionScope))
            : in(subject.scopes());
    SetMultimap<ComponentDescriptor, Scope> overlappingScopes =
        Multimaps.filterValues(scopesByComponent, subjectScopes);
    if (!overlappingScopes.isEmpty()) {
      StringBuilder error =
          new StringBuilder()
              .append(subject.typeElement().getQualifiedName())
              .append(" has conflicting scopes:");
      for (Map.Entry<ComponentDescriptor, Scope> entry : overlappingScopes.entries()) {
        Scope scope = entry.getValue();
        error
            .append("\n  ")
            .append(entry.getKey().typeElement().getQualifiedName())
            .append(" also has ")
            .append(getReadableSource(scope));
      }
      report.addItem(
          error.toString(),
          compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
          subject.typeElement());
    }
  }
}
