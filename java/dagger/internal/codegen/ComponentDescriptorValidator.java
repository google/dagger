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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentRequirement.Kind.BOUND_INSTANCE;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentAnnotation;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static dagger.internal.codegen.Scopes.scopesOf;
import static dagger.internal.codegen.Scopes.singletonScope;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.ErrorMessages.ComponentBuilderMessages;
import dagger.model.Scope;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Reports errors in the component hierarchy.
 *
 * <ul>
 *   <li>Validates scope hierarchy of component dependencies and subcompoennts.
 *   <li>Reports errors if there are component dependency cycles.
 *   <li>Reports errors if any abstract modules have non-abstract instance binding methods.
 *   <li>Validates component builder types.
 * </ul>
 */
// TODO(dpb): Combine with ComponentHierarchyValidator.
final class ComponentDescriptorValidator {

  private final Elements elements;
  private final DaggerTypes types;
  private final CompilerOptions compilerOptions;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final ComponentHierarchyValidator componentHierarchyValidator;

  @Inject
  ComponentDescriptorValidator(
      Elements elements,
      DaggerTypes types,
      CompilerOptions compilerOptions,
      MethodSignatureFormatter methodSignatureFormatter,
      ComponentHierarchyValidator componentHierarchyValidator) {
    this.elements = elements;
    this.types = types;
    this.compilerOptions = compilerOptions;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.componentHierarchyValidator = componentHierarchyValidator;
  }

  ValidationReport<TypeElement> validate(ComponentDescriptor component) {
    ComponentValidation validation = new ComponentValidation(component);
    validation.visitComponent(component);
    validation.report(component).addSubreport(componentHierarchyValidator.validate(component));
    return validation.buildReport();
  }

  private final class ComponentValidation {
    final ComponentDescriptor rootComponent;
    final Map<ComponentDescriptor, ValidationReport.Builder<TypeElement>> reports =
        new LinkedHashMap<>();

    ComponentValidation(ComponentDescriptor rootComponent) {
      this.rootComponent = checkNotNull(rootComponent);
    }

    /** Returns a report that contains all validation messages found during traversal. */
    ValidationReport<TypeElement> buildReport() {
      ValidationReport.Builder<TypeElement> report =
          ValidationReport.about(rootComponent.componentDefinitionType());
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

    void visitComponent(ComponentDescriptor component) {
      validateDependencyScopes(component);
      validateComponentDependencyHierarchy(component);
      validateModules(component);
      validateBuilders(component);
      component.subcomponents().forEach(this::visitComponent);
    }

    /** Validates that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(ComponentDescriptor component) {
      validateComponentDependencyHierarchy(
          component, component.componentDefinitionType(), new ArrayDeque<>());
    }

    /** Recursive method to validate that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(
        ComponentDescriptor component, TypeElement dependency, Deque<TypeElement> dependencyStack) {
      if (dependencyStack.contains(dependency)) {
        // Current component has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.componentDefinitionType().getQualifiedName());
        message.append(" contains a cycle in its component dependencies:\n");
        dependencyStack.push(dependency);
        appendIndentedComponentsList(message, dependencyStack);
        dependencyStack.pop();
        report(component)
            .addItem(
                message.toString(),
                compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                component.componentDefinitionType(),
                getComponentAnnotation(component.componentDefinitionType()).get());
      } else {
        Optional<AnnotationMirror> componentAnnotation = getComponentAnnotation(dependency);
        if (componentAnnotation.isPresent()) {
          dependencyStack.push(dependency);

          ImmutableSet<TypeElement> dependencies =
              MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get()));
          for (TypeElement nextDependency : dependencies) {
            validateComponentDependencyHierarchy(component, nextDependency, dependencyStack);
          }

          dependencyStack.pop();
        }
      }
    }

    /**
     * Validates that among the dependencies are at most one scoped dependency, that there are no
     * cycles within the scoping chain, and that singleton components have no scoped dependencies.
     */
    private void validateDependencyScopes(ComponentDescriptor component) {
      ImmutableSet<Scope> scopes = component.scopes();
      ImmutableSet<TypeElement> scopedDependencies =
          scopedTypesIn(
              component
                  .dependencies()
                  .stream()
                  .map(ComponentRequirement::typeElement)
                  .collect(toImmutableSet()));
      if (!scopes.isEmpty()) {
        Scope singletonScope = singletonScope(elements);
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()
            && scopes.contains(singletonScope)) {
          // Singleton is a special-case representing the longest lifetime, and therefore
          // @Singleton components may not depend on scoped components
          if (!scopedDependencies.isEmpty()) {
            StringBuilder message =
                new StringBuilder(
                    "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            report(component)
                .addItem(
                    message.toString(),
                    compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                    component.componentDefinitionType(),
                    component.componentAnnotation());
          }
        } else if (scopedDependencies.size() > 1) {
          // Scoped components may depend on at most one scoped component.
          StringBuilder message = new StringBuilder();
          for (Scope scope : scopes) {
            message.append(getReadableSource(scope)).append(' ');
          }
          message
              .append(component.componentDefinitionType().getQualifiedName())
              .append(" depends on more than one scoped component:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          report(component)
              .addError(
                  message.toString(),
                  component.componentDefinitionType(),
                  component.componentAnnotation());
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!compilerOptions.scopeCycleValidationType().equals(ValidationType.NONE)) {
            validateDependencyScopeHierarchy(
                component,
                component.componentDefinitionType(),
                new ArrayDeque<ImmutableSet<Scope>>(),
                new ArrayDeque<TypeElement>());
          }
        }
      } else {
        // Scopeless components may not depend on scoped components.
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message =
              new StringBuilder(component.componentDefinitionType().getQualifiedName())
                  .append(" (unscoped) cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          report(component)
              .addError(
                  message.toString(),
                  component.componentDefinitionType(),
                  component.componentAnnotation());
        }
      }
    }

    private void validateModules(ComponentDescriptor component) {
      for (ModuleDescriptor module : component.transitiveModules()) {
        if (module.moduleElement().getModifiers().contains(Modifier.ABSTRACT)) {
          for (ContributionBinding binding : module.bindings()) {
            if (binding.requiresModuleInstance()) {
              report(component).addError(abstractModuleHasInstanceBindingMethodsError(module));
              break;
            }
          }
        }
      }
    }

    private String abstractModuleHasInstanceBindingMethodsError(ModuleDescriptor module) {
      String methodAnnotations;
      switch (module.kind()) {
        case MODULE:
          methodAnnotations = "@Provides";
          break;
        case PRODUCER_MODULE:
          methodAnnotations = "@Provides or @Produces";
          break;
        default:
          throw new AssertionError(module.kind());
      }
      return String.format(
          "%s is abstract and has instance %s methods. Consider making the methods static or "
              + "including a non-abstract subclass of the module instead.",
          module.moduleElement(), methodAnnotations);
    }

    private void validateBuilders(ComponentDescriptor component) {
      ComponentDescriptor componentDesc = component;
      if (!componentDesc.builderSpec().isPresent()) {
        // If no builder, nothing to validate.
        return;
      }

      Set<ComponentRequirement> availableDependencies = component.availableDependencies();
      Set<ComponentRequirement> requiredDependencies =
          Sets.filter(
              availableDependencies,
              input -> input.nullPolicy(elements, types).equals(NullPolicy.THROW));
      final BuilderSpec spec = componentDesc.builderSpec().get();
      ImmutableSet<BuilderRequirementMethod> declaredSetters =
          spec.requirementMethods()
              .stream()
              .filter(method -> !method.requirement().kind().equals(BOUND_INSTANCE))
              .collect(toImmutableSet());
      ImmutableSet<ComponentRequirement> declaredRequirements =
          declaredSetters
              .stream()
              .map(BuilderRequirementMethod::requirement)
              .collect(toImmutableSet());

      ComponentBuilderMessages msgs = ErrorMessages.builderMsgsFor(component.kind());
      Set<ComponentRequirement> extraSetters =
          Sets.difference(declaredRequirements, availableDependencies);
      if (!extraSetters.isEmpty()) {
        List<ExecutableElement> excessMethods =
            declaredSetters
                .stream()
                .filter(method -> extraSetters.contains(method.requirement()))
                .map(BuilderRequirementMethod::method)
                .collect(toList());
        Optional<DeclaredType> container =
            Optional.of(MoreTypes.asDeclared(spec.builderDefinitionType().asType()));
        String formatted =
            excessMethods
                .stream()
                .map(method -> methodSignatureFormatter.format(method, container))
                .collect(joining(", ", "[", "]"));
        report(component)
            .addError(String.format(msgs.extraSetters(), formatted), spec.builderDefinitionType());
      }

      Set<ComponentRequirement> missingSetters =
          Sets.difference(requiredDependencies, declaredRequirements);
      if (!missingSetters.isEmpty()) {
        report(component)
            .addError(
                String.format(
                    msgs.missingSetters(),
                    missingSetters.stream().map(ComponentRequirement::type).collect(toList())),
                spec.builderDefinitionType());
      }

      // Validate that declared builder requirements (modules, dependencies) have unique types.
      Map<Equivalence.Wrapper<TypeMirror>, List<ExecutableElement>> declaredRequirementsByType =
          spec.requirementMethods()
              .stream()
              .filter(method -> !method.requirement().kind().equals(BOUND_INSTANCE))
              .collect(
                  groupingBy(
                      method -> method.requirement().wrappedType(),
                      mapping(method -> method.method(), toList())));
      for (Map.Entry<Equivalence.Wrapper<TypeMirror>, List<ExecutableElement>> entry :
          declaredRequirementsByType.entrySet()) {
        if (entry.getValue().size() > 1) {
          TypeMirror type = entry.getKey().get();
          report(component)
              .addError(
                  String.format(msgs.manyMethodsForType(), type, entry.getValue()),
                  spec.builderDefinitionType());
        }
      }
    }

    /**
     * Validates that scopes do not participate in a scoping cycle - that is to say, scoped
     * components are in a hierarchical relationship terminating with Singleton.
     *
     * <p>As a side-effect, this means scoped components cannot have a dependency cycle between
     * themselves, since a component's presence within its own dependency path implies a cyclical
     * relationship between scopes. However, cycles in component dependencies are explicitly checked
     * in {@link #validateComponentDependencyHierarchy(ComponentDescriptor)}.
     */
    private void validateDependencyScopeHierarchy(
        ComponentDescriptor component,
        TypeElement dependency,
        Deque<ImmutableSet<Scope>> scopeStack,
        Deque<TypeElement> scopedDependencyStack) {
      ImmutableSet<Scope> scopes = scopesOf(dependency);
      if (stackOverlaps(scopeStack, scopes)) {
        scopedDependencyStack.push(dependency);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.componentDefinitionType().getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
          report(component)
              .addItem(
                  message.toString(),
                  compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                  component.componentDefinitionType(),
                  getComponentAnnotation(component.componentDefinitionType()).get());
        }
        scopedDependencyStack.pop();
      } else {
        // TODO(beder): transitively check scopes of production components too.
        getAnnotationMirror(dependency, Component.class)
            .ifPresent(
                componentAnnotation -> {
                  ImmutableSet<TypeElement> scopedDependencies =
                      scopedTypesIn(
                          MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation)));
                  if (scopedDependencies.size() == 1) {
                    // empty can be ignored (base-case), and > 1 is a separately-reported error.
                    scopeStack.push(scopes);
                    scopedDependencyStack.push(dependency);
                    validateDependencyScopeHierarchy(
                        component,
                        getOnlyElement(scopedDependencies),
                        scopeStack,
                        scopedDependencyStack);
                    scopedDependencyStack.pop();
                    scopeStack.pop();
                  }
                }); // else: we skip component dependencies which are not components
      }
    }

    private <T> boolean stackOverlaps(Deque<ImmutableSet<T>> stack, ImmutableSet<T> set) {
      for (ImmutableSet<T> entry : stack) {
        if (!Sets.intersection(entry, set).isEmpty()) {
          return true;
        }
      }
      return false;
    }

    /** Appends and formats a list of indented component types (with their scope annotations). */
    private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
      for (TypeElement scopedComponent : types) {
        message.append(INDENT);
        for (Scope scope : scopesOf(scopedComponent)) {
          message.append(getReadableSource(scope)).append(' ');
        }
        message
            .append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
            .append('\n');
      }
    }

    /**
     * Returns a set of type elements containing only those found in the input set that have a
     * scoping annotation.
     */
    private ImmutableSet<TypeElement> scopedTypesIn(Set<TypeElement> types) {
      return types.stream().filter(type -> !scopesOf(type).isEmpty()).collect(toImmutableSet());
    }
  }
}
