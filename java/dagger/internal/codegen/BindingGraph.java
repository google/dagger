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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.DaggerStreams.presentValues;
import static dagger.internal.codegen.DaggerStreams.stream;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.graph.Traverser;
import dagger.Subcomponent;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** The canonical representation of a full-resolved graph. */
@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();

  /**
   * The resolved bindings for all {@link ContributionBinding}s in this graph, keyed by {@link Key}.
   */
  // TODO(ronshapiro): when MembersInjectionBinding no longer extends Binding, rename this to
  // bindings()
  abstract ImmutableMap<Key, ResolvedBindings> contributionBindings();

  /**
   * The resolved bindings for all {@link MembersInjectionBinding}s in this graph, keyed by {@link
   * Key}.
   */
  abstract ImmutableMap<Key, ResolvedBindings> membersInjectionBindings();

  /**
   * Returns the {@link ResolvedBindings resolved bindings} instance for {@code
   * bindingExpressionKey}. If the bindings will be used for members injection, a {@link
   * ResolvedBindings} with {@linkplain #membersInjectionBindings() members injection bindings} will
   * be returned, otherwise a {@link ResolvedBindings} with {@link #contributionBindings()} will be
   * returned.
   */
  final ResolvedBindings resolvedBindings(BindingRequest request) {
    return request.isRequestKind(RequestKind.MEMBERS_INJECTION)
        ? membersInjectionBindings().get(request.key())
        : contributionBindings().get(request.key());
  }

  final Iterable<ResolvedBindings> resolvedBindings() {
    // Don't return an immutable collection - this is only ever used for looping over all bindings
    // in the graph. Copying is wasteful, especially if is a hashing collection, since the values
    // should all, by definition, be distinct.
    // TODO(dpb): consider inlining this to callers and removing this.
    return Iterables.concat(membersInjectionBindings().values(), contributionBindings().values());
  }

  abstract ImmutableList<BindingGraph> subgraphs();

  /**
   * The type that defines the component for this graph.
   *
   * @see ComponentDescriptor#typeElement()
   */
  TypeElement componentTypeElement() {
    return componentDescriptor().typeElement();
  }

  /**
   * Returns the set of modules that are owned by this graph regardless of whether or not any of
   * their bindings are used in this graph. For graphs representing top-level {@link
   * dagger.Component components}, this set will be the same as {@linkplain
   * ComponentDescriptor#modules() the component's transitive modules}. For {@linkplain Subcomponent
   * subcomponents}, this set will be the transitive modules that are not owned by any of their
   * ancestors.
   */
  abstract ImmutableSet<ModuleDescriptor> ownedModules();

  @Memoized
  ImmutableSet<TypeElement> ownedModuleTypes() {
    return FluentIterable.from(ownedModules()).transform(ModuleDescriptor::moduleElement).toSet();
  }

  /**
   * Returns the factory method for this subcomponent, if it exists.
   *
   * <p>This factory method is the one defined in the parent component's interface.
   *
   * <p>In the example below, the {@link BindingGraph#factoryMethod} for {@code ChildComponent}
   * would return the {@link ExecutableElement}: {@code childComponent(ChildModule1)} .
   *
   * <pre><code>
   *   {@literal @Component}
   *   interface ParentComponent {
   *     ChildComponent childComponent(ChildModule1 childModule);
   *   }
   * </code></pre>
   */
  // TODO(b/73294201): Consider returning the resolved ExecutableType for the factory method.
  abstract Optional<ExecutableElement> factoryMethod();

  /**
   * Returns a map between the {@linkplain ComponentRequirement component requirement} and the
   * corresponding {@link VariableElement} for each module parameter in the {@linkplain
   * BindingGraph#factoryMethod factory method}.
   */
  // TODO(dpb): Consider disallowing modules if none of their bindings are used.
  ImmutableMap<ComponentRequirement, VariableElement> factoryMethodParameters() {
    checkState(factoryMethod().isPresent());
    ImmutableMap.Builder<ComponentRequirement, VariableElement> builder = ImmutableMap.builder();
    for (VariableElement parameter : factoryMethod().get().getParameters()) {
      builder.put(ComponentRequirement.forModule(parameter.asType()), parameter);
    }
    return builder.build();
  }

  private static final Traverser<BindingGraph> SUBGRAPH_TRAVERSER =
      Traverser.forTree(BindingGraph::subgraphs);

  /**
   * The types for which the component needs instances.
   *
   * <ul>
   *   <li>component dependencies
   *   <li>{@linkplain #ownedModules() owned modules} with concrete instance bindings that are used
   *       in the graph
   *   <li>bound instances
   * </ul>
   */
  @Memoized
  ImmutableSet<ComponentRequirement> componentRequirements() {
    ImmutableSet<TypeElement> requiredModules = requiredModuleElements();
    ImmutableSet.Builder<ComponentRequirement> requirements = ImmutableSet.builder();
    componentDescriptor().requirements().stream()
        .filter(
            requirement ->
                !requirement.kind().isModule()
                    || requiredModules.contains(requirement.typeElement()))
        .forEach(requirements::add);
    if (factoryMethod().isPresent()) {
      requirements.addAll(factoryMethodParameters().keySet());
    }
    return requirements.build();
  }

  private ImmutableSet<TypeElement> requiredModuleElements() {
    return stream(SUBGRAPH_TRAVERSER.depthFirstPostOrder(this))
        .flatMap(graph -> graph.contributionBindings().values().stream())
        .flatMap(bindings -> bindings.contributionBindings().stream())
        .map(ContributionBinding::contributingModule)
        .distinct()
        .flatMap(presentValues())
        .filter(ownedModuleTypes()::contains)
        .collect(toImmutableSet());
  }

  /** Returns the {@link ComponentDescriptor}s for this component and its subcomponents. */
  ImmutableSet<ComponentDescriptor> componentDescriptors() {
    return FluentIterable.from(SUBGRAPH_TRAVERSER.depthFirstPreOrder(this))
        .transform(BindingGraph::componentDescriptor)
        .toSet();
  }

  /**
   * {@code true} if this graph contains all bindings installed in the component; {@code false} if
   * it contains only those bindings that are reachable from at least one entry point.
   */
  abstract boolean isFullBindingGraph();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override // Suppresses ErrorProne warning that hashCode was overridden w/o equals
  public abstract boolean equals(Object other);

  static BindingGraph create(
      ComponentDescriptor componentDescriptor,
      Map<Key, ResolvedBindings> resolvedContributionBindingsMap,
      Map<Key, ResolvedBindings> resolvedMembersInjectionBindings,
      List<BindingGraph> subgraphs,
      Set<ModuleDescriptor> ownedModules,
      Optional<ExecutableElement> factoryMethod,
      boolean isFullBindingGraph) {
    checkForDuplicates(subgraphs);
    return new AutoValue_BindingGraph(
        componentDescriptor,
        ImmutableMap.copyOf(resolvedContributionBindingsMap),
        ImmutableMap.copyOf(resolvedMembersInjectionBindings),
        ImmutableList.copyOf(subgraphs),
        ImmutableSet.copyOf(ownedModules),
        factoryMethod,
        isFullBindingGraph);
  }

  private static final void checkForDuplicates(Iterable<BindingGraph> graphs) {
    Map<TypeElement, Collection<BindingGraph>> duplicateGraphs =
        Maps.filterValues(
            Multimaps.index(graphs, graph -> graph.componentDescriptor().typeElement()).asMap(),
            overlapping -> overlapping.size() > 1);
    if (!duplicateGraphs.isEmpty()) {
      throw new IllegalArgumentException("Expected no duplicates: " + duplicateGraphs);
    }
  }
}
