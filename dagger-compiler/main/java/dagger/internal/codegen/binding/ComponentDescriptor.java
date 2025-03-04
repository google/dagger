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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotations;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Scopes.productionScope;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypeNames.isFutureType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
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
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XTypeElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

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
  private BindingFactory bindingFactory;

  /** The annotation that specifies that {@link #typeElement()} is a component. */
  public abstract ComponentAnnotation annotation();

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

  /**
   * The {@link ModuleDescriptor modules} declared in {@link Component#modules()} and reachable by
   * traversing {@link Module#includes()}.
   */
  public abstract ImmutableSet<ModuleDescriptor> modules();

  /** The scopes of the component. */
  public abstract ImmutableSet<Scope> scopes();

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

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  abstract ImmutableMap<ComponentMethodDescriptor, ComponentDescriptor>
      childComponentsDeclaredByBuilderEntryPoints();

  public abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  /** Returns a descriptor for the creator type for this component type, if the user defined one. */
  public abstract Optional<ComponentCreatorDescriptor> creatorDescriptor();

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

  /** The non-abstract {@link #modules()} and the {@link #dependencies()}. */
  public final ImmutableSet<ComponentRequirement> dependenciesAndConcreteModules() {
    return Stream.concat(
            moduleTypes().stream()
                .filter(dep -> !dep.isAbstract())
                .map(module -> ComponentRequirement.forModule(module.getType())),
            dependencies().stream())
        .collect(toImmutableSet());
  }

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
   * Returns this component's dependencies keyed by its provision/production method.
   *
   * <p>Note that the dependencies' types are not simply the enclosing type of the method; a method
   * may be declared by a supertype of the actual dependency.
   */
  @Memoized
  public ImmutableMap<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod() {
    ImmutableMap.Builder<XMethodElement, ComponentRequirement> builder = ImmutableMap.builder();
    for (ComponentRequirement componentDependency : dependencies()) {
      XTypeElements.getAllMethods(componentDependency.typeElement()).stream()
          .filter(ComponentDescriptor::isComponentContributionMethod)
          .forEach(method -> builder.put(method, componentDependency));
    }
    return builder.buildOrThrow();
  }

  /** The {@linkplain #dependencies() component dependency} that defines a method. */
  public final ComponentRequirement getDependencyThatDefinesMethod(XElement method) {
    checkArgument(isMethod(method), "method must be an executable element: %s", method);
    checkState(
        dependenciesByDependencyMethod().containsKey(method),
        "no dependency implements %s",
        method);
    return dependenciesByDependencyMethod().get(method);
  }

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

  /** The entry point methods on the component type. Each has a {@link DependencyRequest}. */
  public final ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods().stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

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
        ? Optional.ofNullable(typeElement().getAnnotation(XTypeNames.CANCELLATION_POLICY))
            .map(CancellationPolicy::from)
        : Optional.empty();
  }

  /** Returns the bindings for the component. */
  @Memoized
  public ImmutableSet<ContributionBinding> bindings() {
    ImmutableSet.Builder<ContributionBinding> builder = ImmutableSet.builder();
    componentBinding().ifPresent(builder::add);
    return builder
        .addAll(componentDependencyBindings())
        .addAll(boundInstanceBindings())
        .addAll(subcomponentCreatorBindings())
        .addAll(moduleBindings())
        .build();
  }

  /** Returns the binding for the component, itself, if this is a real component. */
  @Memoized
  Optional<ContributionBinding> componentBinding() {
    return isRealComponent()
        ? Optional.of(bindingFactory.componentBinding(typeElement()))
        : Optional.empty();
  }

  /** Returns the bindings for the component dependency and those contributed by its methods. */
  @Memoized
  ImmutableSet<ContributionBinding> componentDependencyBindings() {
    ImmutableSet.Builder<ContributionBinding> builder = ImmutableSet.builder();
    for (ComponentRequirement dependency : dependencies()) {
      builder.add(bindingFactory.componentDependencyBinding(dependency));

      // Within a component dependency, we want to allow the same method to appear multiple
      // times assuming it is the exact same method. We do this by tracking a set of bindings
      // we've already added with the binding element removed since that is the only thing
      // allowed to differ.
      HashMultimap<String, ContributionBinding> dedupeBindings = HashMultimap.create();
      XTypeElements.getAllMethods(dependency.typeElement()).stream()
          // MembersInjection methods aren't "provided" explicitly, so ignore them.
          .filter(ComponentDescriptor::isComponentContributionMethod)
          .forEach(
              method -> {
                ContributionBinding binding =
                    isProduction() && isComponentProductionMethod(method)
                        ? bindingFactory.componentDependencyProductionMethodBinding(method)
                        : bindingFactory.componentDependencyProvisionMethodBinding(method);
                if (dedupeBindings.put(
                    getSimpleName(method),
                    // Remove the binding element since we know that will be different, but
                    // everything else we want to be the same to consider it a duplicate.
                    binding.toBuilder().clearBindingElement().build())) {
                  builder.add(binding);
                }
              });
    }
    return builder.build();
  }

  /** Returns the {@code @BindsInstance} bindings required to create this component. */
  @Memoized
  ImmutableSet<ContributionBinding> boundInstanceBindings() {
    return creatorDescriptor().isPresent()
        ? creatorDescriptor().get().boundInstanceRequirements().stream()
            .map(
                requirement ->
                    bindingFactory.boundInstanceBinding(
                        requirement,
                        creatorDescriptor().get().elementForRequirement(requirement)))
            .collect(toImmutableSet())
        : ImmutableSet.of();
  }

  /** Returns the subcomponent creator bindings for this component. */
  @Memoized
  ImmutableSet<ContributionBinding> subcomponentCreatorBindings() {
    ImmutableSet.Builder<ContributionBinding> builder = ImmutableSet.builder();
    childComponentsDeclaredByBuilderEntryPoints()
        .forEach(
            (builderEntryPoint, childComponent) -> {
              if (!childComponentsDeclaredByModules().contains(childComponent)) {
                builder.add(
                    bindingFactory.subcomponentCreatorBinding(
                        builderEntryPoint.methodElement(), typeElement()));
              }
            });
    return builder.build();
  }

  @Memoized
  ImmutableSet<ContributionBinding> moduleBindings() {
    return modules().stream()
        .map(ModuleDescriptor::bindings)
        .flatMap(ImmutableSet::stream)
        .collect(toImmutableSet());
  }

  @Memoized
  public ImmutableSet<DelegateDeclaration> delegateDeclarations() {
    return modules().stream()
        .map(ModuleDescriptor::delegateDeclarations)
        .flatMap(ImmutableSet::stream)
        .collect(toImmutableSet());
  }

  @Memoized
  public ImmutableSet<MultibindingDeclaration> multibindingDeclarations() {
    return modules().stream()
        .map(ModuleDescriptor::multibindingDeclarations)
        .flatMap(ImmutableSet::stream)
        .collect(toImmutableSet());
  }

  @Memoized
  public ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations() {
    return modules().stream()
        .map(ModuleDescriptor::optionalDeclarations)
        .flatMap(ImmutableSet::stream)
        .collect(toImmutableSet());
  }

  @Memoized
  public ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations() {
    return modules().stream()
        .map(ModuleDescriptor::subcomponentDeclarations)
        .flatMap(ImmutableSet::stream)
        .collect(toImmutableSet());
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
  private static boolean isComponentContributionMethod(XMethodElement method) {
    return method.getParameters().isEmpty()
        && !isVoid(method.getReturnType())
        && !method.getEnclosingElement().getClassName().equals(TypeName.OBJECT)
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(getSimpleName(method));
  }

  /** Returns {@code true} if a method could be a component production entry point. */
  private static boolean isComponentProductionMethod(XMethodElement method) {
    return isComponentContributionMethod(method) && isFutureType(method.getReturnType());
  }

  /** A factory for creating a {@link ComponentDescriptor}. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final XProcessingEnv processingEnv;
    private final BindingFactory bindingFactory;
    private final DependencyRequestFactory dependencyRequestFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;
    private final InjectionAnnotations injectionAnnotations;
    private final DaggerSuperficialValidation superficialValidation;
    private final Map<XTypeElement, ComponentDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        BindingFactory bindingFactory,
        DependencyRequestFactory dependencyRequestFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory,
        InjectionAnnotations injectionAnnotations,
        DaggerSuperficialValidation superficialValidation) {
      this.processingEnv = processingEnv;
      this.bindingFactory = bindingFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
      this.injectionAnnotations = injectionAnnotations;
      this.superficialValidation = superficialValidation;
    }

    /** Returns a descriptor for a root component type. */
    public ComponentDescriptor rootComponentDescriptor(XTypeElement typeElement) {
      Optional<ComponentAnnotation> annotation =
          rootComponentAnnotation(typeElement, superficialValidation);
      checkArgument(annotation.isPresent(), "%s must have a component annotation", typeElement);
      return create(typeElement, annotation.get());
    }

    /** Returns a descriptor for a subcomponent type. */
    public ComponentDescriptor subcomponentDescriptor(XTypeElement typeElement) {
      Optional<ComponentAnnotation> annotation =
          subcomponentAnnotation(typeElement, superficialValidation);
      checkArgument(annotation.isPresent(), "%s must have a subcomponent annotation", typeElement);
      return create(typeElement, annotation.get());
    }

    /**
     * Returns a descriptor for a fictional component based on a module type in order to validate
     * its bindings.
     */
    public ComponentDescriptor moduleComponentDescriptor(XTypeElement typeElement) {
      Optional<ModuleAnnotation> annotation = moduleAnnotation(typeElement, superficialValidation);
      checkArgument(annotation.isPresent(), "%s must have a module annotation", typeElement);
      return create(typeElement, ComponentAnnotation.fromModuleAnnotation(annotation.get()));
    }

    private ComponentDescriptor create(
        XTypeElement typeElement, ComponentAnnotation componentAnnotation) {
      return reentrantComputeIfAbsent(
          cache, typeElement, unused -> createUncached(typeElement, componentAnnotation));
    }

    private ComponentDescriptor createUncached(
        XTypeElement typeElement, ComponentAnnotation componentAnnotation) {
      ImmutableSet<ComponentRequirement> componentDependencies =
          componentAnnotation.dependencyTypes().stream()
              .map(ComponentRequirement::forDependency)
              .collect(toImmutableSet());

      // Start with the component's modules. For fictional components built from a module, start
      // with that module.
      ImmutableSet<XTypeElement> modules =
          componentAnnotation.isRealComponent()
              ? componentAnnotation.modules()
              : ImmutableSet.of(typeElement);

      ImmutableSet<ModuleDescriptor> transitiveModules =
          moduleDescriptorFactory.transitiveModules(modules);

      ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
          ImmutableSet.builder();
      ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByFactoryMethod = ImmutableBiMap.builder();
      ImmutableMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByBuilderMethod = ImmutableBiMap.builder();
      if (componentAnnotation.isRealComponent()) {
        for (XMethodElement componentMethod : getAllUnimplementedMethods(typeElement)) {
          ComponentMethodDescriptor componentMethodDescriptor =
              getDescriptorForComponentMethod(componentAnnotation, typeElement, componentMethod);
          componentMethodsBuilder.add(componentMethodDescriptor);
          componentMethodDescriptor
              .subcomponent()
              .ifPresent(
                  subcomponent -> {
                    // If the dependency request is present, that means the method returns the
                    // subcomponent factory.
                    if (componentMethodDescriptor.dependencyRequest().isPresent()) {
                      subcomponentsByBuilderMethod.put(componentMethodDescriptor, subcomponent);
                    } else {
                      subcomponentsByFactoryMethod.put(componentMethodDescriptor, subcomponent);
                    }
                  });
        }
      }

      // Validation should have ensured that this set will have at most one element.
      ImmutableSet<XTypeElement> enclosedCreators =
          enclosedAnnotatedTypes(typeElement, creatorAnnotationsFor(componentAnnotation));
      Optional<ComponentCreatorDescriptor> creatorDescriptor =
          enclosedCreators.isEmpty()
              ? Optional.empty()
              : Optional.of(
                  ComponentCreatorDescriptor.create(
                      getOnlyElement(enclosedCreators), dependencyRequestFactory));

      ImmutableSet<Scope> scopes = injectionAnnotations.getScopes(typeElement);
      if (componentAnnotation.isProduction()) {
        scopes =
            ImmutableSet.<Scope>builder()
                .addAll(scopes).add(productionScope(processingEnv))
                .build();
      }

      ImmutableSet<ComponentDescriptor> subcomponentsFromModules =
        transitiveModules.stream()
            .flatMap(transitiveModule -> transitiveModule.subcomponentDeclarations().stream())
            .map(SubcomponentDeclaration::subcomponentType)
            .map(this::subcomponentDescriptor)
            .collect(toImmutableSet());

      ComponentDescriptor componentDescriptor =
          new AutoValue_ComponentDescriptor(
              componentAnnotation,
              typeElement,
              componentDependencies,
              transitiveModules,
              scopes,
              subcomponentsFromModules,
              subcomponentsByFactoryMethod.buildOrThrow(),
              subcomponentsByBuilderMethod.buildOrThrow(),
              componentMethodsBuilder.build(),
              creatorDescriptor);
      componentDescriptor.bindingFactory = bindingFactory;
      return componentDescriptor;
    }

    private ComponentMethodDescriptor getDescriptorForComponentMethod(
        ComponentAnnotation componentAnnotation,
        XTypeElement componentElement,
        XMethodElement componentMethod) {
      ComponentMethodDescriptor.Builder descriptor =
          ComponentMethodDescriptor.builder(componentMethod);

      XMethodType resolvedComponentMethod = componentMethod.asMemberOf(componentElement.getType());
      XType returnType = resolvedComponentMethod.getReturnType();
      if (isDeclared(returnType)
              && !injectionAnnotations.getQualifier(componentMethod).isPresent()) {
        XTypeElement returnTypeElement = returnType.getTypeElement();
        if (hasAnyAnnotation(returnTypeElement, subcomponentAnnotations())) {
          // It's a subcomponent factory method. There is no dependency request, and there could be
          // any number of parameters. Just return the descriptor.
          return descriptor.subcomponent(subcomponentDescriptor(returnTypeElement)).build();
        }
        if (isSubcomponentCreator(returnTypeElement)) {
          descriptor.subcomponent(
              subcomponentDescriptor(returnTypeElement.getEnclosingTypeElement()));
        }
      }

      switch (componentMethod.getParameters().size()) {
        case 0:
          checkArgument(
              !isVoid(returnType), "component method cannot be void: %s", componentMethod);
          descriptor.dependencyRequest(
              componentAnnotation.isProduction()
                  ? dependencyRequestFactory.forComponentProductionMethod(
                      componentMethod, resolvedComponentMethod)
                  : dependencyRequestFactory.forComponentProvisionMethod(
                      componentMethod, resolvedComponentMethod));
          break;

        case 1:
          checkArgument(
              isVoid(returnType)
                  // TODO(bcorso): Replace this with isSameType()?
                  || returnType
                      .getTypeName()
                      .equals(resolvedComponentMethod.getParameterTypes().get(0).getTypeName()),
              "members injection method must return void or parameter type: %s",
              componentMethod);
          descriptor.dependencyRequest(
              dependencyRequestFactory.forComponentMembersInjectionMethod(
                  componentMethod, resolvedComponentMethod));
          break;

        default:
          throw new IllegalArgumentException(
              "component method has too many parameters: " + componentMethod);
      }

      return descriptor.build();
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}
