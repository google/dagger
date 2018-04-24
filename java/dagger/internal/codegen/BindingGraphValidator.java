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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElements;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingType.PROVISION;
import static dagger.internal.codegen.ComponentRequirement.Kind.BOUND_INSTANCE;
import static dagger.internal.codegen.ComponentRequirement.Kind.MODULE;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentAnnotation;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DiagnosticFormatting.stripCommonTypePrefixes;
import static dagger.internal.codegen.ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT;
import static dagger.internal.codegen.ErrorMessages.DEPENDS_ON_PRODUCTION_EXECUTOR_FORMAT;
import static dagger.internal.codegen.ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT;
import static dagger.internal.codegen.ErrorMessages.DUPLICATE_SIZE_LIMIT;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_CONTRIBUTION_TYPES_FOR_KEY_FORMAT;
import static dagger.internal.codegen.ErrorMessages.abstractModuleHasInstanceBindingMethods;
import static dagger.internal.codegen.ErrorMessages.referenceReleasingScopeMetadataMissingCanReleaseReferences;
import static dagger.internal.codegen.ErrorMessages.referenceReleasingScopeNotAnnotatedWithMetadata;
import static dagger.internal.codegen.ErrorMessages.referenceReleasingScopeNotInComponentHierarchy;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.internal.codegen.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.RequestKinds.entryPointCanUseProduction;
import static dagger.internal.codegen.RequestKinds.extractKeyType;
import static dagger.internal.codegen.RequestKinds.getRequestKind;
import static dagger.internal.codegen.Scopes.getReadableSource;
import static dagger.internal.codegen.Scopes.scopesOf;
import static dagger.internal.codegen.Scopes.singletonScope;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.Lazy;
import dagger.internal.codegen.ComponentDescriptor.BuilderRequirementMethod;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ComponentRequirement.NullPolicy;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.ErrorMessages.ComponentBuilderMessages;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import dagger.model.Scope;
import dagger.releasablereferences.CanReleaseReferences;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** Reports errors in the shape of the binding graph. */
final class BindingGraphValidator {

  private final Elements elements;
  private final DaggerTypes types;
  private final CompilerOptions compilerOptions;
  private final InjectBindingRegistry injectBindingRegistry;
  private final BindingDeclarationFormatter bindingDeclarationFormatter;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final KeyFactory keyFactory;

  @Inject
  BindingGraphValidator(
      Elements elements,
      DaggerTypes types,
      CompilerOptions compilerOptions,
      InjectBindingRegistry injectBindingRegistry,
      BindingDeclarationFormatter bindingDeclarationFormatter,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFormatter dependencyRequestFormatter,
      KeyFactory keyFactory) {
    this.elements = elements;
    this.types = types;
    this.compilerOptions = compilerOptions;
    this.injectBindingRegistry = injectBindingRegistry;
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.keyFactory = keyFactory;
  }

  private final class ComponentValidation extends ComponentTreeTraverser {
    final BindingGraph rootGraph;
    final Map<ComponentDescriptor, ValidationReport.Builder<TypeElement>> reports =
        new LinkedHashMap<>();

    /** Bindings whose scopes are not compatible with the component that owns them. */
    private final SetMultimap<ComponentDescriptor, ContributionBinding> incompatiblyScopedBindings =
        LinkedHashMultimap.create();

    ComponentValidation(BindingGraph rootGraph) {
      super(rootGraph);
      this.rootGraph = rootGraph;
    }

    @Override
    protected BindingGraphTraverser bindingGraphTraverser(
        ComponentTreePath componentPath, ComponentMethodDescriptor entryPointMethod) {
      return new BindingGraphValidation(componentPath, entryPointMethod);
    }

    /** Returns a report that contains all validation messages found during traversal. */
    ValidationReport<TypeElement> buildReport() {
      ValidationReport.Builder<TypeElement> report =
          ValidationReport.about(rootGraph.componentType());
      reports.values().forEach(subreport -> report.addSubreport(subreport.build()));
      return report.build();
    }

    /** Returns the report builder for a (sub)component. */
    private ValidationReport.Builder<TypeElement> report(BindingGraph graph) {
      return reentrantComputeIfAbsent(
          reports,
          graph.componentDescriptor(),
          descriptor -> ValidationReport.about(descriptor.componentDefinitionType()));
    }

    @Override
    protected void visitComponent(BindingGraph graph) {
      validateDependencyScopes(graph);
      validateComponentDependencyHierarchy(graph);
      validateModules(graph);
      validateBuilders(graph);
      super.visitComponent(graph);
      checkScopedBindings(graph);
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
        report(parent)
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

    /** Validates that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(BindingGraph graph) {
      validateComponentDependencyHierarchy(graph, graph.componentType(), new ArrayDeque<>());
    }

    /** Recursive method to validate that component dependencies do not form a cycle. */
    private void validateComponentDependencyHierarchy(
        BindingGraph graph, TypeElement dependency, Deque<TypeElement> dependencyStack) {
      if (dependencyStack.contains(dependency)) {
        // Current component has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(graph.componentType().getQualifiedName());
        message.append(" contains a cycle in its component dependencies:\n");
        dependencyStack.push(dependency);
        appendIndentedComponentsList(message, dependencyStack);
        dependencyStack.pop();
        report(graph)
            .addItem(
                message.toString(),
                compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                graph.componentType(),
                getComponentAnnotation(graph.componentType()).get());
      } else {
        Optional<AnnotationMirror> componentAnnotation = getComponentAnnotation(dependency);
        if (componentAnnotation.isPresent()) {
          dependencyStack.push(dependency);

          ImmutableSet<TypeElement> dependencies =
              MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get()));
          for (TypeElement nextDependency : dependencies) {
            validateComponentDependencyHierarchy(graph, nextDependency, dependencyStack);
          }

          dependencyStack.pop();
        }
      }
    }

    /**
     * Validates that among the dependencies are at most one scoped dependency, that there are no
     * cycles within the scoping chain, and that singleton components have no scoped dependencies.
     */
    private void validateDependencyScopes(BindingGraph graph) {
      ComponentDescriptor descriptor = graph.componentDescriptor();
      ImmutableSet<Scope> scopes = descriptor.scopes();
      ImmutableSet<TypeElement> scopedDependencies =
          scopedTypesIn(
              descriptor
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
            StringBuilder message = new StringBuilder(
                "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            report(graph)
                .addItem(
                    message.toString(),
                    compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                    descriptor.componentDefinitionType(),
                    descriptor.componentAnnotation());
          }
        } else if (scopedDependencies.size() > 1) {
          // Scoped components may depend on at most one scoped component.
          StringBuilder message = new StringBuilder();
          for (Scope scope : scopes) {
            message.append(getReadableSource(scope)).append(' ');
          }
          message
              .append(descriptor.componentDefinitionType().getQualifiedName())
              .append(" depends on more than one scoped component:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          report(graph)
              .addError(
                  message.toString(),
                  descriptor.componentDefinitionType(),
                  descriptor.componentAnnotation());
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!compilerOptions.scopeCycleValidationType().equals(ValidationType.NONE)) {
            validateDependencyScopeHierarchy(
                graph,
                descriptor.componentDefinitionType(),
                new ArrayDeque<ImmutableSet<Scope>>(),
                new ArrayDeque<TypeElement>());
          }
        }
      } else {
        // Scopeless components may not depend on scoped components.
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message =
              new StringBuilder(descriptor.componentDefinitionType().getQualifiedName())
                  .append(" (unscoped) cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          report(graph)
              .addError(
                  message.toString(),
                  descriptor.componentDefinitionType(),
                  descriptor.componentAnnotation());
        }
      }
    }

    private void validateModules(BindingGraph graph) {
      for (ModuleDescriptor module : graph.componentDescriptor().transitiveModules()) {
        if (module.moduleElement().getModifiers().contains(Modifier.ABSTRACT)) {
          for (ContributionBinding binding : module.bindings()) {
            if (binding.requiresModuleInstance()) {
              report(graph).addError(abstractModuleHasInstanceBindingMethods(module));
              break;
            }
          }
        }
      }
    }

    private void validateBuilders(BindingGraph graph) {
      ComponentDescriptor componentDesc = graph.componentDescriptor();
      if (!componentDesc.builderSpec().isPresent()) {
        // If no builder, nothing to validate.
        return;
      }

      Set<ComponentRequirement> availableDependencies = graph.availableDependencies();
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

      ComponentBuilderMessages msgs =
          ErrorMessages.builderMsgsFor(graph.componentDescriptor().kind());
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
        report(graph)
            .addError(String.format(msgs.extraSetters(), formatted), spec.builderDefinitionType());
      }

      Set<ComponentRequirement> missingSetters =
          Sets.difference(requiredDependencies, declaredRequirements);
      if (!missingSetters.isEmpty()) {
        report(graph)
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
          report(graph)
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
     * in {@link #validateComponentDependencyHierarchy(BindingGraph)}.
     */
    private void validateDependencyScopeHierarchy(
        BindingGraph graph,
        TypeElement dependency,
        Deque<ImmutableSet<Scope>> scopeStack,
        Deque<TypeElement> scopedDependencyStack) {
      ImmutableSet<Scope> scopes = scopesOf(dependency);
      if (stackOverlaps(scopeStack, scopes)) {
        scopedDependencyStack.push(dependency);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(graph.componentType().getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
          report(graph)
              .addItem(
                  message.toString(),
                  compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                  graph.componentType(),
                  getComponentAnnotation(graph.componentType()).get());
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
                        graph,
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

    /**
     * Collects scoped bindings that are not compatible with their owning component for later
     * reporting by {@link #checkScopedBindings(BindingGraph)}.
     */
    private void checkBindingScope(
        ContributionBinding binding, ComponentDescriptor owningComponent) {
      if (binding.scope().isPresent()
          && !binding.scope().get().isReusable()
          && !owningComponent.scopes().contains(binding.scope().get())) {
        incompatiblyScopedBindings.put(owningComponent, binding);
      }
    }

    /**
     * Reports an error if any of the scoped bindings owned by a given component are incompatible
     * with the component. Must be called after all bindings owned by the given component have been
     * {@linkplain #checkBindingScope(ContributionBinding, ComponentDescriptor) visited}.
     */
    private void checkScopedBindings(BindingGraph graph) {
      if (!incompatiblyScopedBindings.containsKey(graph.componentDescriptor())) {
        return;
      }

      StringBuilder message = new StringBuilder(graph.componentType().getQualifiedName());
      if (!graph.componentDescriptor().scopes().isEmpty()) {
        message.append(" scoped with ");
        for (Scope scope : graph.componentDescriptor().scopes()) {
          message.append(getReadableSource(scope)).append(' ');
        }
        message.append("may not reference bindings with different scopes:\n");
      } else {
        message.append(" (unscoped) may not reference scoped bindings:\n");
      }
      for (ContributionBinding binding :
          incompatiblyScopedBindings.get(graph.componentDescriptor())) {
        message.append(INDENT);

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
                .append(binding.bindingTypeElement().get().getQualifiedName());
            break;

          default:
            throw new AssertionError(binding);
        }

        message.append("\n");
      }
      report(graph)
          .addError(
              message.toString(),
              graph.componentType(),
              graph.componentDescriptor().componentAnnotation());
    }

    final class BindingGraphValidation extends BindingGraphTraverser {

      BindingGraphValidation(
          ComponentTreePath componentPath, ComponentMethodDescriptor entryPointMethod) {
        super(componentPath, entryPointMethod);
      }

      /** Reports an error for the current component at the entry point. */
      private void reportErrorAtEntryPoint(String format, Object... args) {
        reportErrorAtEntryPoint(currentGraph(), format, args);
      }

      /** Reports an error for the given component at the entry point. */
      private void reportErrorAtEntryPoint(BindingGraph graph, String format, Object... args) {
        String message = args.length == 0 ? format : String.format(format, args);
        report(graph).addError(message, entryPointElement());
      }

      private String formatDependencyTrace() {
        return dependencyRequestFormatter.format(dependencyTrace());
      }

      @Override
      protected void visitDependencyRequest(DependencyRequest dependencyRequest) {
        if (atDependencyCycle()) {
          reportDependencyCycle();
        } else {
          super.visitDependencyRequest(dependencyRequest);
        }
      }

      @Override
      protected void visitResolvedBindings(ResolvedBindings resolvedBindings) {
        if (resolvedBindings.isEmpty()) {
          reportMissingBinding();
        } else if (resolvedBindings.bindings().size() > 1) {
          reportDuplicateBindings();
        }
        super.visitResolvedBindings(resolvedBindings);
      }

      @Override
      protected void visitContributionBinding(
          ContributionBinding binding, ComponentDescriptor owningComponent) {
        checkBindingScope(binding, owningComponent);
        if (compilerOptions.usesProducers()) {
          // TODO(dpb,beder): Validate this during @Inject/@Provides/@Produces validation.
          // Only the Dagger-specific binding may depend on the production executor.
          Key productionImplementationExecutorKey =
              keyFactory.forProductionImplementationExecutor();
          if (!binding.key().equals(productionImplementationExecutorKey)) {
            Key productionExecutorKey = keyFactory.forProductionExecutor();
            for (DependencyRequest request : binding.explicitDependencies()) {
              if (request.key().equals(productionExecutorKey)
                  || request.key().equals(productionImplementationExecutorKey)) {
                reportDependsOnProductionExecutor();
              }
            }
          }
        }
        super.visitContributionBinding(binding, owningComponent);
      }

      /**
       * Returns the binding declarations that can be reported for {@code resolvedBindings}, indexed
       * by the component that owns each declaration.
       *
       * <p>Contains all {@link MultibindingDeclaration}s, {@link SubcomponentDeclaration}s, and
       * {@link OptionalBindingDeclaration}s within {@code resolvedBindings}, as well as all {@link
       * ContributionBinding}s with present {@linkplain BindingDeclaration#bindingElement() binding
       * elements}.
       *
       * <p>Includes {@link BindingKind#RELEASABLE_REFERENCE_MANAGER} or
       * {@link BindingKind#RELEASABLE_REFERENCE_MANAGERS} bindings, even
       * though they have no binding elements, because they will be reported via the declared
       * scopes.
       *
       * <p>For other bindings without binding elements, such as the {@link
       * ContributionBinding#isSyntheticMultibinding()}, includes the conflicting declarations in
       * their resolved dependencies.
       */
      private ImmutableSetMultimap<ComponentDescriptor, BindingDeclaration>
          reportableDeclarations() {
        ImmutableSetMultimap.Builder<ComponentDescriptor, BindingDeclaration> declarations =
            ImmutableSetMultimap.builder();

        Queue<ResolvedBindings> queue = new ArrayDeque<>();
        queue.add(resolvedBindings());

        while (!queue.isEmpty()) {
          ResolvedBindings queued = queue.remove();
          declarations
              .putAll(queued.owningComponent(), queued.multibindingDeclarations())
              .putAll(queued.owningComponent(), queued.subcomponentDeclarations())
              .putAll(queued.owningComponent(), queued.optionalBindingDeclarations());
          queued
              .allContributionBindings()
              .asMap()
              .forEach(
                  (owningComponent, bindings) -> {
                    BindingGraph owningGraph =
                        componentTreePath().graphForComponent(owningComponent);
                    for (ContributionBinding binding : bindings) {
                      if (bindingDeclarationFormatter.canFormat(binding)) {
                        declarations.put(owningComponent, binding);
                      } else {
                        queue.addAll(owningGraph.resolvedDependencies(binding));
                      }
                    }
                  });
        }

        return declarations.build();
      }

      /**
       * Descriptive portion of the error message for when the given request has no binding.
       * Currently, the only other portions of the message are the dependency path, line number and
       * filename.
       */
      private StringBuilder requiresErrorMessageBase() {
        Key key = dependencyRequest().key();
        StringBuilder errorMessage = new StringBuilder();
        // TODO(dpb): Check for wildcard injection somewhere else first?
        if (key.type().getKind().equals(TypeKind.WILDCARD)) {
          errorMessage
              .append("Dagger does not support injecting Provider<T>, Lazy<T> or Produced<T> when ")
              .append("T is a wildcard type such as ")
              .append(formatCurrentDependencyRequestKey());
        } else {
          // TODO(ronshapiro): replace "provided" with "satisfied"?
          errorMessage
              .append(formatCurrentDependencyRequestKey())
              .append(" cannot be provided without ");
          if (isValidImplicitProvisionKey(key, types)) {
            errorMessage.append("an @Inject constructor or ");
          }
          errorMessage.append("an @Provides-");
          if (dependencyRequestCanUseProduction()) {
            errorMessage.append(" or @Produces-");
          }
          errorMessage.append("annotated method.");
        }
        if (isValidMembersInjectionKey(key)
            && injectBindingRegistry.getOrFindMembersInjectionBinding(key)
                .map(binding -> !binding.injectionSites().isEmpty())
                .orElse(false)) {
          errorMessage.append(" ").append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
        }
        return errorMessage.append('\n');
      }

      private void reportMissingBinding() {
        if (reportMissingReleasableReferenceManager()) {
          return;
        }
        StringBuilder errorMessage = requiresErrorMessageBase().append(formatDependencyTrace());
        for (String suggestion :
            MissingBindingSuggestions.forKey(rootGraph, dependencyRequest().key())) {
          errorMessage.append('\n').append(suggestion);
        }
        reportErrorAtEntryPoint(rootGraph, errorMessage.toString());
      }

      /**
       * If the current dependency request is missing a binding because it's an invalid
       * {@code @ForReleasableReferences} request, reports that.
       *
       * <p>An invalid request is one whose type is either {@link ReleasableReferenceManager} or
       * {@link TypedReleasableReferenceManager}, and whose scope:
       *
       * <ul>
       *   <li>does not annotate any component in the hierarchy, or
       *   <li>is not annotated with the metadata annotation type that is the {@link
       *       TypedReleasableReferenceManager}'s type argument
       * </ul>
       *
       * @return {@code true} if the request was invalid and an error was reported
       */
      private boolean reportMissingReleasableReferenceManager() {
        Key key = dependencyRequest().key();
        if (!key.qualifier().isPresent()
            || !isTypeOf(ForReleasableReferences.class, key.qualifier().get().getAnnotationType())
            || !isType(key.type())) {
          return false;
        }

        Optional<DeclaredType> metadataType;
        if (isTypeOf(ReleasableReferenceManager.class, key.type())) {
          metadataType = Optional.empty();
        } else if (isTypeOf(TypedReleasableReferenceManager.class, key.type())) {
          List<? extends TypeMirror> typeArguments =
              MoreTypes.asDeclared(key.type()).getTypeArguments();
          if (typeArguments.size() != 1
              || !typeArguments.get(0).getKind().equals(TypeKind.DECLARED)) {
            return false;
          }
          metadataType = Optional.of(MoreTypes.asDeclared(typeArguments.get(0)));
        } else {
          return false;
        }

        Scope scope =
            Scopes.scope(MoreTypes.asTypeElement(getTypeValue(key.qualifier().get(), "value")));
        String missingRequestKey = formatCurrentDependencyRequestKey();
        if (!rootGraph.componentDescriptor().releasableReferencesScopes().contains(scope)) {
          reportErrorAtEntryPoint(
              rootGraph,
              referenceReleasingScopeNotInComponentHierarchy(missingRequestKey, scope, rootGraph));
          return true;
        }
        if (metadataType.isPresent()) {
          if (!isAnnotationPresent(scope.scopeAnnotationElement(), metadataType.get())) {
            reportErrorAtEntryPoint(
                rootGraph,
                referenceReleasingScopeNotAnnotatedWithMetadata(
                    missingRequestKey, scope, metadataType.get()));
          }
          if (!isAnnotationPresent(metadataType.get().asElement(), CanReleaseReferences.class)) {
            reportErrorAtEntryPoint(
                rootGraph,
                referenceReleasingScopeMetadataMissingCanReleaseReferences(
                    missingRequestKey, metadataType.get()));
          }
        }
        return false;
      }

      @SuppressWarnings("resource") // Appendable is a StringBuilder.
      private void reportDependsOnProductionExecutor() {
        reportErrorAtEntryPoint(
            DEPENDS_ON_PRODUCTION_EXECUTOR_FORMAT, formatCurrentDependencyRequestKey());
      }

      @SuppressWarnings("resource") // Appendable is a StringBuilder.
      private void reportDuplicateBindings() {
        // If any of the duplicate bindings results from multibinding contributions or declarations,
        // report the conflict using those contributions and declarations.
        if (resolvedBindings()
            .contributionBindings()
            .stream()
            // TODO(dpb): Kill with fire.
            .anyMatch(ContributionBinding::isSyntheticMultibinding)) {
          reportMultipleContributionTypes();
          return;
        }
        StringBuilder builder = new StringBuilder();
        new Formatter(builder)
            .format(DUPLICATE_BINDINGS_FOR_KEY_FORMAT, formatCurrentDependencyRequestKey());
        ImmutableSetMultimap<ComponentDescriptor, BindingDeclaration> duplicateDeclarations =
            reportableDeclarations();
        bindingDeclarationFormatter.formatIndentedList(
            builder, duplicateDeclarations.values(), 1, DUPLICATE_SIZE_LIMIT);
        reportErrorAtEntryPoint(
            componentTreePath().rootmostGraph(duplicateDeclarations.keySet()), builder.toString());
      }

      @SuppressWarnings("resource") // Appendable is a StringBuilder.
      private void reportMultipleContributionTypes() {
        StringBuilder builder = new StringBuilder();
        new Formatter(builder)
            .format(
                MULTIPLE_CONTRIBUTION_TYPES_FOR_KEY_FORMAT, formatCurrentDependencyRequestKey());
        ImmutableSetMultimap<ComponentDescriptor, BindingDeclaration> duplicateDeclarations =
            reportableDeclarations();
        ImmutableListMultimap<ContributionType, BindingDeclaration> duplicateDeclarationsByType =
            Multimaps.index(
                duplicateDeclarations.values(),
                declaration ->
                    declaration instanceof HasContributionType
                        ? ((HasContributionType) declaration).contributionType()
                        : ContributionType.UNIQUE);
        verify(
            duplicateDeclarationsByType.keySet().size() > 1,
            "expected multiple contribution types for %s: %s",
            dependencyRequest().key(),
            duplicateDeclarationsByType);
        ImmutableSortedMap.copyOf(Multimaps.asMap(duplicateDeclarationsByType))
            .forEach(
                (contributionType, declarations) -> {
                  builder.append(INDENT);
                  builder.append(formatContributionType(contributionType));
                  builder.append(" bindings and declarations:");
                  bindingDeclarationFormatter.formatIndentedList(
                      builder, declarations, 2, DUPLICATE_SIZE_LIMIT);
                  builder.append('\n');
                });
        reportErrorAtEntryPoint(
            componentTreePath().rootmostGraph(duplicateDeclarations.keySet()), builder.toString());
      }

      // TODO(cgruber): Provide a hint for the start and end of the cycle.
      private void reportDependencyCycle() {
        if (!providersBreakingCycle().isEmpty()) {
          return;
        }
        ImmutableList.Builder<ContributionBinding> cycleBindings = ImmutableList.builder();
        cycleDependencyTrace()
            .forEach(
                (dependencyRequest, resolvedBindings) ->
                    cycleBindings.addAll(resolvedBindings.contributionBindings()));
        reportErrorAtEntryPoint(
            owningGraph(cycleBindings.build()),
            CONTAINS_DEPENDENCY_CYCLE_FORMAT,
            formatDependencyTrace());
      }

      /**
       * Returns any steps in a dependency cycle that "break" the cycle. These are any nonsynthetic
       * {@link Provider}, {@link Lazy}, or {@code Map<K, Provider<V>>} requests after the first
       * request in the cycle.
       *
       * <p>The synthetic request for a {@code Map<K, Provider<V>>} as a dependency of a multibound
       * {@code Map<K, V>} does not break cycles because the map's {@link Provider}s' {@link
       * Provider#get() get()} methods are called during provision.
       *
       * <p>A request for an instance of {@code Optional} breaks the cycle if it is resolved to a
       * {@link BindsOptionalOf} binding and a request for the {@code Optional}'s type parameter
       * would.
       */
      private ImmutableSet<DependencyRequest> providersBreakingCycle() {
        ImmutableSet.Builder<DependencyRequest> providers = ImmutableSet.builder();
        AtomicBoolean first = new AtomicBoolean(true);
        cycleDependencyTrace()
            .forEach(
                (dependencyRequest, resolvedBindings) -> {
                  // Skip the first request in the cycle and any synthetic requests.
                  if (first.getAndSet(false) || !dependencyRequest.requestElement().isPresent()) {
                    return;
                  }

                  if (breaksCycle(dependencyRequest.key().type(), dependencyRequest.kind())) {
                    providers.add(dependencyRequest);
                  } else if (!resolvedBindings.optionalBindingDeclarations().isEmpty()) {
                    /* Request resolved to a @BindsOptionalOf binding, so test the type inside the
                     * Optional. Optional<Provider or Lazy or Provider of Lazy or Map of Provider>
                     * breaks the cycle. */
                    TypeMirror optionalValueType =
                        OptionalType.from(dependencyRequest.key()).valueType();
                    RequestKind requestKind = getRequestKind(optionalValueType);
                    if (breaksCycle(extractKeyType(requestKind, optionalValueType), requestKind)) {
                      providers.add(dependencyRequest);
                    }
                  }
                });
        return providers.build();
      }

      private boolean breaksCycle(TypeMirror requestedType, RequestKind requestKind) {
        switch (requestKind) {
          case PROVIDER:
          case LAZY:
          case PROVIDER_OF_LAZY:
            return true;

          case INSTANCE:
            return MapType.isMap(requestedType)
                && MapType.from(requestedType).valuesAreTypeOf(Provider.class);

          default:
            return false;
        }
      }

      /**
       * Returns true if the current dependency request can be satisfied by a production binding.
       */
      private boolean dependencyRequestCanUseProduction() {
        if (atEntryPoint()) {
          return entryPointCanUseProduction(dependencyRequest().kind());
        } else {
          // The current request can be satisfied by a production binding if it's not from a
          // provision binding
          return !hasDependentProvisionBindings();
        }
      }

      /** Returns {@code true} if any provision bindings contain the latest request in the path. */
      private boolean hasDependentProvisionBindings() {
        return dependentBindings().stream().map(Binding::bindingType).anyMatch(isEqual(PROVISION));
      }

      private String formatCurrentDependencyRequestKey() {
        return dependencyRequest().key().toString();
      }
    }
  }

  ValidationReport<TypeElement> validate(BindingGraph graph) {
    ComponentValidation validation = new ComponentValidation(graph);
    validation.traverseComponents();
    return validation.buildReport();
  }

  /**
   * Append and format a list of indented component types (with their scope annotations)
   */
  private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
    for (TypeElement scopedComponent : types) {
      message.append(INDENT);
      for (Scope scope : scopesOf(scopedComponent)) {
        message.append(getReadableSource(scope)).append(' ');
      }
      message.append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
          .append('\n');
    }
  }

  /**
   * Returns a set of type elements containing only those found in the input set that have
   * a scoping annotation.
   */
  private ImmutableSet<TypeElement> scopedTypesIn(Set<TypeElement> types) {
    return types.stream().filter(type -> !scopesOf(type).isEmpty()).collect(toImmutableSet());
  }

  private String formatContributionType(ContributionType type) {
    switch (type) {
      case MAP:
        return "Map";
      case SET:
      case SET_VALUES:
        return "Set";
      case UNIQUE:
        return "Unique";
    }
    throw new AssertionError(type);
  }
}
