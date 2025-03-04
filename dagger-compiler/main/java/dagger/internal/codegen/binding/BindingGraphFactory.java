/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.binding.LegacyBindingGraphFactory.useLegacyBindingGraphFactory;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.extension.DaggerGraphs.unreachableNodes;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.model.BindingKind.ASSISTED_INJECTION;
import static dagger.internal.codegen.model.BindingKind.DELEGATE;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.OPTIONAL;
import static dagger.internal.codegen.model.BindingKind.SUBCOMPONENT_CREATOR;
import static dagger.internal.codegen.model.RequestKind.MEMBERS_INJECTION;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.base.Keys;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.TarjanSCCs;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.MissingBinding;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/** A factory for {@link BindingGraph} objects. */
public final class BindingGraphFactory {
  private final LegacyBindingGraphFactory legacyBindingGraphFactory;
  private final InjectBindingRegistry injectBindingRegistry;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final BindingNode.Factory bindingNodeFactory;
  private final ComponentDeclarations.Factory componentDeclarationsFactory;
  private final CompilerOptions compilerOptions;

  @Inject
  BindingGraphFactory(
      LegacyBindingGraphFactory legacyBindingGraphFactory,
      InjectBindingRegistry injectBindingRegistry,
      KeyFactory keyFactory,
      BindingFactory bindingFactory,
      BindingNode.Factory bindingNodeFactory,
      ComponentDeclarations.Factory componentDeclarationsFactory,
      CompilerOptions compilerOptions) {
    this.legacyBindingGraphFactory = legacyBindingGraphFactory;
    this.injectBindingRegistry = injectBindingRegistry;
    this.keyFactory = keyFactory;
    this.bindingFactory = bindingFactory;
    this.bindingNodeFactory = bindingNodeFactory;
    this.componentDeclarationsFactory = componentDeclarationsFactory;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Creates a binding graph for a component.
   *
   * @param createFullBindingGraph if {@code true}, the binding graph will include all bindings;
   *     otherwise it will include only bindings reachable from at least one entry point
   */
  public BindingGraph create(
      ComponentDescriptor componentDescriptor, boolean createFullBindingGraph) {
    return useLegacyBindingGraphFactory(compilerOptions, componentDescriptor)
        ? legacyBindingGraphFactory.create(componentDescriptor, createFullBindingGraph)
        : createBindingGraph(componentDescriptor, createFullBindingGraph);
  }

  private BindingGraph createBindingGraph(
      ComponentDescriptor componentDescriptor, boolean createFullBindingGraph) {
    Resolver resolver = new Resolver(componentDescriptor);
    resolver.resolve(createFullBindingGraph);

    MutableNetwork<Node, Edge> network = resolver.network;
    if (!createFullBindingGraph) {
      unreachableNodes(network.asGraph(), resolver.componentNode).forEach(network::removeNode);
    }

    network = BindingGraphTransformations.withFixedBindingTypes(network);
    return BindingGraph.create(
        ImmutableNetwork.copyOf(network),
        createFullBindingGraph);
  }

  private final class Resolver {
    final ComponentPath componentPath;
    final Optional<Resolver> parentResolver;
    final ComponentNode componentNode;
    final ComponentDescriptor componentDescriptor;
    final ComponentDeclarations declarations;
    final MutableNetwork<Node, Edge> network;
    final Map<Key, ResolvedBindings> resolvedContributionBindings = new LinkedHashMap<>();
    final Map<Key, ResolvedBindings> resolvedMembersInjectionBindings = new LinkedHashMap<>();
    final RequiresResolutionChecker requiresResolutionChecker = new RequiresResolutionChecker();
    final Queue<ComponentDescriptor> subcomponentsToResolve = new ArrayDeque<>();

    Resolver(ComponentDescriptor componentDescriptor) {
      this(Optional.empty(), componentDescriptor);
    }

    Resolver(Resolver parentResolver, ComponentDescriptor componentDescriptor) {
      this(Optional.of(parentResolver), componentDescriptor);
    }

    private Resolver(Optional<Resolver> parentResolver, ComponentDescriptor componentDescriptor) {
      this.parentResolver = parentResolver;
      this.componentDescriptor = checkNotNull(componentDescriptor);
      DaggerTypeElement componentType = DaggerTypeElement.from(componentDescriptor.typeElement());
      componentPath =
          parentResolver.isPresent()
              ? parentResolver.get().componentPath.childPath(componentType)
              : ComponentPath.create(ImmutableList.of(componentType));
      this.componentNode = ComponentNodeImpl.create(componentPath, componentDescriptor);
      this.network =
          parentResolver.isPresent()
              ? parentResolver.get().network
              : NetworkBuilder.directed().allowsParallelEdges(true).allowsSelfLoops(true).build();
      declarations =
          componentDeclarationsFactory.create(
              parentResolver.map(parent -> parent.componentDescriptor),
              componentDescriptor);
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByFactoryMethods().values());
      subcomponentsToResolve.addAll(
          componentDescriptor.childComponentsDeclaredByBuilderEntryPoints().values());
    }

    void resolve(boolean createFullBindingGraph) {
      addNode(componentNode);

      componentDescriptor.entryPointMethods().stream()
          .map(method -> method.dependencyRequest().get())
          .forEach(
              entryPoint -> {
                ResolvedBindings resolvedBindings =
                    entryPoint.kind().equals(MEMBERS_INJECTION)
                        ? resolveMembersInjectionKey(entryPoint.key())
                        : resolveContributionKey(entryPoint.key());
                addDependencyEdges(componentNode, resolvedBindings, entryPoint);
              });

      if (createFullBindingGraph) {
        // Resolve the keys for all bindings in all modules, stripping any multibinding contribution
        // identifier so that the multibinding itself is resolved.
        declarations.allDeclarations().stream()
            // TODO(b/349155899): Consider resolving all declarations in full binding graph mode,
            // not just those from modules.
            .filter(declaration -> declaration.contributingModule().isPresent())
            // @BindsOptionalOf bindings are keyed by the unwrapped type so wrap it in Optional to
            // resolve the optional type instead.
            .map(
                declaration ->
                    declaration instanceof OptionalBindingDeclaration
                        ? keyFactory.optionalOf(declaration.key())
                        : declaration.key())
            .map(Key::withoutMultibindingContributionIdentifier)
            .forEach(this::resolveContributionKey);
      }

      // Resolve all bindings for subcomponents, creating subgraphs for all subcomponents that have
      // been detected during binding resolution. If a binding for a subcomponent is never resolved,
      // no BindingGraph will be created for it and no implementation will be generated. This is
      // done in a queue since resolving one subcomponent might resolve a key for a subcomponent
      // from a parent graph. This is done until no more new subcomponents are resolved.
      Set<ComponentDescriptor> resolvedSubcomponents = new HashSet<>();
      for (ComponentDescriptor subcomponent : Iterables.consumingIterable(subcomponentsToResolve)) {
        if (resolvedSubcomponents.add(subcomponent)) {
          Resolver subcomponentResolver = new Resolver(this, subcomponent);
          addChildFactoryMethodEdge(subcomponentResolver);
          subcomponentResolver.resolve(createFullBindingGraph);
        }
      }
    }

    /**
     * Returns the resolved contribution bindings for the given {@link Key}:
     *
     * <ul>
     *   <li>All explicit bindings for:
     *       <ul>
     *         <li>the requested key
     *         <li>{@code Set<T>} if the requested key's type is {@code Set<Produced<T>>}
     *         <li>{@code Map<K, Provider<V>>} if the requested key's type is {@code Map<K,
     *             Producer<V>>}.
     *       </ul>
     *   <li>An implicit {@link Inject @Inject}-annotated constructor binding if there is one and
     *       there are no explicit bindings or synthetic bindings.
     * </ul>
     */
    ResolvedBindings lookUpBindings(Key requestKey) {
      Set<ContributionBinding> bindings = new LinkedHashSet<>();
      Set<ContributionBinding> multibindingContributions = new LinkedHashSet<>();
      Set<MultibindingDeclaration> multibindingDeclarations = new LinkedHashSet<>();
      Set<OptionalBindingDeclaration> optionalBindingDeclarations = new LinkedHashSet<>();
      Set<SubcomponentDeclaration> subcomponentDeclarations = new LinkedHashSet<>();

      // Gather all bindings, multibindings, optional, and subcomponent declarations/contributions.
      for (Resolver resolver : getResolverLineage()) {
        bindings.addAll(resolver.getLocalExplicitBindings(requestKey));
        multibindingContributions.addAll(resolver.getLocalMultibindingContributions(requestKey));
        multibindingDeclarations.addAll(resolver.declarations.multibindings(requestKey));
        subcomponentDeclarations.addAll(resolver.declarations.subcomponents(requestKey));
        // The optional binding declarations are keyed by the unwrapped type.
        keyFactory.unwrapOptional(requestKey)
            .map(resolver.declarations::optionalBindings)
            .ifPresent(optionalBindingDeclarations::addAll);
      }

      // Add synthetic multibinding
      if (!multibindingContributions.isEmpty() || !multibindingDeclarations.isEmpty()) {
        if (MapType.isMap(requestKey)) {
          bindings.add(bindingFactory.multiboundMap(requestKey, multibindingContributions));
        } else if (SetType.isSet(requestKey)) {
          bindings.add(bindingFactory.multiboundSet(requestKey, multibindingContributions));
        } else {
          throw new AssertionError("Unexpected type in multibinding key: " + requestKey);
        }
      }

      // Add synthetic optional binding
      if (!optionalBindingDeclarations.isEmpty()) {
        ImmutableSet<Binding> optionalContributions =
            lookUpBindings(keyFactory.unwrapOptional(requestKey).get()).bindings();
        bindings.add(
            optionalContributions.isEmpty()
                ? bindingFactory.syntheticAbsentOptionalDeclaration(requestKey)
                : bindingFactory.syntheticPresentOptionalDeclaration(
                    requestKey, optionalContributions));
      }

      // Add subcomponent creator binding
      if (!subcomponentDeclarations.isEmpty()) {
        ContributionBinding binding =
            bindingFactory.subcomponentCreatorBinding(
                ImmutableSet.copyOf(subcomponentDeclarations));
        bindings.add(binding);
        addSubcomponentToOwningResolver(binding);
      }

      // Add members injector binding
      if (isTypeOf(requestKey.type().xprocessing(), XTypeNames.MEMBERS_INJECTOR)) {
        injectBindingRegistry.getOrFindMembersInjectorBinding(requestKey).ifPresent(bindings::add);
      }

      // Add Assisted Factory binding
      if (isDeclared(requestKey.type().xprocessing())
          && isAssistedFactoryType(requestKey.type().xprocessing().getTypeElement())) {
        bindings.add(
            bindingFactory.assistedFactoryBinding(
                requestKey.type().xprocessing().getTypeElement(),
                Optional.of(requestKey.type().xprocessing())));
      }

      // If there are no bindings, add the implicit @Inject-constructed binding if there is one.
      if (bindings.isEmpty()) {
        injectBindingRegistry
            .getOrFindInjectionBinding(requestKey)
            .filter(this::isCorrectlyScopedInSubcomponent)
            .ifPresent(bindings::add);
      }

      return ResolvedBindings.create(
          requestKey,
          bindings.stream()
              .map(
                  binding -> {
                    Optional<BindingNode> bindingNodeOwnedByAncestor =
                        getBindingNodeOwnedByAncestor(requestKey, binding);
                    // If a binding is owned by an ancestor we use the corresponding BindingNode
                    // instance directly rather than creating a new instance to avoid accidentally
                    // including additional multi/optional/subcomponent declarations that don't
                    // exist in the ancestor's BindingNode instance.
                    return bindingNodeOwnedByAncestor.isPresent()
                          ? bindingNodeOwnedByAncestor.get()
                          : bindingNodeFactory.forContributionBindings(
                              componentPath,
                              binding,
                              multibindingDeclarations,
                              optionalBindingDeclarations,
                              subcomponentDeclarations);
                  })
              .collect(toImmutableSet()));
    }

    /**
     * Returns true if this binding graph resolution is for a subcomponent and the {@code @Inject}
     * binding's scope correctly matches one of the components in the current component ancestry.
     * If not, it means the binding is not owned by any of the currently known components, and will
     * be owned by a future ancestor (or, if never owned, will result in an incompatibly scoped
     * binding error at the root component).
     */
    private boolean isCorrectlyScopedInSubcomponent(ContributionBinding binding) {
      checkArgument(binding.kind() == INJECTION || binding.kind() == ASSISTED_INJECTION);
      if (!rootComponent().isSubcomponent()
          || !binding.scope().isPresent()
          || binding.scope().get().isReusable()) {
        return true;
      }

      Resolver owningResolver = getOwningResolver(binding).orElse(this);
      ComponentDescriptor owningComponent = owningResolver.componentDescriptor;
      return owningComponent.scopes().contains(binding.scope().get());
    }

    private ComponentDescriptor rootComponent() {
      return parentResolver.map(Resolver::rootComponent).orElse(componentDescriptor);
    }

    /** Returns the resolved members injection bindings for the given {@link Key}. */
    ResolvedBindings lookUpMembersInjectionBinding(Key requestKey) {
      // no explicit deps for members injection, so just look it up
      Optional<MembersInjectionBinding> binding =
          injectBindingRegistry.getOrFindMembersInjectionBinding(requestKey);
      return binding.isPresent()
          ? ResolvedBindings.create(
              requestKey,
              bindingNodeFactory.forMembersInjectionBinding(componentPath, binding.get()))
          : ResolvedBindings.create(requestKey);
    }

    /**
     * When a binding is resolved for a {@link SubcomponentDeclaration}, adds corresponding {@link
     * ComponentDescriptor subcomponent} to a queue in the owning component's resolver. The queue
     * will be used to detect which subcomponents need to be resolved.
     */
    private void addSubcomponentToOwningResolver(ContributionBinding subcomponentCreatorBinding) {
      checkArgument(subcomponentCreatorBinding.kind().equals(SUBCOMPONENT_CREATOR));
      Resolver owningResolver = getOwningResolver(subcomponentCreatorBinding).get();

      XTypeElement builderType =
          subcomponentCreatorBinding.key().type().xprocessing().getTypeElement();
      owningResolver.subcomponentsToResolve.add(
          owningResolver.componentDescriptor.getChildComponentWithBuilderType(builderType));
    }

    private ImmutableSet<ContributionBinding> createDelegateBindings(
        ImmutableSet<DelegateDeclaration> delegateDeclarations) {
      ImmutableSet.Builder<ContributionBinding> builder = ImmutableSet.builder();
      for (DelegateDeclaration delegateDeclaration : delegateDeclarations) {
        builder.add(bindingFactory.delegateBinding(delegateDeclaration));
      }
      return builder.build();
    }
    /**
     * Returns a {@link BindingNode} for the given binding that is owned by an ancestor component,
     * if one exists. Otherwise returns {@link Optional#empty()}.
     */
    private Optional<BindingNode> getBindingNodeOwnedByAncestor(
        Key requestKey, ContributionBinding binding) {
      if (canBeResolvedInParent(requestKey, binding)) {
        // Resolve in the parent to make sure we have the most recent multi/optional contributions.
        parentResolver.get().resolveContributionKey(requestKey);
        BindingNode previouslyResolvedBinding =
            getPreviouslyResolvedBindings(requestKey).get().forBinding(binding);
        if (!requiresResolutionChecker.requiresResolution(previouslyResolvedBinding)) {
          return Optional.of(previouslyResolvedBinding);
        }
      }
      return Optional.empty();
    }

    private boolean canBeResolvedInParent(Key requestKey, ContributionBinding binding) {
      if (parentResolver.isEmpty()) {
        return false;
      }
      Optional<Resolver> owningResolver = getOwningResolver(binding);
      if (owningResolver.isPresent()) {
        return !owningResolver.get().equals(this);
      }
      return !Keys.isComponentOrCreator(requestKey)
          // TODO(b/305748522): Allow caching for assisted injection bindings.
          && binding.kind() != BindingKind.ASSISTED_INJECTION
          && getPreviouslyResolvedBindings(requestKey).isPresent()
          && getPreviouslyResolvedBindings(requestKey).get().bindings().contains(binding);
    }

    private Optional<Resolver> getOwningResolver(ContributionBinding binding) {
      // TODO(ronshapiro): extract the different pieces of this method into their own methods
      if ((binding.scope().isPresent() && binding.scope().get().isProductionScope())
          || binding.kind().equals(BindingKind.PRODUCTION)) {
        for (Resolver requestResolver : getResolverLineage()) {
          // Resolve @Inject @ProductionScope bindings at the highest production component.
          if (binding.kind().equals(INJECTION)
              && requestResolver.componentDescriptor.isProduction()) {
            return Optional.of(requestResolver);
          }

          // Resolve explicit @Produces and @ProductionScope bindings at the highest component that
          // installs the binding.
          if (requestResolver.containsExplicitBinding(binding)) {
            return Optional.of(requestResolver);
          }
        }
      }

      if (binding.scope().isPresent() && binding.scope().get().isReusable()) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          // If a @Reusable binding was resolved in an ancestor, use that component.
          ResolvedBindings resolvedBindings =
              requestResolver.resolvedContributionBindings.get(binding.key());
          if (resolvedBindings != null && resolvedBindings.bindings().contains(binding)) {
            return Optional.of(requestResolver);
          }
        }
        // If a @Reusable binding was not resolved in any ancestor, resolve it here.
        return Optional.empty();
      }

      // TODO(b/359893922): we currently iterate from child to parent to find an owning resolver,
      // but we probably want to iterate from parent to child to catch missing bindings in
      // misconfigured repeated modules.
      for (Resolver requestResolver : getResolverLineage().reverse()) {
        if (requestResolver.containsExplicitBinding(binding)) {
          return Optional.of(requestResolver);
        }
      }

      // look for scope separately.  we do this for the case where @Singleton can appear twice
      // in the † compatibility mode
      Optional<Scope> bindingScope = binding.scope();
      if (bindingScope.isPresent()) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.componentDescriptor.scopes().contains(bindingScope.get())) {
            return Optional.of(requestResolver);
          }
        }
      }
      return Optional.empty();
    }

    private boolean containsExplicitBinding(ContributionBinding binding) {
      return declarations.bindings(binding.key()).contains(binding)
          || resolverContainsDelegateDeclarationForBinding(binding)
          || !declarations.subcomponents(binding.key()).isEmpty();
    }

    /** Returns true if {@code binding} was installed in a module in this resolver's component. */
    private boolean resolverContainsDelegateDeclarationForBinding(ContributionBinding binding) {
      if (!binding.kind().equals(DELEGATE)) {
        return false;
      }
      if (LegacyBindingGraphFactory.hasStrictMultibindingsExemption(compilerOptions, binding)) {
        return false;
      }
      return declarations.delegates(binding.key()).stream()
          .anyMatch(
              declaration ->
                  declaration.contributingModule().equals(binding.contributingModule())
                  && declaration.bindingElement().equals(binding.bindingElement()));
    }

    /** Returns the resolver lineage from parent to child. */
    private ImmutableList<Resolver> getResolverLineage() {
      ImmutableList.Builder<Resolver> resolverList = ImmutableList.builder();
      for (Optional<Resolver> currentResolver = Optional.of(this);
          currentResolver.isPresent();
          currentResolver = currentResolver.get().parentResolver) {
        resolverList.add(currentResolver.get());
      }
      return resolverList.build().reverse();
    }

    /**
     * Returns the explicit {@link ContributionBinding}s that match the {@code key} from this
     * resolver.
     */
    private ImmutableSet<ContributionBinding> getLocalExplicitBindings(Key key) {
      return ImmutableSet.<ContributionBinding>builder()
          .addAll(declarations.bindings(key))
          .addAll(createDelegateBindings(declarations.delegates(key)))
          .build();
    }

    /**
     * Returns the explicit multibinding contributions that contribute to the map or set requested
     * by {@code key} from this resolver.
     */
    private ImmutableSet<ContributionBinding> getLocalMultibindingContributions(Key key) {
      return ImmutableSet.<ContributionBinding>builder()
          .addAll(declarations.multibindingContributions(key))
          .addAll(createDelegateBindings(declarations.delegateMultibindingContributions(key)))
          .build();
    }

    /**
     * Returns the {@link OptionalBindingDeclaration}s that match the {@code key} from this and all
     * ancestor resolvers.
     */
    private ImmutableSet<OptionalBindingDeclaration> getOptionalBindingDeclarations(Key key) {
      Optional<Key> unwrapped = keyFactory.unwrapOptional(key);
      if (unwrapped.isEmpty()) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<OptionalBindingDeclaration> declarations = ImmutableSet.builder();
      for (Resolver resolver : getResolverLineage()) {
        declarations.addAll(resolver.declarations.optionalBindings(unwrapped.get()));
      }
      return declarations.build();
    }

    /**
     * Returns the {@link ResolvedBindings} for {@code key} that was resolved in this resolver or an
     * ancestor resolver. Only checks for {@link ContributionBinding}s as {@link
     * MembersInjectionBinding}s are not inherited.
     */
    private Optional<ResolvedBindings> getPreviouslyResolvedBindings(Key key) {
      if (parentResolver.isEmpty()) {
        return Optional.empty();
      }
      // Check the parent's resolvedContributionBindings directly before calling
      // parentResolver.getPreviouslyResolvedBindings() otherwise the parent will skip itself.
      return parentResolver.get().resolvedContributionBindings.containsKey(key)
          ? Optional.of(parentResolver.get().resolvedContributionBindings.get(key))
          : parentResolver.get().getPreviouslyResolvedBindings(key);
    }

    private ResolvedBindings resolveMembersInjectionKey(Key key) {
      if (resolvedMembersInjectionBindings.containsKey(key)) {
        return resolvedMembersInjectionBindings.get(key);
      }
      ResolvedBindings bindings = lookUpMembersInjectionBinding(key);
      addNodes(bindings);
      resolveDependencies(bindings);
      resolvedMembersInjectionBindings.put(key, bindings);
      return bindings;
    }

    @CanIgnoreReturnValue
    private ResolvedBindings resolveContributionKey(Key key) {
      if (resolvedContributionBindings.containsKey(key)) {
        return resolvedContributionBindings.get(key);
      }
      ResolvedBindings bindings = lookUpBindings(key);
      resolvedContributionBindings.put(key, bindings);
      addNodes(bindings);
      resolveDependencies(bindings);
      return bindings;
    }

     /** Resolves each of the dependencies of the bindings owned by this component. */
    private void resolveDependencies(ResolvedBindings resolvedBindings) {
      for (BindingNode binding : resolvedBindings.bindingNodesOwnedBy(componentPath)) {
        for (DependencyRequest request : binding.dependencies()) {
          ResolvedBindings dependencies = resolveContributionKey(request.key());
          addDependencyEdges(binding, dependencies, request);
        }
      }
    }

    private void addNodes(ResolvedBindings resolvedBindings) {
      if (resolvedBindings.isEmpty()) {
        addNode(missingBinding(resolvedBindings.key()));
        return;
      }
      resolvedBindings.bindingNodesOwnedBy(componentPath).forEach(this::addNode);
    }

    private void addNode(Node node) {
      network.addNode(node);
      // Subcomponent creator bindings have an implicit edge to the subcomponent they create.
      if (node instanceof BindingNode && ((BindingNode) node).kind() == SUBCOMPONENT_CREATOR) {
        addSubcomponentEdge((BindingNode) node);
      }
    }

    private void addDependencyEdges(
        Node parent, ResolvedBindings dependencies, DependencyRequest request) {
      if (dependencies.isEmpty()) {
        addDependencyEdge(parent, missingBinding(request.key()), request);
      } else {
        dependencies.bindingNodes()
            .forEach(dependency -> addDependencyEdge(parent, dependency, request));
      }
    }

    private void addDependencyEdge(Node source, Node target, DependencyRequest request) {
      boolean isEntryPoint = source instanceof ComponentNode;
      addEdge(source, target, new DependencyEdgeImpl(request, isEntryPoint));
    }

    private void addSubcomponentEdge(BindingNode binding) {
      checkState(binding.kind() == SUBCOMPONENT_CREATOR);
      Resolver owningResolver =
          getResolverLineage().reverse().stream()
                .filter(resolver -> resolver.componentPath.equals(binding.componentPath()))
                .collect(onlyElement());
      ComponentDescriptor subcomponent =
          owningResolver.componentDescriptor.getChildComponentWithBuilderType(
              binding.key().type().xprocessing().getTypeElement());
      ComponentNode subcomponentNode =
          ComponentNodeImpl.create(
              owningResolver.componentPath.childPath(
                  DaggerTypeElement.from(subcomponent.typeElement())),
              subcomponent);
      addEdge(
          binding,
          subcomponentNode,
          new SubcomponentCreatorBindingEdgeImpl(binding.subcomponentDeclarations()));
    }

    private void addChildFactoryMethodEdge(Resolver subcomponentResolver) {
      componentDescriptor
          .getFactoryMethodForChildComponent(subcomponentResolver.componentDescriptor)
          .ifPresent(
              childFactoryMethod
                  -> addEdge(
                      componentNode,
                      subcomponentResolver.componentNode,
                      new ChildFactoryMethodEdgeImpl(childFactoryMethod.methodElement())));
    }

    private void addEdge(Node source, Node target, Edge edge) {
      network.addNode(source);
      network.addNode(target);
      network.addEdge(source, target, edge);
    }

    private MissingBinding missingBinding(Key key) {
      // Put all missing binding nodes in the root component. This simplifies the binding graph
      // and produces better error messages for users since all dependents point to the same node.
      return MissingBindingImpl.create(rootResolver().componentPath, key);
    }

    private Resolver rootResolver() {
      return parentResolver.isPresent() ? parentResolver.get().rootResolver() : this;
    }

    private final class RequiresResolutionChecker {
      private final Map<Node, Boolean> dependsOnMissingBindingCache = new HashMap<>();
      private final Map<Node, Boolean> dependsOnLocalBindingsCache = new HashMap<>();

      boolean requiresResolution(BindingNode binding) {
        // If we're not allowed to float then the binding cannot be re-resolved in this component.
        if (isNotAllowedToFloat(binding)) {
          return false;
        }
        if (hasLocalBindings(binding)) {
          return true;
        }
        return shouldCheckDependencies(binding)
            // Try to re-resolving bindings that depend on missing bindings. The missing bindings
            // will still end up being reported for cases where the binding is not allowed to float,
            // but re-resolving allows cases that are allowed to float to be re-resolved which can
            // prevent misleading dependency traces that include all floatable bindings.
            // E.g. see MissingBindingSuggestionsTest#bindsMissingBinding_fails().
            && (dependsOnLocalBinding(binding) || dependsOnMissingBinding(binding));
      }

      private boolean isNotAllowedToFloat(BindingNode binding) {
        // In general, @Provides/@Binds/@Production bindings are allowed to float to get access to
        // multibinding contributions that are contributed in subcomponents. However, they aren't
        // allowed to float to get access to missing bindings that are installed in subcomponents,
        // so we prevent floating if these bindings depend on a missing binding.
        return binding.kind() != BindingKind.INJECTION
            && binding.kind() != BindingKind.ASSISTED_INJECTION
            && dependsOnMissingBinding(binding);
      }

      private boolean dependsOnMissingBinding(BindingNode binding) {
        if (!dependsOnMissingBindingCache.containsKey(binding)) {
          visitUncachedDependencies(binding);
        }
        return checkNotNull(dependsOnMissingBindingCache.get(binding));
      }

      private boolean dependsOnLocalBinding(BindingNode binding) {
        if (!dependsOnLocalBindingsCache.containsKey(binding)) {
          visitUncachedDependencies(binding);
        }
        return checkNotNull(dependsOnLocalBindingsCache.get(binding));
      }

      private void visitUncachedDependencies(BindingNode binding) {
        // We use Tarjan's algorithm to visit the uncached dependencies of the binding grouped by
        // strongly connected nodes (i.e. cycles) and iterated in reverse topological order.
        for (ImmutableSet<Node> cycleNodes : stronglyConnectedNodes(binding)) {
          // As a sanity check, verify that none of the keys in the cycle are cached yet.
          checkState(cycleNodes.stream().noneMatch(dependsOnLocalBindingsCache::containsKey));
          checkState(cycleNodes.stream().noneMatch(dependsOnMissingBindingCache::containsKey));
          boolean dependsOnMissingBinding =
              cycleNodes.stream().anyMatch(this::isMissingBinding)
              || cycleNodes.stream()
                  .filter(this::shouldCheckDependencies)
                  .flatMap(this::dependencyStream)
                  .filter(not(cycleNodes::contains))
                  .anyMatch(dependsOnMissingBindingCache::get);
          // All keys in the cycle have the same cached value since they all depend on each other.
          cycleNodes.forEach(
              cycleNode -> dependsOnMissingBindingCache.put(cycleNode, dependsOnMissingBinding));

          // Note that we purposely don't filter out scoped bindings below. In particular, there are
          // currently 3 cases where hasLocalBinding will return true:
          //
          //   1) The binding is MULTIBOUND_SET/MULTIBOUND_MAP and depends on an explicit
          //      multibinding contributions in the current component.
          //   2) The binding is OPTIONAL and depends on an explicit binding contributed in the
          //      current component.
          //   3) The binding has a duplicate explicit binding contributed in this component.
          //
          // For case #1 and #2 it's not actually required to check for scope because those are
          // synthetic bindings which are never scoped.
          //
          // For case #3 we actually want don't want to rule out a scoped binding, e.g. in the case
          // where we have a floating @Inject Foo(Bar bar) binding with @Singleton Bar provided in
          // the ParentComponent and a duplicate Bar provided in the ChildComponent we want to
          // reprocess Foo so that we can report the duplicate Bar binding.
          boolean dependsOnLocalBindings =
              // First, check if any of the bindings themselves depends on local bindings.
              cycleNodes.stream().anyMatch(this::hasLocalBindings)
              // Next, check if any of the dependencies (that aren't in the cycle itself) depend
              // on local bindings. We should be guaranteed that all dependencies are cached since
              // Tarjan's algorithm is traversed in reverse topological order.
              || cycleNodes.stream()
                  .filter(this::shouldCheckDependencies)
                  .flatMap(this::dependencyStream)
                  .filter(not(cycleNodes::contains))
                  .anyMatch(dependsOnLocalBindingsCache::get);
          // All keys in the cycle have the same cached value since they all depend on each other.
          cycleNodes.forEach(
              cycleNode -> dependsOnLocalBindingsCache.put(cycleNode, dependsOnLocalBindings));
        }
      }

      /**
       * Returns a list of strongly connected components in reverse topological order, starting from
       * the given {@code rootNode} and traversing its transitive dependencies.
       *
       * <p>Note that the returned list may not include all transitive dependencies of the {@code
       * rootNode} because we intentionally stop at dependencies that:
       *
       * <ul>
       *   <li> Already have a cached value.
       *   <li> Are scoped to an ancestor component (i.e. cannot depend on local bindings).
       * </ul>
       */
      private ImmutableList<ImmutableSet<Node>> stronglyConnectedNodes(BindingNode rootNode) {
        return TarjanSCCs.compute(
            ImmutableSet.of(rootNode),
            node -> shouldCheckDependencies(node)
                ? dependencyStream(node)
                    // Skip dependencies that are already cached
                    .filter(dep -> !dependsOnLocalBindingsCache.containsKey(dep))
                    .collect(toImmutableSet())
                : ImmutableSet.of());
      }

      private Stream<Node> dependencyStream(Node node) {
        return network.successors(node).stream();
      }

      private boolean shouldCheckDependencies(Node node) {
        if (!(node instanceof BindingNode)) {
          return false;
        }
        // Note: we can skip dependencies for scoped bindings because while there could be
        // duplicates underneath the scoped binding, those duplicates are technically unused so
        // Dagger shouldn't validate them.
        BindingNode binding = (BindingNode) node;
        return !isScopedToComponent(binding)
            // TODO(beder): Figure out what happens with production subcomponents.
            && !binding.kind().equals(BindingKind.PRODUCTION);
      }

      private boolean isScopedToComponent(BindingNode binding) {
        return binding.scope().isPresent() && !binding.scope().get().isReusable();
      }

      private boolean isMissingBinding(Node binding) {
        return binding instanceof MissingBinding;
      }

      private boolean hasLocalBindings(Node node) {
        if (!(node instanceof BindingNode)) {
          return false;
        }
        BindingNode binding = (BindingNode) node;
        return hasLocalMultibindingContributions(binding)
            || hasLocalOptionalBindingContribution(binding)
            || hasDuplicateExplicitBinding(binding);
      }
    }

    /**
     * Returns {@code true} if there's a contribution in this component matching the given binding
     * key.
     */
    private boolean hasLocalMultibindingContributions(BindingNode binding) {
      return !declarations.multibindingContributions(binding.key()).isEmpty()
          || !declarations.delegateMultibindingContributions(binding.key()).isEmpty();
    }

    /**
     * Returns {@code true} if there is a contribution in this component for an {@code
     * Optional<Foo>} key that has not been contributed in a parent.
     */
    private boolean hasLocalOptionalBindingContribution(BindingNode binding) {
      if (binding.kind() == OPTIONAL) {
        return hasLocalExplicitBindings(keyFactory.unwrapOptional(binding.key()).get());
      } else {
        // If a parent contributes a @Provides Optional<Foo> binding and a child has a
        // @BindsOptionalOf Foo method, the two should conflict, even if there is no binding for
        // Foo on its own
        return !getOptionalBindingDeclarations(binding.key()).isEmpty();
      }
    }

    /**
     * Returns {@code true} if there is at least one explicit binding that matches the given key.
     */
    private boolean hasLocalExplicitBindings(Key requestKey) {
      return !declarations.bindings(requestKey).isEmpty()
          || !declarations.delegates(requestKey).isEmpty();
    }

    /** Returns {@code true} if this resolver has a duplicate explicit binding to resolve. */
    private boolean hasDuplicateExplicitBinding(BindingNode binding) {
      // By default, we don't actually report an error when an explicit binding tries to override
      // an injection binding (b/312202845). For now, ignore injection bindings unless we actually
      // will report an error, otherwise we'd end up silently overriding the binding rather than
      // reporting a duplicate.
      // TODO(b/312202845): This can be removed once b/312202845 is fixed.
      if (binding.kind() == BindingKind.INJECTION
              && !compilerOptions.explicitBindingConflictsWithInjectValidationType()
                  .diagnosticKind()
                  .equals(Optional.of(Diagnostic.Kind.ERROR))) {
        return false;
      }

      // If the current component has an explicit binding for the same key it must be a duplicate.
      return hasLocalExplicitBindings(binding.key());
    }
  }
}
