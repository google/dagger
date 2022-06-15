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

import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.javapoet.TypeNames.isFutureType;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.squareup.javapoet.TypeName;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A component declaration.
 *
 * <p>Represents one type annotated with {@code @Component}, {@code Subcomponent},
 * {@code @ProductionComponent}, or {@code @ProductionSubcomponent}.
 *
 * <p>When validating bindings installed in modules, a {@link ComponentDescriptor} can also
 * represent a synthetic component for the module, where there is an entry point for each binding in
 * the module.
 */
@CheckReturnValue
@AutoValue
public abstract class ComponentDescriptor {
  /**
   * The cancellation policy for a {@link dagger.producers.ProductionComponent}.
   *
   * <p>@see dagger.producers.CancellationPolicy
   */
  public enum CancellationPolicy {
    PROPAGATE,
    IGNORE;

    private static CancellationPolicy from(XAnnotation annotation) {
      checkArgument(XAnnotations.getClassName(annotation).equals(TypeNames.CANCELLATION_POLICY));
      return valueOf(getSimpleName(annotation.getAsEnum("fromSubcomponents")));
    }
  }

  /** Creates a {@link ComponentDescriptor}. */
  static ComponentDescriptor create(
      ComponentAnnotation componentAnnotation,
      XTypeElement component,
      ImmutableSet<ComponentRequirement> componentDependencies,
      ImmutableSet<ModuleDescriptor> transitiveModules,
      ImmutableMap<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod,
      ImmutableSet<Scope> scopes,
      ImmutableSet<ComponentDescriptor> subcomponentsFromModules,
      ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor> subcomponentsByFactoryMethod,
      ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor> subcomponentsByBuilderMethod,
      ImmutableSet<ComponentMethodDescriptor> componentMethods,
      Optional<ComponentCreatorDescriptor> creator) {
    ComponentDescriptor descriptor =
        new AutoValue_ComponentDescriptor(
            componentAnnotation,
            component,
            componentDependencies,
            transitiveModules,
            dependenciesByDependencyMethod,
            scopes,
            subcomponentsFromModules,
            subcomponentsByFactoryMethod,
            subcomponentsByBuilderMethod,
            componentMethods,
            creator);
    return descriptor;
  }

  /** The annotation that specifies that {@link #typeElement()} is a component. */
  public abstract ComponentAnnotation annotation();

  /** Returns {@code true} if this is a subcomponent. */
  public final boolean isSubcomponent() {
    return annotation().isSubcomponent();
  }

  /**
   * Returns {@code true} if this is a production component or subcomponent, or a
   * {@code @ProducerModule} when doing module binding validation.
   */
  public final boolean isProduction() {
    return annotation().isProduction();
  }

  /**
   * Returns {@code true} if this is a real component, and not a fictional one used to validate
   * module bindings.
   */
  public final boolean isRealComponent() {
    return annotation().isRealComponent();
  }

  /**
   * The element that defines the component. This is the element to which the {@link #annotation()}
   * was applied.
   */
  public abstract XTypeElement typeElement();

  /**
   * The set of component dependencies listed in {@link Component#dependencies} or {@link
   * dagger.producers.ProductionComponent#dependencies()}.
   */
  public abstract ImmutableSet<ComponentRequirement> dependencies();

  /** The non-abstract {@link #modules()} and the {@link #dependencies()}. */
  public final ImmutableSet<ComponentRequirement> dependenciesAndConcreteModules() {
    return Stream.concat(
            moduleTypes().stream()
                .filter(dep -> !dep.isAbstract())
                .map(module -> ComponentRequirement.forModule(module.getType())),
            dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * The {@link ModuleDescriptor modules} declared in {@link Component#modules()} and reachable by
   * traversing {@link Module#includes()}.
   */
  public abstract ImmutableSet<ModuleDescriptor> modules();

  /** The types of the {@link #modules()}. */
  public final ImmutableSet<XTypeElement> moduleTypes() {
    return modules().stream().map(ModuleDescriptor::moduleElement).collect(toImmutableSet());
  }

  /**
   * The types for which the component will need instances if all of its bindings are used. For the
   * types the component will need in a given binding graph, use {@link
   * BindingGraph#componentRequirements()}.
   *
   * <ul>
   *   <li>{@linkplain #modules()} modules} with concrete instance bindings
   *   <li>Bound instances
   *   <li>{@linkplain #dependencies() dependencies}
   * </ul>
   */
  @Memoized
  ImmutableSet<ComponentRequirement> requirements() {
    ImmutableSet.Builder<ComponentRequirement> requirements = ImmutableSet.builder();
    modules().stream()
        .filter(
            module ->
                module.bindings().stream().anyMatch(ContributionBinding::requiresModuleInstance))
        .map(module -> ComponentRequirement.forModule(module.moduleElement().getType()))
        .forEach(requirements::add);
    requirements.addAll(dependencies());
    requirements.addAll(
        creatorDescriptor()
            .map(ComponentCreatorDescriptor::boundInstanceRequirements)
            .orElse(ImmutableSet.of()));
    return requirements.build();
  }

  /**
   * This component's {@linkplain #dependencies() dependencies} keyed by each provision or
   * production method defined by that dependency. Note that the dependencies' types are not simply
   * the enclosing type of the method; a method may be declared by a supertype of the actual
   * dependency.
   */
  public abstract ImmutableMap<XMethodElement, ComponentRequirement>
      dependenciesByDependencyMethod();

  /** The {@linkplain #dependencies() component dependency} that defines a method. */
  public final ComponentRequirement getDependencyThatDefinesMethod(XElement method) {
    checkArgument(isMethod(method), "method must be an executable element: %s", method);
    checkState(
        dependenciesByDependencyMethod().containsKey(method),
        "no dependency implements %s",
        method);
    return dependenciesByDependencyMethod().get(method);
  }

  /** The scopes of the component. */
  public abstract ImmutableSet<Scope> scopes();

  /**
   * All {@link Subcomponent}s which are direct children of this component. This includes
   * subcomponents installed from {@link Module#subcomponents()} as well as subcomponent {@linkplain
   * #childComponentsDeclaredByFactoryMethods() factory methods} and {@linkplain
   * #childComponentsDeclaredByBuilderEntryPoints() builder methods}.
   */
  public final ImmutableSet<ComponentDescriptor> childComponents() {
    return ImmutableSet.<ComponentDescriptor>builder()
        .addAll(childComponentsDeclaredByFactoryMethods().values())
        .addAll(childComponentsDeclaredByBuilderEntryPoints().values())
        .addAll(childComponentsDeclaredByModules())
        .build();
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a {@linkplain
   * Module#subcomponents() module's subcomponents}.
   */
  abstract ImmutableSet<ComponentDescriptor> childComponentsDeclaredByModules();

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * factory method.
   */
  public abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByFactoryMethods();

  /** Returns a map of {@link #childComponents()} indexed by {@link #typeElement()}. */
  @Memoized
  public ImmutableMap<XTypeElement, ComponentDescriptor> childComponentsByElement() {
    return Maps.uniqueIndex(childComponents(), ComponentDescriptor::typeElement);
  }

  /** Returns the factory method that declares a child component. */
  final Optional<ComponentMethodDescriptor> getFactoryMethodForChildComponent(
      ComponentDescriptor childComponent) {
    return Optional.ofNullable(
        childComponentsDeclaredByFactoryMethods().inverse().get(childComponent));
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByBuilderEntryPoints();

  private final Supplier<ImmutableMap<XTypeElement, ComponentDescriptor>>
      childComponentsByBuilderType =
          Suppliers.memoize(
              () ->
                  childComponents().stream()
                      .filter(child -> child.creatorDescriptor().isPresent())
                      .collect(
                          toImmutableMap(
                              child -> child.creatorDescriptor().get().typeElement(),
                              child -> child)));

  /** Returns the child component with the given builder type. */
  final ComponentDescriptor getChildComponentWithBuilderType(XTypeElement builderType) {
    return checkNotNull(
        childComponentsByBuilderType.get().get(builderType),
        "no child component found for builder type %s",
        builderType.getQualifiedName());
  }

  public abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  /** Returns the first component method associated with this binding request, if one exists. */
  public Optional<ComponentMethodDescriptor> firstMatchingComponentMethod(BindingRequest request) {
    return Optional.ofNullable(firstMatchingComponentMethods().get(request));
  }

  @Memoized
  ImmutableMap<BindingRequest, ComponentMethodDescriptor> firstMatchingComponentMethods() {
    Map<BindingRequest, ComponentMethodDescriptor> methods = new HashMap<>();
    for (ComponentMethodDescriptor method : entryPointMethods()) {
      methods.putIfAbsent(BindingRequest.bindingRequest(method.dependencyRequest().get()), method);
    }
    return ImmutableMap.copyOf(methods);
  }

  /** The entry point methods on the component type. Each has a {@link DependencyRequest}. */
  public final ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods().stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation
  /** Returns a descriptor for the creator type for this component type, if the user defined one. */
  public abstract Optional<ComponentCreatorDescriptor> creatorDescriptor();

  /**
   * Returns {@code true} for components that have a creator, either because the user {@linkplain
   * #creatorDescriptor() specified one} or because it's a top-level component with an implicit
   * builder.
   */
  public final boolean hasCreator() {
    return !isSubcomponent() || creatorDescriptor().isPresent();
  }

  /**
   * Returns the {@link CancellationPolicy} for this component, or an empty optional if either the
   * component is not a production component or no {@code CancellationPolicy} annotation is present.
   */
  public final Optional<CancellationPolicy> cancellationPolicy() {
    return isProduction()
        // TODO(bcorso): Get values from XAnnotation instead of using CancellationPolicy directly.
        ? Optional.ofNullable(typeElement().getAnnotation(TypeNames.CANCELLATION_POLICY))
            .map(CancellationPolicy::from)
        : Optional.empty();
  }

  @Memoized
  @Override
  public int hashCode() {
    // TODO(b/122962745): Only use typeElement().hashCode()
    return Objects.hash(typeElement(), annotation());
  }

  // TODO(ronshapiro): simplify the equality semantics
  @Override
  public abstract boolean equals(Object obj);

  /** A component method. */
  @AutoValue
  public abstract static class ComponentMethodDescriptor {
    /** The method itself. Note that this may be declared on a supertype of the component. */
    public abstract XMethodElement methodElement();

    /**
     * The dependency request for production, provision, and subcomponent creator methods. Absent
     * for subcomponent factory methods.
     */
    public abstract Optional<DependencyRequest> dependencyRequest();

    /** The subcomponent for subcomponent factory methods and subcomponent creator methods. */
    public abstract Optional<ComponentDescriptor> subcomponent();

    /** A {@link ComponentMethodDescriptor}builder for a method. */
    public static Builder builder(XMethodElement method) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor.Builder()
          .methodElement(method);
    }

    /** A builder of {@link ComponentMethodDescriptor}s. */
    @AutoValue.Builder
    public interface Builder {
      /** @see ComponentMethodDescriptor#methodElement() */
      Builder methodElement(XMethodElement methodElement);

      /**
       * @see ComponentMethodDescriptor#dependencyRequest()
       */
      @CanIgnoreReturnValue // TODO(kak): remove this once open-source checkers understand AutoValue
      Builder dependencyRequest(DependencyRequest dependencyRequest);

      /**
       * @see ComponentMethodDescriptor#subcomponent()
       */
      @CanIgnoreReturnValue // TODO(kak): remove this once open-source checkers understand AutoValue
      Builder subcomponent(ComponentDescriptor subcomponent);

      /** Builds the descriptor. */
      ComponentMethodDescriptor build();
    }
  }

  /** No-argument methods defined on {@link Object} that are ignored for contribution. */
  private static final ImmutableSet<String> NON_CONTRIBUTING_OBJECT_METHOD_NAMES =
      ImmutableSet.of("toString", "hashCode", "clone", "getClass");

  /**
   * Returns {@code true} if a method could be a component entry point but not a members-injection
   * method.
   */
  static boolean isComponentContributionMethod(XMethodElement method) {
    return method.getParameters().isEmpty()
        && !isVoid(method.getReturnType())
        && !method.getEnclosingElement().getClassName().equals(TypeName.OBJECT)
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(getSimpleName(method));
  }

  /** Returns {@code true} if a method could be a component production entry point. */
  static boolean isComponentProductionMethod(XMethodElement method) {
    return isComponentContributionMethod(method) && isFutureType(method.getReturnType());
  }
}
