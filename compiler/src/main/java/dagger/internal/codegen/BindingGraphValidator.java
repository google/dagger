/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.squareup.javapoet.TypeName;
import dagger.Component;
import dagger.Lazy;
import dagger.MapKey;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import dagger.producers.ProductionComponent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElements;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Maps.filterKeys;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor.isOfKind;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind.PRODUCTION_SUBCOMPONENT;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind.SUBCOMPONENT;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ContributionBinding.indexMapBindingsByAnnotationType;
import static dagger.internal.codegen.ContributionBinding.indexMapBindingsByMapKey;
import static dagger.internal.codegen.ContributionBinding.Kind.IS_SYNTHETIC_KIND;
import static dagger.internal.codegen.ContributionBinding.Kind.SYNTHETIC_MULTIBOUND_MAP;
import static dagger.internal.codegen.ContributionType.indexByContributionType;
import static dagger.internal.codegen.ErrorMessages.CANNOT_INJECT_WILDCARD_TYPE;
import static dagger.internal.codegen.ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT;
import static dagger.internal.codegen.ErrorMessages.DUPLICATE_SIZE_LIMIT;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.ErrorMessages.MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.duplicateMapKeysError;
import static dagger.internal.codegen.ErrorMessages.inconsistentMapKeyAnnotationsError;
import static dagger.internal.codegen.ErrorMessages.nullableToNonNullable;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.Scope.reusableScope;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.tools.Diagnostic.Kind.ERROR;

public class BindingGraphValidator {

  private final Elements elements;
  private final Types types;
  private final CompilerOptions compilerOptions;
  private final InjectBindingRegistry injectBindingRegistry;
  private final HasSourceElementFormatter hasSourceElementFormatter;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final KeyFormatter keyFormatter;
  private final Key.Factory keyFactory;

  BindingGraphValidator(
      Elements elements,
      Types types,
      CompilerOptions compilerOptions,
      InjectBindingRegistry injectBindingRegistry,
      HasSourceElementFormatter hasSourceElementFormatter,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFormatter dependencyRequestFormatter,
      KeyFormatter keyFormatter,
      Key.Factory keyFactory) {
    this.elements = elements;
    this.types = types;
    this.compilerOptions = compilerOptions;
    this.injectBindingRegistry = injectBindingRegistry;
    this.hasSourceElementFormatter = hasSourceElementFormatter;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.keyFormatter = keyFormatter;
    this.keyFactory = keyFactory;
  }

  /** A dependency path from an entry point. */
  static final class DependencyPath {
    final Deque<ResolvedRequest> requestPath = new ArrayDeque<>();
    private final LinkedHashMultiset<BindingKey> keyPath = LinkedHashMultiset.create();
    private final Set<DependencyRequest> resolvedRequests = new HashSet<>();

    /** The entry point. */
    Element entryPointElement() {
      return requestPath.getFirst().request().requestElement();
    }

    /** The current dependency request, which is a transitive dependency of the entry point. */
    DependencyRequest currentDependencyRequest() {
      return requestPath.getLast().request();
    }

    /**
     * The resolved bindings for the {@linkplain #currentDependencyRequest() current dependency
     * request.
     */
    ResolvedBindings currentBinding() {
      return requestPath.getLast().binding();
    }

    /**
     * The binding that depends on the {@linkplain #currentDependencyRequest() current request}.
     *
     * @throws IllegalStateException if there are fewer than two requests in the path
     */
    ResolvedBindings previousBinding() {
      checkState(size() > 1);
      return Iterators.get(requestPath.descendingIterator(), 1).binding();
    }

    /**
     * {@code true} if there is a dependency cycle, which means that the
     * {@linkplain #currentDependencyRequest() current request}'s binding key occurs earlier in the
     * path.
     */
    boolean hasCycle() {
      return keyPath.count(currentDependencyRequest().bindingKey()) > 1;
    }

    /**
     * If there is a cycle, the segment of the path that represents the cycle. The first request's
     * and the last request's binding keys are equal. The last request is the
     * {@linkplain #currentDependencyRequest() current request}.
     *
     * @throws IllegalStateException if {@link #hasCycle()} is {@code false}
     */
    ImmutableList<ResolvedRequest> cycle() {
      checkState(hasCycle(), "no cycle");
      return FluentIterable.from(requestPath)
          .skip(indexOf(keyPath, Predicates.equalTo(currentDependencyRequest().bindingKey())))
          .toList();
    }

    /**
     * Makes {@code request} the current request. Be sure to call {@link #pop()} to back up to the
     * previous request in the path.
     */
    void push(ResolvedRequest request) {
      requestPath.addLast(request);
      keyPath.add(request.request().bindingKey());
    }

    /** Makes the previous request the current request. */
    void pop() {
      verify(keyPath.remove(requestPath.removeLast().request().bindingKey()));
    }

    /**
     * Adds the {@linkplain #currentDependencyRequest() current request} to a set of visited
     * requests, and returns {@code true} if the set didn't already contain it.
     */
    boolean visitCurrentRequest() {
      return resolvedRequests.add(currentDependencyRequest());
    }

    int size() {
      return requestPath.size();
    }

    /** The nonsynthetic dependency requests in this path, starting with the entry point. */
    FluentIterable<DependencyRequest> nonsyntheticRequests() {
      return FluentIterable.from(requestPath)
          .filter(Predicates.not(new PreviousBindingWasSynthetic()))
          .transform(REQUEST_FROM_RESOLVED_REQUEST);
    }
  }

  private final class Validation {
    final BindingGraph subject;
    final ValidationReport.Builder<TypeElement> reportBuilder;
    final Optional<Validation> parent;

    Validation(BindingGraph subject, Optional<Validation> parent) {
      this.subject = subject;
      this.reportBuilder =
          ValidationReport.about(subject.componentDescriptor().componentDefinitionType());
      this.parent = parent;
    }

    Validation(BindingGraph topLevelGraph) {
      this(topLevelGraph, Optional.<Validation>absent());
    }

    BindingGraph topLevelGraph() {
      return parent.isPresent() ? parent.get().topLevelGraph() : subject;
    }

    ValidationReport<TypeElement> buildReport() {
      return reportBuilder.build();
    }

    void validateSubgraph() {
      validateComponentScope();
      validateDependencyScopes();
      validateComponentHierarchy();
      validateBuilders();

      for (ComponentMethodDescriptor componentMethod :
           subject.componentDescriptor().componentMethods()) {
        Optional<DependencyRequest> entryPoint = componentMethod.dependencyRequest();
        if (entryPoint.isPresent()) {
          traverseRequest(entryPoint.get(), new DependencyPath());
        }
      }

      for (Map.Entry<ComponentMethodDescriptor, ComponentDescriptor> entry :
          filterKeys(
                  subject.componentDescriptor().subcomponents(),
                  isOfKind(SUBCOMPONENT, PRODUCTION_SUBCOMPONENT))
              .entrySet()) {
        validateSubcomponentFactoryMethod(
            entry.getKey().methodElement(), entry.getValue().componentDefinitionType());
      }

      for (BindingGraph subgraph : subject.subgraphs().values()) {
        Validation subgraphValidation = new Validation(subgraph, Optional.of(this));
        subgraphValidation.validateSubgraph();
        reportBuilder.addSubreport(subgraphValidation.buildReport());
      }
    }

    private void validateSubcomponentFactoryMethod(
        ExecutableElement factoryMethod, TypeElement subcomponentType) {
      BindingGraph subgraph = subject.subgraphs().get(factoryMethod);
      FluentIterable<TypeElement> missingModules =
          FluentIterable.from(subgraph.componentRequirements())
              .filter(not(in(subgraphFactoryMethodParameters(factoryMethod))))
              .filter(
                  new Predicate<TypeElement>() {
                    @Override
                    public boolean apply(TypeElement moduleType) {
                      return !componentCanMakeNewInstances(moduleType);
                    }
                  });
      if (!missingModules.isEmpty()) {
        reportBuilder.addError(
            String.format(
                "%s requires modules which have no visible default constructors. "
                    + "Add the following modules as parameters to this method: %s",
                subcomponentType.getQualifiedName(),
                Joiner.on(", ").join(missingModules.toSet())),
            factoryMethod);
      }
    }

    private ImmutableSet<TypeElement> subgraphFactoryMethodParameters(
        ExecutableElement factoryMethod) {
      DeclaredType componentType =
          asDeclared(subject.componentDescriptor().componentDefinitionType().asType());
      ExecutableType factoryMethodType =
          asExecutable(types.asMemberOf(componentType, factoryMethod));
      return asTypeElements(factoryMethodType.getParameterTypes());
    }

    /**
     * Traverse the resolved dependency requests, validating resolved bindings, and reporting any
     * cycles found.
     *
     * @param request the current dependency request
     */
    private void traverseRequest(DependencyRequest request, DependencyPath path) {
      path.push(ResolvedRequest.create(request, subject));
      try {
        if (path.hasCycle()) {
          reportCycle(path);
          return;
        }

        if (path.visitCurrentRequest()) {
          validateResolvedBinding(path);

          // Validate all dependencies within the component that owns the binding.
          for (Map.Entry<ComponentDescriptor, Collection<Binding>> entry :
              path.currentBinding().bindingsByComponent().asMap().entrySet()) {
            Validation validation = validationForComponent(entry.getKey());
            for (Binding binding : entry.getValue()) {
              for (DependencyRequest nextRequest : binding.implicitDependencies()) {
                validation.traverseRequest(nextRequest, path);
              }
            }
          }
        }
      } finally {
        path.pop();
      }
    }

    private Validation validationForComponent(ComponentDescriptor component) {
      if (component.equals(subject.componentDescriptor())) {
        return this;
      } else if (parent.isPresent()) {
        return parent.get().validationForComponent(component);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "unknown component %s within %s",
                component.componentDefinitionType(),
                subject.componentDescriptor().componentDefinitionType()));
      }
    }

    /**
     * Reports errors if the set of bindings resolved is inconsistent with the type of the binding.
     */
    private void validateResolvedBinding(DependencyPath path) {
      ResolvedBindings resolvedBinding = path.currentBinding();
      if (resolvedBinding.isEmpty()) {
        reportMissingBinding(path);
        return;
      }

      switch (resolvedBinding.bindingKey().kind()) {
        case CONTRIBUTION:
          if (Iterables.any(
              resolvedBinding.bindings(), BindingType.isOfType(BindingType.MEMBERS_INJECTION))) {
            // TODO(dpb): How could this ever happen, even in an invalid graph?
            throw new AssertionError(
                "contribution binding keys should never have members injection bindings");
          }
          validateNullability(
              path.currentDependencyRequest(), resolvedBinding.contributionBindings());
          if (resolvedBinding.contributionBindings().size() > 1) {
            reportDuplicateBindings(path);
            return;
          }
          ContributionBinding contributionBinding = resolvedBinding.contributionBinding();
          if (contributionBinding.bindingType().equals(BindingType.PRODUCTION)
              && doesPathRequireProvisionOnly(path)) {
            reportProviderMayNotDependOnProducer(path);
            return;
          }
          if (compilerOptions.usesProducers()) {
            Key productionImplementationExecutorKey =
                keyFactory.forProductionImplementationExecutor();
            // only forbid depending on the production executor if it's not the Dagger-specific
            // binding to the implementation
            if (!contributionBinding.key().equals(productionImplementationExecutorKey)) {
              Key productionExecutorKey = keyFactory.forProductionExecutor();
              for (DependencyRequest request : contributionBinding.dependencies()) {
                if (request.key().equals(productionExecutorKey)
                    || request.key().equals(productionImplementationExecutorKey)) {
                  reportDependsOnProductionExecutor(path);
                  return;
                }
              }
            }
          }
          if (contributionBinding.bindingKind().equals(SYNTHETIC_MULTIBOUND_MAP)) {
            ImmutableSet<ContributionBinding> multibindings =
                inlineSyntheticContributions(resolvedBinding).contributionBindings();
            validateMapKeySet(path, multibindings);
            validateMapKeyAnnotationTypes(path, multibindings);
          }
          break;
        case MEMBERS_INJECTION:
          if (!Iterables.all(
              resolvedBinding.bindings(), BindingType.isOfType(BindingType.MEMBERS_INJECTION))) {
            // TODO(dpb): How could this ever happen, even in an invalid graph?
            throw new AssertionError(
                "members injection binding keys should never have contribution bindings");
          }
          if (resolvedBinding.bindings().size() > 1) {
            reportDuplicateBindings(path);
            return;
          }
          validateMembersInjectionBinding(getOnlyElement(resolvedBinding.bindings()), path);
          return;
        default:
          throw new AssertionError();
      }
    }

    /**
     * Returns an object that contains all the same bindings as {@code resolvedBindings}, except
     * that any synthetic {@link ContributionBinding}s are replaced by the contribution bindings and
     * multibinding declarations of their dependencies.
     *
     * <p>For example, if:
     *
     * <ul>
     * <li>The bindings for {@code key1} are {@code A} and {@code B}, with multibinding declaration
     *     {@code X}.
     * <li>{@code B} is a synthetic binding with a dependency on {@code key2}.
     * <li>The bindings for {@code key2} are {@code C} and {@code D}, with multibinding declaration
     *     {@code Y}.
     * </ul>
     *
     * then {@code inlineSyntheticBindings(bindingsForKey1)} has bindings {@code A}, {@code C}, and
     * {@code D}, with multibinding declarations {@code X} and {@code Y}.
     *
     * <p>The replacement is repeated until none of the bindings are synthetic.
     */
    private ResolvedBindings inlineSyntheticContributions(ResolvedBindings resolvedBinding) {
      if (!FluentIterable.from(resolvedBinding.contributionBindings())
          .transform(ContributionBinding.KIND)
          .anyMatch(IS_SYNTHETIC_KIND)) {
        return resolvedBinding;
      }

      ImmutableSetMultimap.Builder<ComponentDescriptor, ContributionBinding> contributions =
          ImmutableSetMultimap.builder();
      ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
          ImmutableSet.builder();

      Queue<ResolvedBindings> queue = new ArrayDeque<>();
      queue.add(resolvedBinding);

      for (ResolvedBindings queued = queue.poll(); queued != null; queued = queue.poll()) {
        multibindingDeclarations.addAll(queued.multibindingDeclarations());
        for (Map.Entry<ComponentDescriptor, ContributionBinding> bindingEntry :
            queued.allContributionBindings().entries()) {
          BindingGraph owningGraph = validationForComponent(bindingEntry.getKey()).subject;
          ContributionBinding binding = bindingEntry.getValue();
          if (binding.isSyntheticBinding()) {
            for (DependencyRequest dependency : binding.dependencies()) {
              queue.add(owningGraph.resolvedBindings().get(dependency.bindingKey()));
            }
          } else {
            contributions.put(bindingEntry);
          }
        }
      }
      return ResolvedBindings.forContributionBindings(
          resolvedBinding.bindingKey(),
          resolvedBinding.owningComponent(),
          contributions.build(),
          multibindingDeclarations.build());
    }

    private ImmutableListMultimap<ContributionType, HasSourceElement> declarationsByType(
        ResolvedBindings resolvedBinding) {
      ResolvedBindings inlined = inlineSyntheticContributions(resolvedBinding);
      return new ImmutableListMultimap.Builder<ContributionType, HasSourceElement>()
          .putAll(indexByContributionType(inlined.contributionBindings()))
          .putAll(indexByContributionType(inlined.multibindingDeclarations()))
          .build();
    }

    /** Ensures that if the request isn't nullable, then each contribution is also not nullable. */
    private void validateNullability(DependencyRequest request, Set<ContributionBinding> bindings) {
      if (request.isNullable()) {
        return;
      }

      // Note: the method signature will include the @Nullable in it!
      /* TODO(sameb): Sometimes javac doesn't include the Element in its output.
       * (Maybe this happens if the code was already compiled before this point?)
       * ... we manually print out the request in that case, otherwise the error
       * message is kind of useless. */
      String typeName = TypeName.get(request.key().type()).toString();

      for (ContributionBinding binding : bindings) {
        if (binding.nullableType().isPresent()) {
          reportBuilder.addItem(
              nullableToNonNullable(typeName, hasSourceElementFormatter.format(binding))
                  + "\n at: "
                  + dependencyRequestFormatter.format(request),
              compilerOptions.nullableValidationKind(),
              request.requestElement());
        }
      }
    }

    /**
     * Reports errors if {@code mapBindings} has more than one binding for the same map key.
     */
    private void validateMapKeySet(DependencyPath path, Set<ContributionBinding> mapBindings) {
      for (Collection<ContributionBinding> mapBindingsForMapKey :
          indexMapBindingsByMapKey(mapBindings).asMap().values()) {
        if (mapBindingsForMapKey.size() > 1) {
          reportDuplicateMapKeys(path, mapBindingsForMapKey);
        }
      }
    }

    /**
     * Reports errors if {@code mapBindings} uses more than one {@link MapKey} annotation type.
     */
    private void validateMapKeyAnnotationTypes(
        DependencyPath path, Set<ContributionBinding> contributionBindings) {
      ImmutableSetMultimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
          mapBindingsByAnnotationType = indexMapBindingsByAnnotationType(contributionBindings);
      if (mapBindingsByAnnotationType.keySet().size() > 1) {
        reportInconsistentMapKeyAnnotations(path, mapBindingsByAnnotationType);
      }
    }

    /**
     * Reports errors if a members injection binding is invalid.
     */
    private void validateMembersInjectionBinding(Binding binding, final DependencyPath path) {
      binding
          .key()
          .type()
          .accept(
              new SimpleTypeVisitor6<Void, Void>() {
                @Override
                protected Void defaultAction(TypeMirror e, Void p) {
                  reportBuilder.addError(
                      "Invalid members injection request.",
                      path.currentDependencyRequest().requestElement());
                  return null;
                }

                @Override
                public Void visitDeclared(DeclaredType type, Void ignored) {
                  // If the key has type arguments, validate that each type argument is declared.
                  // Otherwise the type argument may be a wildcard (or other type), and we can't
                  // resolve that to actual types.  If the arg was an array, validate the type
                  // of the array.
                  for (TypeMirror arg : type.getTypeArguments()) {
                    boolean declared;
                    switch (arg.getKind()) {
                      case ARRAY:
                        declared =
                            MoreTypes.asArray(arg)
                                .getComponentType()
                                .accept(
                                    new SimpleTypeVisitor6<Boolean, Void>() {
                                      @Override
                                      protected Boolean defaultAction(TypeMirror e, Void p) {
                                        return false;
                                      }

                                      @Override
                                      public Boolean visitDeclared(DeclaredType t, Void p) {
                                        for (TypeMirror arg : t.getTypeArguments()) {
                                          if (!arg.accept(this, null)) {
                                            return false;
                                          }
                                        }
                                        return true;
                                      }

                                      @Override
                                      public Boolean visitArray(ArrayType t, Void p) {
                                        return t.getComponentType().accept(this, null);
                                      }

                                      @Override
                                      public Boolean visitPrimitive(PrimitiveType t, Void p) {
                                        return true;
                                      }
                                    },
                                    null);
                        break;
                      case DECLARED:
                        declared = true;
                        break;
                      default:
                        declared = false;
                    }
                    if (!declared) {
                      reportBuilder.addError(
                          String.format(
                              MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE,
                              arg.toString(),
                              type.toString(),
                              dependencyRequestFormatter.toDependencyTrace(path)),
                          path.entryPointElement());
                      return null;
                    }
                  }

                  TypeElement element = MoreElements.asType(type.asElement());
                  // Also validate that the key is not the erasure of a generic type.
                  // If it is, that means the user referred to Foo<T> as just 'Foo',
                  // which we don't allow.  (This is a judgement call -- we *could*
                  // allow it and instantiate the type bounds... but we don't.)
                  if (!MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
                      && types.isSameType(types.erasure(element.asType()), type)) {
                    reportBuilder.addError(
                        String.format(
                            ErrorMessages.MEMBERS_INJECTION_WITH_RAW_TYPE,
                            type.toString(),
                            dependencyRequestFormatter.toDependencyTrace(path)),
                        path.entryPointElement());
                  }
                  return null;
                }
              },
              null);
    }

    /**
     * Validates that component dependencies do not form a cycle.
     */
    private void validateComponentHierarchy() {
      ComponentDescriptor descriptor = subject.componentDescriptor();
      TypeElement componentType = descriptor.componentDefinitionType();
      validateComponentHierarchy(componentType, componentType, new ArrayDeque<TypeElement>());
    }

    /**
     * Recursive method to validate that component dependencies do not form a cycle.
     */
    private void validateComponentHierarchy(
        TypeElement rootComponent,
        TypeElement componentType,
        Deque<TypeElement> componentStack) {

      if (componentStack.contains(componentType)) {
        // Current component has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(rootComponent.getQualifiedName());
        message.append(" contains a cycle in its component dependencies:\n");
        componentStack.push(componentType);
        appendIndentedComponentsList(message, componentStack);
        componentStack.pop();
        reportBuilder.addItem(
            message.toString(),
            compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
            rootComponent,
            getAnnotationMirror(rootComponent, Component.class).get());
      } else {
        Optional<AnnotationMirror> componentAnnotation =
            getAnnotationMirror(componentType, Component.class);
        if (componentAnnotation.isPresent()) {
          componentStack.push(componentType);

          ImmutableSet<TypeElement> dependencies =
              MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get()));
          for (TypeElement dependency : dependencies) {
            validateComponentHierarchy(rootComponent, dependency, componentStack);
          }

          componentStack.pop();
        }
      }
    }

    /**
     * Validates that among the dependencies are at most one scoped dependency,
     * that there are no cycles within the scoping chain, and that singleton
     * components have no scoped dependencies.
     */
    private void validateDependencyScopes() {
      ComponentDescriptor descriptor = subject.componentDescriptor();
      ImmutableSet<Scope> scopes = descriptor.scopes();
      ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(descriptor.dependencies());
      if (!scopes.isEmpty()) {
        Scope singletonScope = Scope.singletonScope(elements);
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()
            && scopes.contains(singletonScope)) {
          // Singleton is a special-case representing the longest lifetime, and therefore
          // @Singleton components may not depend on scoped components
          if (!scopedDependencies.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            reportBuilder.addItem(
                message.toString(),
                compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
                descriptor.componentDefinitionType(),
                descriptor.componentAnnotation());
          }
        } else if (scopedDependencies.size() > 1) {
          // Scoped components may depend on at most one scoped component.
          StringBuilder message = new StringBuilder();
          for (Scope scope : scopes) {
            message.append(scope.getReadableSource()).append(' ');
          }
          message
              .append(descriptor.componentDefinitionType().getQualifiedName())
              .append(" depends on more than one scoped component:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportBuilder.addError(
              message.toString(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!compilerOptions.scopeCycleValidationType().equals(ValidationType.NONE)) {
            validateScopeHierarchy(descriptor.componentDefinitionType(),
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
          reportBuilder.addError(
              message.toString(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        }
      }
    }

    private void validateBuilders() {
      ComponentDescriptor componentDesc = subject.componentDescriptor();
      if (!componentDesc.builderSpec().isPresent()) {
        // If no builder, nothing to validate.
        return;
      }

      Set<TypeElement> availableDependencies = subject.availableDependencies();
      Set<TypeElement> requiredDependencies =
          Sets.filter(
              availableDependencies,
              new Predicate<TypeElement>() {
                @Override
                public boolean apply(TypeElement input) {
                  return !Util.componentCanMakeNewInstances(input);
                }
              });
      final BuilderSpec spec = componentDesc.builderSpec().get();
      Map<TypeElement, ExecutableElement> allSetters = spec.methodMap();

      ErrorMessages.ComponentBuilderMessages msgs =
          ErrorMessages.builderMsgsFor(subject.componentDescriptor().kind());
      Set<TypeElement> extraSetters = Sets.difference(allSetters.keySet(), availableDependencies);
      if (!extraSetters.isEmpty()) {
        Collection<ExecutableElement> excessMethods =
            Maps.filterKeys(allSetters, Predicates.in(extraSetters)).values();
        Iterable<String> formatted = FluentIterable.from(excessMethods).transform(
            new Function<ExecutableElement, String>() {
              @Override public String apply(ExecutableElement input) {
                return methodSignatureFormatter.format(input,
                    Optional.of(MoreTypes.asDeclared(spec.builderDefinitionType().asType())));
              }});
        reportBuilder.addError(
            String.format(msgs.extraSetters(), formatted), spec.builderDefinitionType());
      }

      Set<TypeElement> missingSetters = Sets.difference(requiredDependencies, allSetters.keySet());
      if (!missingSetters.isEmpty()) {
        reportBuilder.addError(
            String.format(msgs.missingSetters(), missingSetters), spec.builderDefinitionType());
      }
    }

    /**
     * Validates that scopes do not participate in a scoping cycle - that is to say, scoped
     * components are in a hierarchical relationship terminating with Singleton.
     *
     * <p>As a side-effect, this means scoped components cannot have a dependency cycle between
     * themselves, since a component's presence within its own dependency path implies a cyclical
     * relationship between scopes. However, cycles in component dependencies are explicitly
     * checked in {@link #validateComponentHierarchy()}.
     */
    private void validateScopeHierarchy(TypeElement rootComponent,
        TypeElement componentType,
        Deque<ImmutableSet<Scope>> scopeStack,
        Deque<TypeElement> scopedDependencyStack) {
      ImmutableSet<Scope> scopes = Scope.scopesOf(componentType);
      if (stackOverlaps(scopeStack, scopes)) {
        scopedDependencyStack.push(componentType);
        // Current scope has already appeared in the component chain.
        StringBuilder message = new StringBuilder();
        message.append(rootComponent.getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (compilerOptions.scopeCycleValidationType().diagnosticKind().isPresent()) {
          reportBuilder.addItem(
              message.toString(),
              compilerOptions.scopeCycleValidationType().diagnosticKind().get(),
              rootComponent,
              getAnnotationMirror(rootComponent, Component.class)
                  .or(getAnnotationMirror(rootComponent, ProductionComponent.class))
                  .get());
        }
        scopedDependencyStack.pop();
      } else {
        // TODO(beder): transitively check scopes of production components too.
        Optional<AnnotationMirror> componentAnnotation =
            getAnnotationMirror(componentType, Component.class);
        if (componentAnnotation.isPresent()) {
          ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(
              MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get())));
          if (scopedDependencies.size() == 1) {
            // empty can be ignored (base-case), and > 1 is a different error reported separately.
            scopeStack.push(scopes);
            scopedDependencyStack.push(componentType);
            validateScopeHierarchy(rootComponent, getOnlyElement(scopedDependencies),
                scopeStack, scopedDependencyStack);
            scopedDependencyStack.pop();
            scopeStack.pop();
          }
        } // else: we skip component dependencies which are not components
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
     * Validates that the scope (if any) of this component are compatible with the scopes of the
     * bindings available in this component
     */
    void validateComponentScope() {
      ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings = subject.resolvedBindings();
      ImmutableSet<Scope> componentScopes = subject.componentDescriptor().scopes();
      ImmutableSet.Builder<String> incompatiblyScopedMethodsBuilder = ImmutableSet.builder();
      Scope reusableScope = reusableScope(elements);
      for (ResolvedBindings bindings : resolvedBindings.values()) {
        for (ContributionBinding contributionBinding : bindings.ownedContributionBindings()) {
          Optional<Scope> bindingScope = contributionBinding.scope();
          if (bindingScope.isPresent()
              && !bindingScope.get().equals(reusableScope)
              && !componentScopes.contains(bindingScope.get())) {
            // Scoped components cannot reference bindings to @Provides methods or @Inject
            // types decorated by a different scope annotation. Unscoped components cannot
            // reference to scoped @Provides methods or @Inject types decorated by any
            // scope annotation.
            switch (contributionBinding.bindingKind()) {
              case SYNTHETIC_DELEGATE_BINDING:
              case PROVISION:
                ExecutableElement provisionMethod =
                    MoreElements.asExecutable(contributionBinding.bindingElement());
                incompatiblyScopedMethodsBuilder.add(
                    methodSignatureFormatter.format(provisionMethod));
                break;
              case INJECTION:
                incompatiblyScopedMethodsBuilder.add(
                    bindingScope.get().getReadableSource()
                        + " class "
                        + contributionBinding.bindingTypeElement().getQualifiedName());
                break;
              default:
                throw new IllegalStateException();
            }
          }
        }
      }

      ImmutableSet<String> incompatiblyScopedMethods = incompatiblyScopedMethodsBuilder.build();
      if (!incompatiblyScopedMethods.isEmpty()) {
        TypeElement componentType = subject.componentDescriptor().componentDefinitionType();
        StringBuilder message = new StringBuilder(componentType.getQualifiedName());
        if (!componentScopes.isEmpty()) {
          message.append(" scoped with ");
          for (Scope scope : componentScopes) {
            message.append(scope.getReadableSource()).append(' ');
          }
          message.append("may not reference bindings with different scopes:\n");
        } else {
          message.append(" (unscoped) may not reference scoped bindings:\n");
        }
        for (String method : incompatiblyScopedMethods) {
          message.append(ErrorMessages.INDENT).append(method).append("\n");
        }
        reportBuilder.addError(
            message.toString(), componentType, subject.componentDescriptor().componentAnnotation());
      }
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportProviderMayNotDependOnProducer(DependencyPath path) {
      StringBuilder errorMessage = new StringBuilder();
      if (path.size() == 1) {
        new Formatter(errorMessage)
            .format(
                ErrorMessages.PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
                formatRootRequestKey(path));
      } else {
        ImmutableSet<? extends Binding> dependentProvisions =
            provisionsDependingOnLatestRequest(path);
        // TODO(beder): Consider displaying all dependent provisions in the error message. If we do
        // that, should we display all productions that depend on them also?
        new Formatter(errorMessage).format(ErrorMessages.PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
            keyFormatter.format(dependentProvisions.iterator().next().key()));
      }
      reportBuilder.addError(errorMessage.toString(), path.entryPointElement());
    }

    /**
     * Descriptive portion of the error message for when the given request has no binding.
     * Currently, the only other portions of the message are the dependency path, line number and
     * filename. Not static because it uses the instance field types.
     */
    private StringBuilder requiresErrorMessageBase(DependencyPath path) {
      Key key = path.currentDependencyRequest().key();
      String requiresErrorMessageFormat;
      // TODO(dpb): Check for wildcard injection somewhere else first?
      if (key.type().getKind().equals(TypeKind.WILDCARD)) {
        requiresErrorMessageFormat = CANNOT_INJECT_WILDCARD_TYPE;
      } else {
        boolean requiresProvision = doesPathRequireProvisionOnly(path);
        if (!key.isValidImplicitProvisionKey(types)) {
          requiresErrorMessageFormat = requiresProvision
              ? REQUIRES_PROVIDER_FORMAT
              : REQUIRES_PROVIDER_OR_PRODUCER_FORMAT;
        } else {
          requiresErrorMessageFormat = requiresProvision
              ? REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT
              : REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
        }
      }
      StringBuilder errorMessage = new StringBuilder(
          String.format(requiresErrorMessageFormat, keyFormatter.format(key)));
      if (key.isValidMembersInjectionKey()) {
        Optional<MembersInjectionBinding> membersInjectionBinding =
            injectBindingRegistry.getOrFindMembersInjectionBinding(key);
        if (membersInjectionBinding.isPresent()
            && !membersInjectionBinding.get().injectionSites().isEmpty()) {
          errorMessage.append(" ");
          errorMessage.append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
        }
      }
      return errorMessage.append('\n');
    }

    private void reportMissingBinding(DependencyPath path) {
      StringBuilder errorMessage =
          requiresErrorMessageBase(path).append(dependencyRequestFormatter.toDependencyTrace(path));
      for (String suggestion :
          MissingBindingSuggestions.forKey(
              topLevelGraph(), path.currentDependencyRequest().bindingKey())) {
        errorMessage.append('\n').append(suggestion);
      }
      reportBuilder.addError(errorMessage.toString(), path.entryPointElement());
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportDependsOnProductionExecutor(DependencyPath path) {
      StringBuilder builder = new StringBuilder();
      new Formatter(builder)
          .format(ErrorMessages.DEPENDS_ON_PRODUCTION_EXECUTOR_FORMAT, formatRootRequestKey(path));
      reportBuilder.addError(builder.toString(), path.entryPointElement());
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportDuplicateBindings(DependencyPath path) {
      ResolvedBindings resolvedBinding = path.currentBinding();
      if (FluentIterable.from(resolvedBinding.contributionBindings())
          .transform(ContributionBinding.KIND)
          .anyMatch(IS_SYNTHETIC_KIND)) {
        reportMultipleBindingTypes(path);
        return;
      }
      StringBuilder builder = new StringBuilder();
      new Formatter(builder)
          .format(ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT, formatRootRequestKey(path));
      ImmutableSet<ContributionBinding> duplicateBindings =
          inlineSyntheticContributions(resolvedBinding).contributionBindings();
      hasSourceElementFormatter.formatIndentedList(
          builder, duplicateBindings, 1, DUPLICATE_SIZE_LIMIT);
      owningReportBuilder(duplicateBindings).addError(builder.toString(), path.entryPointElement());
    }

    /**
     * Returns the report builder for the rootmost component that contains any of the duplicate
     * bindings.
     */
    private ValidationReport.Builder<TypeElement> owningReportBuilder(
        Iterable<ContributionBinding> duplicateBindings) {
      ImmutableSet.Builder<ComponentDescriptor> owningComponentsBuilder = ImmutableSet.builder();
      for (ContributionBinding binding : duplicateBindings) {
        BindingKey bindingKey = BindingKey.create(BindingKey.Kind.CONTRIBUTION, binding.key());
        ResolvedBindings resolvedBindings = subject.resolvedBindings().get(bindingKey);
        owningComponentsBuilder.addAll(
            resolvedBindings.allContributionBindings().inverse().get(binding));
      }
      ImmutableSet<ComponentDescriptor> owningComponents = owningComponentsBuilder.build();
      for (Validation validation : validationPath()) {
        if (owningComponents.contains(validation.subject.componentDescriptor())) {
          return validation.reportBuilder;
        }
      }
      throw new AssertionError(
          "cannot find owning component for duplicate bindings: " + duplicateBindings);
    }

    /**
     * The path from the {@link Validation} of the root graph down to this {@link Validation}.
     */
    private ImmutableList<Validation> validationPath() {
      ImmutableList.Builder<Validation> validationPath = ImmutableList.builder();
      for (Optional<Validation> validation = Optional.of(this);
          validation.isPresent();
          validation = validation.get().parent) {
        validationPath.add(validation.get());
      }
      return validationPath.build().reverse();
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportMultipleBindingTypes(DependencyPath path) {
      StringBuilder builder = new StringBuilder();
      new Formatter(builder)
          .format(ErrorMessages.MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT, formatRootRequestKey(path));
      ResolvedBindings resolvedBinding = path.currentBinding();
      ImmutableListMultimap<ContributionType, HasSourceElement> declarationsByType =
          declarationsByType(resolvedBinding);
      verify(
          declarationsByType.keySet().size() > 1,
          "expected multiple binding types for %s: %s",
          resolvedBinding.bindingKey(),
          declarationsByType);
      for (ContributionType type :
          Ordering.natural().immutableSortedCopy(declarationsByType.keySet())) {
        builder.append(INDENT);
        builder.append(formatContributionType(type));
        builder.append(" bindings and declarations:");
        hasSourceElementFormatter.formatIndentedList(
            builder, declarationsByType.get(type), 2, DUPLICATE_SIZE_LIMIT);
        builder.append('\n');
      }
      reportBuilder.addError(builder.toString(), path.entryPointElement());
    }

    private void reportDuplicateMapKeys(
        DependencyPath path, Collection<ContributionBinding> mapBindings) {
      StringBuilder builder = new StringBuilder();
      builder.append(duplicateMapKeysError(formatRootRequestKey(path)));
      hasSourceElementFormatter.formatIndentedList(builder, mapBindings, 1, DUPLICATE_SIZE_LIMIT);
      reportBuilder.addError(builder.toString(), path.entryPointElement());
    }

    private void reportInconsistentMapKeyAnnotations(
        DependencyPath path,
        Multimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
            mapBindingsByAnnotationType) {
      StringBuilder builder =
          new StringBuilder(inconsistentMapKeyAnnotationsError(formatRootRequestKey(path)));
      for (Map.Entry<Equivalence.Wrapper<DeclaredType>, Collection<ContributionBinding>> entry :
          mapBindingsByAnnotationType.asMap().entrySet()) {
        DeclaredType annotationType = entry.getKey().get();
        Collection<ContributionBinding> bindings = entry.getValue();

        builder
            .append('\n')
            .append(INDENT)
            .append(annotationType)
            .append(':');

        hasSourceElementFormatter.formatIndentedList(builder, bindings, 2, DUPLICATE_SIZE_LIMIT);
      }
      reportBuilder.addError(builder.toString(), path.entryPointElement());
    }

    private void reportCycle(DependencyPath path) {
      if (!providersBreakingCycle(path.cycle()).isEmpty()) {
        return;
      }
      // TODO(cgruber): Provide a hint for the start and end of the cycle.
      TypeElement componentType =
          MoreElements.asType(path.entryPointElement().getEnclosingElement());
      reportBuilder.addItem(
          String.format(
              CONTAINS_DEPENDENCY_CYCLE_FORMAT,
              componentType.getQualifiedName(),
              path.entryPointElement().getSimpleName(),
              dependencyRequestFormatter.toDependencyTrace(path)),
          ERROR,
          path.entryPointElement());
    }

    /**
     * Returns any steps in a dependency cycle that "break" the cycle. These are any
     * {@link Provider}, {@link Lazy}, or {@code Map<K, Provider<V>>} requests after the first
     * request in the cycle.
     *
     * <p>If an implicit {@link Provider} dependency on {@code Map<K, Provider<V>>} is immediately
     * preceded by a dependency on {@code Map<K, V>}, which means that the map's {@link Provider}s'
     * {@link Provider#get() get()} methods are called during provision and so the cycle is not
     * really broken.
     */
    private ImmutableSet<DependencyRequest> providersBreakingCycle(
        ImmutableList<ResolvedRequest> cycle) {
      ImmutableSet.Builder<DependencyRequest> providers = ImmutableSet.builder();
      for (int i = 1; i < cycle.size(); i++) {
        DependencyRequest dependencyRequest = cycle.get(i).request();
        switch (dependencyRequest.kind()) {
          case PROVIDER:
            // TODO(dpb): Just exclude requests from synthetic bindings.
            if (isDependencyOfSyntheticMap(dependencyRequest, cycle.get(i - 1).request())) {
              i++; // Skip the Provider requests in the Map<K, Provider<V>> too.
            } else {
              providers.add(dependencyRequest);
            }
            break;

          case LAZY:
            providers.add(dependencyRequest);
            break;

          case INSTANCE:
            TypeMirror type = dependencyRequest.key().type();
            if (MapType.isMap(type) && MapType.from(type).valuesAreTypeOf(Provider.class)) {
              providers.add(dependencyRequest);
            }
            break;

          default:
            break;
        }
      }
      return providers.build();
    }

    /**
     * Returns {@code true} if {@code request} is a request for {@code Map<K, Provider<V>>} or
     * {@code Map<K, Producer<V>>} from a synthetic binding for {@code Map<K, V>} or
     * {@code Map<K, Produced<V>>}.
     */
    // TODO(dpb): Make this check more explicit.
    private boolean isDependencyOfSyntheticMap(
        DependencyRequest request, DependencyRequest requestForPreviousBinding) {
      // Synthetic map dependencies share the same request element as the previous request.
      return request.requestElement().equals(requestForPreviousBinding.requestElement())
          && Sets.union(
                  keyFactory.implicitMapProviderKeyFrom(requestForPreviousBinding.key()).asSet(),
                  keyFactory.implicitMapProducerKeyFrom(requestForPreviousBinding.key()).asSet())
              .contains(request.key());
    }
  }

  ValidationReport<TypeElement> validate(BindingGraph subject) {
    Validation validation = new Validation(subject);
    validation.validateSubgraph();
    return validation.buildReport();
  }

  /**
   * Append and format a list of indented component types (with their scope annotations)
   */
  private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
    for (TypeElement scopedComponent : types) {
      message.append(INDENT);
      for (Scope scope : Scope.scopesOf(scopedComponent)) {
        message.append(scope.getReadableSource()).append(' ');
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
    return FluentIterable.from(types).filter(new Predicate<TypeElement>() {
      @Override public boolean apply(TypeElement input) {
        return !Scope.scopesOf(input).isEmpty();
      }
    }).toSet();
  }

  /**
   * Returns whether the given dependency path would require the most recent request to be resolved
   * by only provision bindings.
   */
  private boolean doesPathRequireProvisionOnly(DependencyPath path) {
    if (path.size() == 1) {
      // if this is an entry-point, then we check the request
      switch (path.currentDependencyRequest().kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
        case MEMBERS_INJECTOR:
          return true;
        case PRODUCER:
        case PRODUCED:
        case FUTURE:
          return false;
        default:
          throw new AssertionError();
      }
    }
    // otherwise, the second-most-recent bindings determine whether the most recent one must be a
    // provision
    return !provisionsDependingOnLatestRequest(path).isEmpty();
  }

  /**
   * Returns any provision bindings resolved for the second-most-recent request in the given path;
   * that is, returns those provision bindings that depend on the latest request in the path.
   */
  private ImmutableSet<? extends Binding> provisionsDependingOnLatestRequest(
      final DependencyPath path) {
    return FluentIterable.from(path.previousBinding().bindings())
        .filter(BindingType.isOfType(BindingType.PROVISION))
        .filter(
            new Predicate<Binding>() {
              @Override
              public boolean apply(Binding binding) {
                return binding.implicitDependencies().contains(path.currentDependencyRequest());
              }
            })
        .toSet();
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
      default:
        throw new IllegalStateException("Unknown binding type: " + type);
    }
  }

  private String formatRootRequestKey(DependencyPath path) {
    return keyFormatter.format(path.currentDependencyRequest().key());
  }

  @AutoValue
  abstract static class ResolvedRequest {
    abstract DependencyRequest request();
    abstract ResolvedBindings binding();

    static ResolvedRequest create(DependencyRequest request, BindingGraph graph) {
      BindingKey bindingKey = request.bindingKey();
      ResolvedBindings resolvedBindings = graph.resolvedBindings().get(bindingKey);
      return new AutoValue_BindingGraphValidator_ResolvedRequest(
          request,
          resolvedBindings == null
              ? ResolvedBindings.noBindings(bindingKey, graph.componentDescriptor())
              : resolvedBindings);
    }
  }

  private static final Function<ResolvedRequest, DependencyRequest> REQUEST_FROM_RESOLVED_REQUEST =
      new Function<ResolvedRequest, DependencyRequest>() {
        @Override
        public DependencyRequest apply(ResolvedRequest resolvedRequest) {
          return resolvedRequest.request();
        }
      };

  private static final class PreviousBindingWasSynthetic implements Predicate<ResolvedRequest> {
    private ResolvedBindings previousBinding;

    @Override
    public boolean apply(ResolvedRequest resolvedRequest) {
      boolean previousBindingWasSynthetic =
          previousBinding != null && previousBinding.isSyntheticContribution();
      previousBinding = resolvedRequest.binding();
      return previousBindingWasSynthetic;
    }
  }
}
