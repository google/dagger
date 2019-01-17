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
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static dagger.internal.codegen.Scopes.uniqueScopeOf;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.Scope;
import java.util.Collection;
import java.util.Formatter;
import java.util.Map;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Validates the relationships between parent components and subcomponents. */
final class ComponentHierarchyValidator {
  private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");
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
    validateRepeatedScopedDeclarations(report, componentDescriptor, LinkedHashMultimap.create());

    if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
      validateScopeHierarchy(
          report, componentDescriptor, LinkedHashMultimap.<ComponentDescriptor, Scope>create());
    }
    validateProductionModuleUniqueness(report, componentDescriptor, LinkedHashMultimap.create());
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
        subject.isProduction()
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

  private void validateProductionModuleUniqueness(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor componentDescriptor,
      SetMultimap<ComponentDescriptor, ModuleDescriptor> producerModulesByComponent) {
    ImmutableSet<ModuleDescriptor> producerModules =
        componentDescriptor.modules().stream()
            .filter(module -> module.kind().equals(ModuleKind.PRODUCER_MODULE))
            .collect(toImmutableSet());

    producerModulesByComponent.putAll(componentDescriptor, producerModules);
    for (ComponentDescriptor childComponent : componentDescriptor.childComponents()) {
      validateProductionModuleUniqueness(report, childComponent, producerModulesByComponent);
    }
    producerModulesByComponent.removeAll(componentDescriptor);

    SetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules =
        Multimaps.filterValues(producerModulesByComponent, producerModules::contains);
    if (repeatedModules.isEmpty()) {
      return;
    }

    StringBuilder error = new StringBuilder();
    Formatter formatter = new Formatter(error);

    formatter.format("%s repeats @ProducerModules:", componentDescriptor.typeElement());

    for (Map.Entry<ComponentDescriptor, Collection<ModuleDescriptor>> entry :
        repeatedModules.asMap().entrySet()) {
      formatter.format("\n  %s also installs: ", entry.getKey().typeElement());
      COMMA_SEPARATED_JOINER
          .appendTo(error, Iterables.transform(entry.getValue(), m -> m.moduleElement()));
    }

    report.addError(error.toString());
  }

  private void validateRepeatedScopedDeclarations(
      ValidationReport.Builder<TypeElement> report,
      ComponentDescriptor component,
      // TODO(ronshapiro): optimize ModuleDescriptor.hashCode()/equals. Otherwise this could be
      // quite costly
      SetMultimap<ComponentDescriptor, ModuleDescriptor> modulesWithScopes) {
    ImmutableSet<ModuleDescriptor> modules =
        component.modules().stream().filter(this::hasScopedDeclarations).collect(toImmutableSet());
    modulesWithScopes.putAll(component, modules);
    for (ComponentDescriptor childComponent : component.childComponents()) {
      validateRepeatedScopedDeclarations(report, childComponent, modulesWithScopes);
    }
    modulesWithScopes.removeAll(component);

    SetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules =
        Multimaps.filterValues(modulesWithScopes, modules::contains);
    if (repeatedModules.isEmpty()) {
      return;
    }

    report.addError(
        repeatedModulesWithScopeError(component, ImmutableSetMultimap.copyOf(repeatedModules)));
  }

  private boolean hasScopedDeclarations(ModuleDescriptor module) {
    return !moduleScopes(module).isEmpty();
  }

  private String repeatedModulesWithScopeError(
      ComponentDescriptor component,
      ImmutableSetMultimap<ComponentDescriptor, ModuleDescriptor> repeatedModules) {
    StringBuilder error =
        new StringBuilder()
            .append(component.typeElement().getQualifiedName())
            .append(" repeats modules with scoped bindings or declarations:");

    repeatedModules
        .asMap()
        .forEach(
            (conflictingComponent, conflictingModules) -> {
              error
                  .append("\n  - ")
                  .append(conflictingComponent.typeElement().getQualifiedName())
                  .append(" also includes:");
              for (ModuleDescriptor conflictingModule : conflictingModules) {
                error
                    .append("\n    - ")
                    .append(conflictingModule.moduleElement().getQualifiedName())
                    .append(" with scopes: ")
                    .append(COMMA_SEPARATED_JOINER.join(moduleScopes(conflictingModule)));
              }
            });
    return error.toString();
  }

  private ImmutableSet<Scope> moduleScopes(ModuleDescriptor module) {
    return FluentIterable.concat(module.allBindingDeclarations())
        .transform(declaration -> uniqueScopeOf(declaration.bindingElement().get()))
        .filter(scope -> scope.isPresent() && !scope.get().isReusable())
        .transform(scope -> scope.get())
        .toSet();
  }
}
