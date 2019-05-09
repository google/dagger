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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerTypes.isFutureType;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;

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
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.Scope;
import dagger.producers.CancellationPolicy;
import dagger.producers.ProductionComponent;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

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
@AutoValue
abstract class ComponentDescriptor {
  /** The annotation that specifies that {@link #typeElement()} is a component. */
  abstract ComponentAnnotation annotation();

  /** Returns {@code true} if this is a subcomponent. */
  final boolean isSubcomponent() {
    return annotation().isSubcomponent();
  }

  /**
   * Returns {@code true} if this is a production component or subcomponent, or a
   * {@code @ProducerModule} when doing module binding validation.
   */
  final boolean isProduction() {
    return annotation().isProduction();
  }

  /**
   * Returns {@code true} if this is a real component, and not a fictional one used to validate
   * module bindings.
   */
  final boolean isRealComponent() {
    return annotation().isRealComponent();
  }

  /**
   * The element that defines the component. This is the element to which the {@link #annotation()}
   * was applied.
   */
  abstract TypeElement typeElement();

  /**
   * The set of component dependencies listed in {@link Component#dependencies} or {@link
   * ProductionComponent#dependencies()}.
   */
  abstract ImmutableSet<ComponentRequirement> dependencies();

  /** The non-abstract {@link #modules()} and the {@link #dependencies()}. */
  final ImmutableSet<ComponentRequirement> dependenciesAndConcreteModules() {
    return Stream.concat(
            moduleTypes().stream()
                .filter(dep -> !dep.getModifiers().contains(ABSTRACT))
                .map(module -> ComponentRequirement.forModule(module.asType())),
            dependencies().stream())
        .collect(toImmutableSet());
  }

  /**
   * The {@link ModuleDescriptor modules} declared in {@link Component#modules()} and reachable by
   * traversing {@link Module#includes()}.
   */
  abstract ImmutableSet<ModuleDescriptor> modules();

  /** The types of the {@link #modules()}. */
  final ImmutableSet<TypeElement> moduleTypes() {
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
        .map(module -> ComponentRequirement.forModule(module.moduleElement().asType()))
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
  abstract ImmutableMap<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod();

  /** The {@linkplain #dependencies() component dependency} that defines a method. */
  final ComponentRequirement getDependencyThatDefinesMethod(Element method) {
    checkArgument(
        method instanceof ExecutableElement, "method must be an executable element: %s", method);
    return checkNotNull(
        dependenciesByDependencyMethod().get(method), "no dependency implements %s", method);
  }

  /**
   * The scopes of the component.
   */
  abstract ImmutableSet<Scope> scopes();

  /**
   * All {@link Subcomponent}s which are direct children of this component. This includes
   * subcomponents installed from {@link Module#subcomponents()} as well as subcomponent {@linkplain
   * #childComponentsDeclaredByFactoryMethods() factory methods} and {@linkplain
   * #childComponentsDeclaredByBuilderEntryPoints() builder methods}.
   */
  final ImmutableSet<ComponentDescriptor> childComponents() {
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
  abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByFactoryMethods();

  /** Returns a map of {@link #childComponents()} indexed by {@link #typeElement()}. */
  @Memoized
  ImmutableMap<TypeElement, ComponentDescriptor> childComponentsByElement() {
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

  private final Supplier<ImmutableMap<TypeElement, ComponentDescriptor>>
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
  final ComponentDescriptor getChildComponentWithBuilderType(TypeElement builderType) {
    return checkNotNull(
        childComponentsByBuilderType.get().get(builderType),
        "no child component found for builder type %s",
        builderType.getQualifiedName());
  }

  abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  /** Returns the first component method associated with this binding request, if one exists. */
  Optional<ComponentMethodDescriptor> firstMatchingComponentMethod(BindingRequest request) {
    return componentMethods().stream()
        .filter(method -> doesComponentMethodMatch(method, request))
        .findFirst();
  }

  /** Returns true if the component method matches the binding request. */
  private static boolean doesComponentMethodMatch(
      ComponentMethodDescriptor componentMethod, BindingRequest request) {
    return componentMethod
        .dependencyRequest()
        .map(BindingRequest::bindingRequest)
        .filter(request::equals)
        .isPresent();
  }

  /** The entry point methods on the component type. Each has a {@link DependencyRequest}. */
  final ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods()
        .stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation
  /** Returns a descriptor for the creator type for this component type, if the user defined one. */
  abstract Optional<ComponentCreatorDescriptor> creatorDescriptor();

  /**
   * Returns {@code true} for components that have a creator, either because the user {@linkplain
   * #creatorDescriptor() specified one} or because it's a top-level component with an implicit
   * builder.
   */
  final boolean hasCreator() {
    return !isSubcomponent() || creatorDescriptor().isPresent();
  }

  /**
   * Returns the {@link CancellationPolicy} for this component, or an empty optional if either the
   * component is not a production component or no {@code CancellationPolicy} annotation is present.
   */
  final Optional<CancellationPolicy> cancellationPolicy() {
    return isProduction()
        ? Optional.ofNullable(typeElement().getAnnotation(CancellationPolicy.class))
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
  abstract static class ComponentMethodDescriptor {
    /** The method itself. Note that this may be declared on a supertype of the component. */
    abstract ExecutableElement methodElement();

    /**
     * The dependency request for production, provision, and subcomponent creator methods. Absent
     * for subcomponent factory methods.
     */
    abstract Optional<DependencyRequest> dependencyRequest();

    /** The subcomponent for subcomponent factory methods and subcomponent creator methods. */
    abstract Optional<ComponentDescriptor> subcomponent();

    /**
     * Returns the return type of {@link #methodElement()} as resolved in the {@link
     * ComponentDescriptor#typeElement() component type}. If there are no type variables in the
     * return type, this is the equivalent of {@code methodElement().getReturnType()}.
     */
    TypeMirror resolvedReturnType(DaggerTypes types) {
      checkState(dependencyRequest().isPresent());

      TypeMirror returnType = methodElement().getReturnType();
      if (returnType.getKind().isPrimitive() || returnType.getKind().equals(VOID)) {
        return returnType;
      }
      return BindingRequest.bindingRequest(dependencyRequest().get())
          .requestedType(dependencyRequest().get().key().type(), types);
    }

    /** A {@link ComponentMethodDescriptor}builder for a method. */
    static Builder builder(ExecutableElement method) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor.Builder()
          .methodElement(method);
    }

    /** A builder of {@link ComponentMethodDescriptor}s. */
    @AutoValue.Builder
    @CanIgnoreReturnValue
    interface Builder {
      /** @see ComponentMethodDescriptor#methodElement() */
      Builder methodElement(ExecutableElement methodElement);

      /** @see ComponentMethodDescriptor#dependencyRequest() */
      Builder dependencyRequest(DependencyRequest dependencyRequest);

      /** @see ComponentMethodDescriptor#subcomponent() */
      Builder subcomponent(ComponentDescriptor subcomponent);

      /** Builds the descriptor. */
      @CheckReturnValue
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
  static boolean isComponentContributionMethod(DaggerElements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class).equals(method.getEnclosingElement())
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString());
  }

  /** Returns {@code true} if a method could be a component production entry point. */
  static boolean isComponentProductionMethod(DaggerElements elements, ExecutableElement method) {
    return isComponentContributionMethod(elements, method) && isFutureType(method.getReturnType());
  }
}
