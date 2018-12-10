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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponent;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerTypes.isFutureType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Scopes.productionScope;
import static dagger.internal.codegen.Scopes.scopesOf;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Subcomponent;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.model.Scope;
import dagger.producers.CancellationPolicy;
import dagger.producers.ProductionComponent;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
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
  /** The kind of the component. */
  abstract ComponentKind kind();

  /** The annotation that specifies that {@link #typeElement()} is a component. */
  abstract AnnotationMirror annotation();

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

  /** The entry point methods on the component type. */
  final ImmutableSet<ComponentMethodDescriptor> entryPointMethods() {
    return componentMethods()
        .stream()
        .filter(method -> method.dependencyRequest().isPresent())
        .collect(toImmutableSet());
  }

  /**
   * The entry points.
   *
   * <p>For descriptors that are generated from a module in order to validate the module's bindings,
   * these will be requests for every key for every binding declared in the module (erasing
   * multibinding contribution identifiers so that we get the multibinding key).
   *
   * <p>In order not to trigger a validation error if the requested binding is nullable, each
   * request will be nullable.
   *
   * <p>In order not to trigger a validation error if the requested binding is a production binding,
   * each request will be for a {@link com.google.common.util.concurrent.ListenableFuture} of the
   * key type.
   */
  final ImmutableSet<DependencyRequest> entryPoints() {
    if (kind().isForModuleValidation()) {
      return modules().stream()
          .flatMap(module -> module.allBindingKeys().stream())
          .map(key -> key.toBuilder().multibindingContributionIdentifier(Optional.empty()).build())
          .map(
              key ->
                  DependencyRequest.builder()
                      .key(key)
                      // TODO(dpb): Futures only in ProducerModules, instances elsewhere?
                      .kind(RequestKind.FUTURE)
                      .isNullable(true)
                      .build())
          .collect(toImmutableSet());
    } else {
      return entryPointMethods().stream()
          .map(method -> method.dependencyRequest().get())
          .collect(toImmutableSet());
    }
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
    return kind().isTopLevel() || creatorDescriptor().isPresent();
  }

  /**
   * Returns the {@link CancellationPolicy} for this component, or an empty optional if either the
   * component is not a production component or no {@code CancellationPolicy} annotation is present.
   */
  final Optional<CancellationPolicy> cancellationPolicy() {
    return kind().isProducer()
        ? Optional.ofNullable(typeElement().getAnnotation(CancellationPolicy.class))
        : Optional.empty();
  }

  /** A function that returns all {@link #scopes()} of its input. */
  @AutoValue
  abstract static class ComponentMethodDescriptor {
    abstract ComponentMethodKind kind();
    abstract Optional<DependencyRequest> dependencyRequest();
    abstract ExecutableElement methodElement();

    static ComponentMethodDescriptor create(
        ComponentMethodKind kind,
        Optional<DependencyRequest> dependencyRequest,
        ExecutableElement methodElement) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
          kind, dependencyRequest, methodElement);
    }

    static ComponentMethodDescriptor forProvision(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(ComponentMethodKind.PROVISION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forMembersInjection(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(
          ComponentMethodKind.MEMBERS_INJECTION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forSubcomponent(
        ComponentMethodKind kind, ExecutableElement methodElement) {
      return create(kind, Optional.empty(), methodElement);
    }

    static ComponentMethodDescriptor forSubcomponentCreator(
        ComponentMethodKind kind,
        DependencyRequest dependencyRequestForBuilder,
        ExecutableElement methodElement) {
      return create(kind, Optional.of(dependencyRequestForBuilder), methodElement);
    }

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
  }

  enum ComponentMethodKind {
    PROVISION,
    PRODUCTION,
    MEMBERS_INJECTION,
    SUBCOMPONENT,
    SUBCOMPONENT_BUILDER,
    PRODUCTION_SUBCOMPONENT,
    PRODUCTION_SUBCOMPONENT_BUILDER;

    /**
     * Returns the component kind associated with this component method, if it exists. Otherwise,
     * throws.
     */
    ComponentKind componentKind() {
      switch (this) {
        case SUBCOMPONENT:
        case SUBCOMPONENT_BUILDER:
          return ComponentKind.SUBCOMPONENT;
        case PRODUCTION_SUBCOMPONENT:
        case PRODUCTION_SUBCOMPONENT_BUILDER:
          return ComponentKind.PRODUCTION_SUBCOMPONENT;
        default:
          throw new IllegalStateException("no component associated with method " + this);
      }
    }
  }

  static final class Factory {
    private final DaggerElements elements;
    private final DaggerTypes types;
    private final DependencyRequestFactory dependencyRequestFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;
    private final CompilerOptions compilerOptions;

    @Inject
    Factory(
        DaggerElements elements,
        DaggerTypes types,
        DependencyRequestFactory dependencyRequestFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory,
        CompilerOptions compilerOptions) {
      this.elements = elements;
      this.types = types;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
      this.compilerOptions = compilerOptions;
    }

    /**
     * Returns a component descriptor for a type.
     *
     * <p>The type must be annotated with a top-level component annotation unless ahead-of-time
     * subcomponents are being generated or we are creating a descriptor for a module in order to
     * validate its bindings.
     */
    ComponentDescriptor forTypeElement(TypeElement typeElement) {
      Optional<ComponentKind> kind = ComponentKind.forAnnotatedElement(typeElement);
      checkArgument(
          kind.isPresent(),
          "%s must have a component or subcomponent or module annotation",
          typeElement);
      if (!compilerOptions.aheadOfTimeSubcomponents()) {
        checkArgument(kind.get().isTopLevel(), "%s must be a top-level component.", typeElement);
      }
      return create(typeElement, kind.get());
    }

    private ComponentDescriptor create(TypeElement typeElement, ComponentKind kind) {
      AnnotationMirror componentAnnotation =
          getAnnotationMirror(typeElement, kind.annotation()).get();
      DeclaredType declaredComponentType = MoreTypes.asDeclared(typeElement.asType());
      ImmutableSet<ComponentRequirement> componentDependencies =
          kind.isTopLevel() && !kind.isForModuleValidation()
              ? getComponentDependencies(componentAnnotation).stream()
                  .map(ComponentRequirement::forDependency)
                  .collect(toImmutableSet())
              : ImmutableSet.of();

      ImmutableMap.Builder<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod =
          ImmutableMap.builder();

      for (ComponentRequirement componentDependency : componentDependencies) {
        for (ExecutableElement dependencyMethod :
            methodsIn(elements.getAllMembers(componentDependency.typeElement()))) {
          if (isComponentContributionMethod(elements, dependencyMethod)) {
            dependenciesByDependencyMethod.put(dependencyMethod, componentDependency);
          }
        }
      }

      ImmutableSet<TypeElement> modules =
          kind.isForModuleValidation()
              ? ImmutableSet.of(typeElement)
              : getComponentModules(componentAnnotation).stream()
                  .map(MoreTypes::asTypeElement)
                  .collect(toImmutableSet());

      ImmutableSet<ModuleDescriptor> transitiveModules =
          moduleDescriptorFactory.transitiveModules(modules);

      ImmutableSet.Builder<ComponentDescriptor> subcomponentsFromModules = ImmutableSet.builder();
      for (ModuleDescriptor module : transitiveModules) {
        for (SubcomponentDeclaration subcomponentDeclaration : module.subcomponentDeclarations()) {
          TypeElement subcomponent = subcomponentDeclaration.subcomponentType();
          subcomponentsFromModules.add(
              create(subcomponent, ComponentKind.forAnnotatedElement(subcomponent).get()));
        }
      }

      ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
          ImmutableSet.builder();
      ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByFactoryMethod = ImmutableBiMap.builder();
      ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByBuilderMethod = ImmutableBiMap.builder();
      if (!kind.isForModuleValidation()) {
        ImmutableSet<ExecutableElement> unimplementedMethods =
            elements.getUnimplementedMethods(typeElement);
        for (ExecutableElement componentMethod : unimplementedMethods) {
          ExecutableType resolvedMethod =
              MoreTypes.asExecutable(types.asMemberOf(declaredComponentType, componentMethod));
          ComponentMethodDescriptor componentMethodDescriptor =
              getDescriptorForComponentMethod(typeElement, kind, componentMethod);
          componentMethodsBuilder.add(componentMethodDescriptor);
          switch (componentMethodDescriptor.kind()) {
            case SUBCOMPONENT:
            case PRODUCTION_SUBCOMPONENT:
              subcomponentsByFactoryMethod.put(
                  componentMethodDescriptor,
                  create(
                      MoreElements.asType(MoreTypes.asElement(resolvedMethod.getReturnType())),
                      componentMethodDescriptor.kind().componentKind()));
              break;

            case SUBCOMPONENT_BUILDER:
            case PRODUCTION_SUBCOMPONENT_BUILDER:
              subcomponentsByBuilderMethod.put(
                  componentMethodDescriptor,
                  create(
                      MoreElements.asType(
                          MoreTypes.asElement(resolvedMethod.getReturnType())
                              .getEnclosingElement()),
                      componentMethodDescriptor.kind().componentKind()));
              break;

            default: // nothing special to do for other methods.
          }
        }
      }

      ImmutableList<DeclaredType> enclosedCreators =
          kind.builderAnnotation()
              .map(builderAnnotation -> enclosedAnnotatedTypes(typeElement, builderAnnotation))
              .orElse(ImmutableList.of());
      Optional<ComponentCreatorDescriptor> creatorDescriptor =
          enclosedCreators.isEmpty()
              ? Optional.empty()
              : Optional.of(
                  ComponentCreatorDescriptor.create(
                      getOnlyElement(enclosedCreators), elements, types, dependencyRequestFactory));

      ImmutableSet<Scope> scopes = scopesOf(typeElement);
      if (kind.isProducer()) {
        scopes =
            ImmutableSet.<Scope>builder().addAll(scopes).add(productionScope(elements)).build();
      }

      return new AutoValue_ComponentDescriptor(
          kind,
          componentAnnotation,
          typeElement,
          componentDependencies,
          transitiveModules,
          dependenciesByDependencyMethod.build(),
          scopes,
          subcomponentsFromModules.build(),
          subcomponentsByFactoryMethod.build(),
          subcomponentsByBuilderMethod.build(),
          componentMethodsBuilder.build(),
          creatorDescriptor);
    }

    private ComponentMethodDescriptor getDescriptorForComponentMethod(
        TypeElement componentElement,
        ComponentKind componentKind,
        ExecutableElement componentMethod) {
      ExecutableType resolvedComponentMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
      TypeMirror returnType = resolvedComponentMethod.getReturnType();
      if (returnType.getKind().equals(DECLARED)) {
        if (MoreTypes.isTypeOf(Provider.class, returnType)
            || MoreTypes.isTypeOf(Lazy.class, returnType)) {
          return ComponentMethodDescriptor.forProvision(
              componentMethod,
              dependencyRequestFactory.forComponentProvisionMethod(
                  componentMethod, resolvedComponentMethod));
        } else if (!getQualifier(componentMethod).isPresent()) {
          Element returnTypeElement = MoreTypes.asElement(returnType);
          if (isSubcomponent(returnTypeElement)) {
            return ComponentMethodDescriptor.forSubcomponent(
                isAnnotationPresent(returnTypeElement, Subcomponent.class)
                    ? ComponentMethodKind.SUBCOMPONENT
                    : ComponentMethodKind.PRODUCTION_SUBCOMPONENT,
                componentMethod);
          } else if (isSubcomponentCreator(returnTypeElement)) {
            DependencyRequest dependencyRequest =
                dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod);
            return ComponentMethodDescriptor.forSubcomponentCreator(
                isAnnotationPresent(returnTypeElement, Subcomponent.Builder.class)
                    ? ComponentMethodKind.SUBCOMPONENT_BUILDER
                    : ComponentMethodKind.PRODUCTION_SUBCOMPONENT_BUILDER,
                dependencyRequest,
                componentMethod);
          }
        }
      }

      // a typical provision method
      if (componentMethod.getParameters().isEmpty()
          && !componentMethod.getReturnType().getKind().equals(VOID)) {
        switch (componentKind) {
          case COMPONENT:
          case SUBCOMPONENT:
            return ComponentMethodDescriptor.forProvision(
                componentMethod,
                dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod));
          case PRODUCTION_COMPONENT:
          case PRODUCTION_SUBCOMPONENT:
            return ComponentMethodDescriptor.forProvision(
                componentMethod,
                dependencyRequestFactory.forComponentProductionMethod(
                    componentMethod, resolvedComponentMethod));
          default:
            throw new AssertionError();
        }
      }

      List<? extends TypeMirror> parameterTypes = resolvedComponentMethod.getParameterTypes();
      if (parameterTypes.size() == 1
          && (returnType.getKind().equals(VOID)
              || MoreTypes.equivalence().equivalent(returnType, parameterTypes.get(0)))) {
        return ComponentMethodDescriptor.forMembersInjection(
            componentMethod,
            dependencyRequestFactory.forComponentMembersInjectionMethod(
                componentMethod, resolvedComponentMethod));
      }

      throw new IllegalArgumentException("not a valid component method: " + componentMethod);
    }
  }

  /**
   * No-argument methods defined on {@link Object} that are ignored for contribution.
   */
  private static final ImmutableSet<String> NON_CONTRIBUTING_OBJECT_METHOD_NAMES =
      ImmutableSet.of("toString", "hashCode", "clone", "getClass");

  static boolean isComponentContributionMethod(DaggerElements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class).equals(method.getEnclosingElement())
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString());
  }

  static boolean isComponentProductionMethod(DaggerElements elements, ExecutableElement method) {
    return isComponentContributionMethod(elements, method) && isFutureType(method.getReturnType());
  }
}
