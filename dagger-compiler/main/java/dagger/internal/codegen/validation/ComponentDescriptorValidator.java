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

package dagger.internal.codegen.validation;

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Collections2.transform;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotation;
import static dagger.internal.codegen.base.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.base.Scopes.getReadableSource;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.stream.Collectors.joining;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.binding.ModuleDescriptor;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/**
 * Reports errors in the component hierarchy.
 *
 * <ul>
 *   <li>Validates scope hierarchy of component dependencies and subcomponents.
 *   <li>Reports errors if there are component dependency cycles.
 *   <li>Reports errors if any abstract modules have non-abstract instance binding methods.
 *   <li>Validates component creator types.
 * </ul>
 */
// TODO(dpb): Combine with ComponentHierarchyValidator.
public final class ComponentDescriptorValidator {

  private final CompilerOptions compilerOptions;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final ComponentHierarchyValidator componentHierarchyValidator;
  private final InjectionAnnotations injectionAnnotations;
  private final DaggerSuperficialValidation superficialValidation;

  @Inject
  ComponentDescriptorValidator(
      CompilerOptions compilerOptions,
      MethodSignatureFormatter methodSignatureFormatter,
      ComponentHierarchyValidator componentHierarchyValidator,
      InjectionAnnotations injectionAnnotations,
      DaggerSuperficialValidation superficialValidation) {
    this.compilerOptions = compilerOptions;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.componentHierarchyValidator = componentHierarchyValidator;
    this.injectionAnnotations = injectionAnnotations;
    this.superficialValidation = superficialValidation;
  }

  public ValidationReport validate(ComponentDescriptor component) {
    ComponentValidation validation = new ComponentValidation(component);
    validation.visitComponent(component);
    validation.report(component).addSubreport(componentHierarchyValidator.validate(component));
    return validation.buildReport();
  }

  private final class ComponentValidation {
    final ComponentDescriptor rootComponent;
    final Map<ComponentDescriptor, ValidationReport.Builder> reports = new LinkedHashMap<>();

    ComponentValidation(ComponentDescriptor rootComponent) {
      this.rootComponent = checkNotNull(rootComponent);
    }

    /** Returns a report that contains all validation messages found during traversal. */
    ValidationReport buildReport() {
      ValidationReport.Builder report = ValidationReport.about(rootComponent.typeElement());
      reports.values().forEach(subreport -> report.addSubreport(subreport.build()));
      return report.build();
    }

    /** Returns the report builder for a (sub)component. */
    private ValidationReport.Builder report(ComponentDescriptor component) {
      return reentrantComputeIfAbsent(
          reports, component, descriptor -> ValidationReport.about(descriptor.typeElement()));
    }

    private void reportComponentItem(
        Diagnostic.Kind kind, ComponentDescriptor component, String message) {
      report(component)
          .addItem(message, kind, component.typeElement(), component.annotation().annotation());
    }

    private void reportComponentError(ComponentDescriptor component, String error) {
      reportComponentItem(ERROR, component, error);
    }

    void visitComponent(ComponentDescriptor component) {
      validateDependencyScopes(component);
      validateComponentDependencyHierarchy(component);
      validateModules(component);
      validateCreators(component);
      component.childComponents().forEach(this::visitComponent);
    }

    /** Validates that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(ComponentDescriptor component) {
      validateComponentDependencyHierarchy(component, component.typeElement(), new ArrayDeque<>());
    }

    /** Recursive method to validate that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(
        ComponentDescriptor component,
        XTypeElement dependency,
        Deque<XTypeElement> dependencyStack) {
      if (dependencyStack.contains(dependency)) {
        // Current component has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.typeElement().getQualifiedName());
        message.append(" contains a cycle in its component dependencies:\n");
        dependencyStack.push(dependency);
        appendIndentedComponentsList(message, dependencyStack);
        dependencyStack.pop();
        reportComponentItem(
            compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
            component,
            message.toString());
      } else if (compilerOptions.validateTransitiveComponentDependencies()
          // Always validate direct component dependencies referenced by this component regardless
          // of the flag value
          || dependencyStack.isEmpty()) {
        rootComponentAnnotation(dependency, superficialValidation)
            .ifPresent(
                componentAnnotation -> {
                  dependencyStack.push(dependency);
                  for (XTypeElement nextDependency : componentAnnotation.dependencies()) {
                    validateComponentDependencyHierarchy(
                        component, nextDependency, dependencyStack);
                  }
                  dependencyStack.pop();
                });
      }
    }

    /**
     * Validates that among the dependencies there are no cycles within the scoping chain, and that
     * singleton components have no scoped dependencies.
     */
    private void validateDependencyScopes(ComponentDescriptor component) {
      ImmutableSet<XClassName> scopes =
          component.scopes().stream()
              .map(Scope::scopeAnnotation)
              .map(DaggerAnnotation::xprocessing)
              .map(XAnnotations::asClassName)
              .collect(toImmutableSet());
      ImmutableSet<XTypeElement> scopedDependencies =
          scopedTypesIn(
              component.dependencies().stream()
                  .map(ComponentRequirement::typeElement)
                  .collect(toImmutableSet()));
      if (!scopes.isEmpty()) {
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()
            && (scopes.contains(XTypeNames.SINGLETON)
                || scopes.contains(XTypeNames.SINGLETON_JAVAX))) {
          // Singleton is a special-case representing the longest lifetime, and therefore
          // @Singleton components may not depend on scoped components
          if (!scopedDependencies.isEmpty()) {
            StringBuilder message =
                new StringBuilder(
                    "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            reportComponentItem(
                compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                component,
                message.toString());
          }
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!compilerOptions.scopeCycleValidationType().equals(ValidationType.NONE)) {
            validateDependencyScopeHierarchy(
                component, component.typeElement(), new ArrayDeque<>(), new ArrayDeque<>());
          }
        }
      } else {
        // Scopeless components may not depend on scoped components.
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message =
              new StringBuilder(component.typeElement().getQualifiedName())
                  .append(" (unscoped) cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportComponentError(component, message.toString());
        }
      }
    }

    private void validateModules(ComponentDescriptor component) {
      for (ModuleDescriptor module : component.modules()) {
        if (module.moduleElement().isAbstract()) {
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

    private void validateCreators(ComponentDescriptor component) {
      if (!component.creatorDescriptor().isPresent()) {
        // If no builder, nothing to validate.
        return;
      }

      ComponentCreatorDescriptor creator = component.creatorDescriptor().get();
      ComponentCreatorMessages messages = ErrorMessages.creatorMessagesFor(creator.annotation());

      // Requirements for modules and dependencies that the creator can set
      Set<ComponentRequirement> creatorModuleAndDependencyRequirements =
          creator.moduleAndDependencyRequirements();
      // Modules and dependencies the component requires
      Set<ComponentRequirement> componentModuleAndDependencyRequirements =
          component.dependenciesAndConcreteModules();

      // Requirements that the creator can set that don't match any requirements that the component
      // actually has.
      Set<ComponentRequirement> inapplicableRequirementsOnCreator =
          Sets.difference(
              creatorModuleAndDependencyRequirements, componentModuleAndDependencyRequirements);

      XType container = creator.typeElement().getType();
      if (!inapplicableRequirementsOnCreator.isEmpty()) {
        Collection<XElement> excessElements =
            Multimaps.filterKeys(
                    creator.unvalidatedRequirementElements(), in(inapplicableRequirementsOnCreator))
                .values();
        String formatted =
            excessElements.stream()
                .map(element -> formatElement(element, container))
                .collect(joining(", ", "[", "]"));
        report(component)
            .addError(String.format(messages.extraSetters(), formatted), creator.typeElement());
      }

      // Component requirements that the creator must be able to set
      Set<ComponentRequirement> mustBePassed =
          Sets.filter(
              componentModuleAndDependencyRequirements,
              input -> input.nullPolicy().equals(NullPolicy.THROW));
      // Component requirements that the creator must be able to set, but can't
      Set<ComponentRequirement> missingRequirements =
          Sets.difference(mustBePassed, creatorModuleAndDependencyRequirements);

      if (!missingRequirements.isEmpty()) {
        report(component)
            .addError(
                String.format(
                    messages.missingSetters(),
                    missingRequirements.stream()
                        .map(ComponentRequirement::type)
                        .map(XTypes::toStableString)
                        .collect(toImmutableList())),
                creator.typeElement());
      }

      // Validate that declared creator requirements (modules, dependencies) have unique types.
      ImmutableSetMultimap<TypeName, XElement> declaredRequirementsByType =
          Multimaps.filterKeys(
                  creator.unvalidatedRequirementElements(),
                  creatorModuleAndDependencyRequirements::contains)
              .entries()
              .stream()
              .collect(
                  toImmutableSetMultimap(
                      entry -> entry.getKey().type().getTypeName(), Entry::getValue));
      declaredRequirementsByType
          .asMap()
          .forEach(
              (type, elementsForType) -> {
                if (elementsForType.size() > 1) {
                  // TODO(cgdecker): Attach this error message to the factory method rather than
                  // the component type if the elements are factory method parameters AND the
                  // factory method is defined by the factory type itself and not by a supertype.
                  report(component)
                      .addError(
                          String.format(
                              messages.multipleSettersForModuleOrDependencyType(),
                              type,
                              transform(
                                  elementsForType, element -> formatElement(element, container))),
                          creator.typeElement());
                }
              });

      // TODO(cgdecker): Duplicate binding validation should handle the case of multiple elements
      // that set the same bound-instance Key, but validating that here would make it fail faster
      // for subcomponents.
    }

    private String formatElement(XElement element, XType container) {
      // TODO(cgdecker): Extract some or all of this to another class?
      // But note that it does different formatting for parameters than
      // DaggerElements.elementToString(Element).
      if (isMethod(element)) {
        return methodSignatureFormatter.format(asMethod(element), Optional.of(container));
      } else if (isMethodParameter(element)) {
        return formatParameter(asMethodParameter(element), container);
      }
      // This method shouldn't be called with any other type of element.
      throw new AssertionError();
    }

    private String formatParameter(XExecutableParameterElement parameter, XType container) {
      // TODO(cgdecker): Possibly leave the type (and annotations?) off of the parameters here and
      // just use their names, since the type will be redundant in the context of the error message.
      StringJoiner joiner = new StringJoiner(" ");
      parameter.getAllAnnotations().stream()
          .map(XAnnotation::getQualifiedName)
          .forEach(joiner::add);
      XType parameterType = resolveParameterType(parameter, container);
      return joiner
          .add(stripCommonTypePrefixes(parameterType.getTypeName().toString()))
          .add(getSimpleName(parameter))
          .toString();
    }

    private XType resolveParameterType(XExecutableParameterElement parameter, XType container) {
      checkArgument(isMethod(parameter.getEnclosingElement()));
      XMethodElement method = asMethod(parameter.getEnclosingElement());
      int parameterIndex = method.getParameters().indexOf(parameter);

      XMethodType methodType = method.asMemberOf(container);
      return methodType.getParameterTypes().get(parameterIndex);
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
        XTypeElement dependency,
        Deque<ImmutableSet<Scope>> scopeStack,
        Deque<XTypeElement> scopedDependencyStack) {
      ImmutableSet<Scope> scopes = injectionAnnotations.getScopes(dependency);
      if (stackOverlaps(scopeStack, scopes)) {
        scopedDependencyStack.push(dependency);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(component.typeElement().getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
          reportComponentItem(
              compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
              component,
              message.toString());
        }
        scopedDependencyStack.pop();
      } else if (compilerOptions.validateTransitiveComponentDependencies()
          // Always validate direct component dependencies referenced by this component regardless
          // of the flag value
          || scopedDependencyStack.isEmpty()) {
        // TODO(beder): transitively check scopes of production components too.
        rootComponentAnnotation(dependency, superficialValidation)
            .filter(componentAnnotation -> !componentAnnotation.isProduction())
            .ifPresent(
                componentAnnotation -> {
                  ImmutableSet<XTypeElement> scopedDependencies =
                      scopedTypesIn(componentAnnotation.dependencies());
                  if (!scopedDependencies.isEmpty()) {
                    // empty can be ignored (base-case)
                    scopeStack.push(scopes);
                    scopedDependencyStack.push(dependency);
                    for (XTypeElement scopedDependency : scopedDependencies) {
                      validateDependencyScopeHierarchy(
                          component, scopedDependency, scopeStack, scopedDependencyStack);
                    }
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
    private void appendIndentedComponentsList(StringBuilder message, Iterable<XTypeElement> types) {
      for (XTypeElement scopedComponent : types) {
        message.append(INDENT);
        for (Scope scope : injectionAnnotations.getScopes(scopedComponent)) {
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
    private ImmutableSet<XTypeElement> scopedTypesIn(Collection<XTypeElement> types) {
      return types.stream()
          .filter(type -> !injectionAnnotations.getScopes(type).isEmpty())
          .collect(toImmutableSet());
    }
  }
}
