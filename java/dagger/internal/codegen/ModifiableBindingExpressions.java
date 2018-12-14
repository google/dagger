/*
 * Copyright (C) 2016 The Dagger Authors.
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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.Scope;
import java.util.Optional;

/**
 * A central repository of code expressions used to access modifiable bindings available to a
 * component. A binding is modifiable if it can be modified across implementations of a
 * subcomponent. This is only relevant for ahead-of-time subcomponents.
 */
final class ModifiableBindingExpressions {
  private final Optional<ModifiableBindingExpressions> parent;
  private final ComponentBindingExpressions bindingExpressions;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final CompilerOptions compilerOptions;
  private final DaggerTypes types;

  ModifiableBindingExpressions(
      Optional<ModifiableBindingExpressions> parent,
      ComponentBindingExpressions bindingExpressions,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      CompilerOptions compilerOptions,
      DaggerTypes types) {
    this.parent = parent;
    this.bindingExpressions = bindingExpressions;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.compilerOptions = compilerOptions;
    this.types = types;
  }

  /**
   * Records the binding exposed by the given component method as modifiable, if it is, and returns
   * the {@link ModifiableBindingType} associated with the binding.
   */
  ModifiableBindingType registerComponentMethodIfModifiable(
      ComponentMethodDescriptor componentMethod, MethodSpec method) {
    BindingRequest request = bindingRequest(componentMethod.dependencyRequest().get());
    ModifiableBindingType modifiableBindingType = getModifiableBindingType(request);
    if (modifiableBindingType.isModifiable()) {
      componentImplementation.registerModifiableBindingMethod(
          modifiableBindingType,
          request,
          componentMethod.resolvedReturnType(types),
          method,
          newModifiableBindingWillBeFinalized(modifiableBindingType, request));
    }
    return modifiableBindingType;
  }

  /**
   * Returns the implementation of a modifiable binding method originally defined in a supertype
   * implementation of this subcomponent. Returns {@link Optional#empty()} when the binding cannot
   * or should not be modified by the current binding graph.
   */
  Optional<ModifiableBindingMethod> reimplementedModifiableBindingMethod(
      ModifiableBindingMethod modifiableBindingMethod) {
    checkState(componentImplementation.superclassImplementation().isPresent());
    ModifiableBindingType newModifiableBindingType =
        getModifiableBindingType(modifiableBindingMethod.request());
    ModifiableBindingType oldModifiableBindingType = modifiableBindingMethod.type();
    boolean modifiableBindingTypeChanged =
        !newModifiableBindingType.equals(oldModifiableBindingType);
    if (modifiableBindingTypeChanged
        && !newModifiableBindingType.hasBaseClassImplementation()
        && (oldModifiableBindingType.hasBaseClassImplementation()
            || componentImplementation.isAbstract())) {
      // We don't want to override one abstract method with another one. However, If the component
      // is not abstract (such as a transition from GENERATED_INSTANCE -> MISSING), we must provide
      // an implementation like normal.
      return Optional.empty();
    }

    if (modifiableBindingTypeChanged
        || shouldModifyImplementation(
            newModifiableBindingType, modifiableBindingMethod.request())) {
      MethodSpec baseMethod = modifiableBindingMethod.methodSpec();
      boolean markMethodFinal =
          knownModifiableBindingWillBeFinalized(modifiableBindingMethod)
              // no need to mark the method final if the component implementation will be final
              && componentImplementation.isAbstract();
      return Optional.of(
          ModifiableBindingMethod.implement(
              modifiableBindingMethod,
              MethodSpec.methodBuilder(baseMethod.name)
                  .addModifiers(baseMethod.modifiers.contains(PUBLIC) ? PUBLIC : PROTECTED)
                  .addModifiers(markMethodFinal ? ImmutableSet.of(FINAL) : ImmutableSet.of())
                  .returns(baseMethod.returnType)
                  .addAnnotation(Override.class)
                  .addCode(
                      bindingExpressions
                          .getBindingExpression(modifiableBindingMethod.request())
                          .getModifiableBindingMethodImplementation(
                              modifiableBindingMethod, componentImplementation, types))
                  .build(),
              markMethodFinal));
    }
    return Optional.empty();
  }

  /**
   * Returns true if a modifiable binding method that was registered in a superclass implementation
   * of this subcomponent should be marked as "finalized" if it is being overridden by this
   * subcomponent implementation. "Finalized" means we should not attempt to modify the binding in
   * any subcomponent subclass.
   */
  private boolean knownModifiableBindingWillBeFinalized(
      ModifiableBindingMethod modifiableBindingMethod) {
    ModifiableBindingType newModifiableBindingType =
        getModifiableBindingType(modifiableBindingMethod.request());
    if (!newModifiableBindingType.isModifiable()) {
      // If a modifiable binding has become non-modifiable it is final by definition.
      return true;
    }
    return modifiableBindingWillBeFinalized(
        newModifiableBindingType,
        shouldModifyImplementation(newModifiableBindingType, modifiableBindingMethod.request()));
  }

  /**
   * Returns true if a newly discovered modifiable binding method, once it is defined in this
   * subcomponent implementation, should be marked as "finalized", meaning we should not attempt to
   * modify the binding in any subcomponent subclass.
   */
  private boolean newModifiableBindingWillBeFinalized(
      ModifiableBindingType modifiableBindingType, BindingRequest request) {
    return modifiableBindingWillBeFinalized(
        modifiableBindingType, shouldModifyImplementation(modifiableBindingType, request));
  }

  /**
   * Returns true if we shouldn't attempt to further modify a modifiable binding once we complete
   * the implementation for the current subcomponent.
   */
  private boolean modifiableBindingWillBeFinalized(
      ModifiableBindingType modifiableBindingType, boolean modifyingBinding) {
    switch (modifiableBindingType) {
      case MISSING:
      case BINDS_METHOD_WITH_MISSING_DEPENDENCY:
      case GENERATED_INSTANCE:
      case OPTIONAL:
      case INJECTION:
        // Once we modify any of the above a single time, then they are finalized.
        return modifyingBinding;
      case PRODUCTION:
        // For production bindings, we know that the binding will be finalized if the parent is a
        // non-production component, but for @ProductionScope bindings we don't ever know because an
        // ancestor non-production component can apply @ProductionScope. We therefore return false
        // always. If we wanted, we could create a separate ModifiableBindingType for production
        // scope to allow us to make this distinction.
        return false;
      case MULTIBINDING:
        return false;
      default:
        throw new IllegalStateException(
            String.format(
                "Building binding expression for unsupported ModifiableBindingType [%s].",
                modifiableBindingType));
    }
  }

  /**
   * Creates a binding expression for a binding if it may be modified across implementations of a
   * subcomponent.
   */
  Optional<BindingExpression> maybeCreateModifiableBindingExpression(BindingRequest request) {
    ModifiableBindingType type = getModifiableBindingType(request);
    if (!type.isModifiable()) {
      return Optional.empty();
    }
    return Optional.of(createModifiableBindingExpression(type, request));
  }

  /** Creates a binding expression for a modifiable binding. */
  private BindingExpression createModifiableBindingExpression(
      ModifiableBindingType type, BindingRequest request) {
    ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
    Optional<ModifiableBindingMethod> matchingModifiableBindingMethod =
        componentImplementation.getModifiableBindingMethod(request);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.componentDescriptor().firstMatchingComponentMethod(request);
    switch (type) {
      case GENERATED_INSTANCE:
        // If the subcomponent is abstract then we need to define an (un-implemented)
        // DeferredModifiableBindingExpression.
        if (componentImplementation.isAbstract()) {
          return new DeferredModifiableBindingExpression(
              componentImplementation,
              type,
              resolvedBindings.contributionBinding(),
              request,
              matchingModifiableBindingMethod,
              matchingComponentMethod,
              types);
        }
        // Otherwise return a concrete implementation.
        return bindingExpressions.createBindingExpression(resolvedBindings, request);

      case MISSING:
        // If we need an expression for a missing binding and the current implementation is
        // abstract, then we need an (un-implemented) MissingBindingExpression.
        if (componentImplementation.isAbstract()) {
          return new MissingBindingExpression(
              componentImplementation,
              request,
              matchingModifiableBindingMethod,
              matchingComponentMethod,
              types);
        }
        // Otherwise we assume that it is valid to have a missing binding as it is part of a
        // dependency chain that has been passively pruned.
        // TODO(b/117833324): Identify pruned bindings when generating the subcomponent
        // implementation in which the bindings are pruned. If we hold a reference to the binding
        // graph used to generate a given implementation then we can compare a implementation's
        // graph with its superclass implementation's graph to detect pruned dependency branches.
        return new PrunedConcreteMethodBindingExpression();

      case BINDS_METHOD_WITH_MISSING_DEPENDENCY:
        checkState(componentImplementation.isAbstract());
        return new DeferredModifiableBindingExpression(
            componentImplementation,
            type,
            resolvedBindings.contributionBinding(),
            request,
            matchingModifiableBindingMethod,
            matchingComponentMethod,
            types);

      case OPTIONAL:
      case MULTIBINDING:
      case INJECTION:
      case PRODUCTION:
        return bindingExpressions.wrapInMethod(
            resolvedBindings,
            request,
            bindingExpressions.createBindingExpression(resolvedBindings, request));
      default:
        throw new IllegalStateException(
            String.format(
                "Building binding expression for unsupported ModifiableBindingType [%s].", type));
    }
  }

  /**
   * The reason why a binding may need to be modified across implementations of a subcomponent, if
   * at all.
   */
  ModifiableBindingType getModifiableBindingType(BindingRequest request) {
    if (!compilerOptions.aheadOfTimeSubcomponents()) {
      return ModifiableBindingType.NONE;
    }

    // When generating a component the binding is not considered modifiable. Bindings are modifiable
    // only across subcomponent implementations.
    if (componentImplementation.componentDescriptor().kind().isTopLevel()) {
      return ModifiableBindingType.NONE;
    }

    if (request.requestKind().filter(RequestKinds::isDerivedFromProvider).isPresent()) {
      return ModifiableBindingType.NONE;
    }

    if (resolvedInThisComponent(request)) {
      ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
      if (resolvedBindings.contributionBindings().isEmpty()) {
        // TODO(ronshapiro): Confirm whether a resolved binding must have a single contribution
        // binding.
        return ModifiableBindingType.NONE;
      }

      ContributionBinding binding = resolvedBindings.contributionBinding();
      if (binding.requiresGeneratedInstance()) {
        return ModifiableBindingType.GENERATED_INSTANCE;
      }

      if (binding.kind().equals(BindingKind.DELEGATE)
          && graph
              .contributionBindings()
              .get(getOnlyElement(binding.dependencies()).key())
              .isEmpty()) {
        return ModifiableBindingType.BINDS_METHOD_WITH_MISSING_DEPENDENCY;
      }

      if (binding.kind().equals(BindingKind.OPTIONAL) && binding.dependencies().isEmpty()) {
        // only empty optional bindings can be modified
        return ModifiableBindingType.OPTIONAL;
      }

      if (binding.isSyntheticMultibinding()) {
        return ModifiableBindingType.MULTIBINDING;
      }

      if (binding.kind().equals(BindingKind.INJECTION)) {
        return ModifiableBindingType.INJECTION;
      }

      if ((binding.scope().map(Scope::isProductionScope).orElse(false)
              && componentImplementation.isAbstract())
          || binding.bindingType().equals(BindingType.PRODUCTION)) {
        return ModifiableBindingType.PRODUCTION;
      }
    } else if (!resolvableBinding(request)) {
      return ModifiableBindingType.MISSING;
    }

    return ModifiableBindingType.NONE;
  }

  /**
   * Returns true if the current binding graph can, and should, modify a binding by overriding a
   * modifiable binding method.
   */
  private boolean shouldModifyImplementation(
      ModifiableBindingType modifiableBindingType, BindingRequest request) {
    if (request.requestKind().isPresent()) {
      switch (request.requestKind().get()) {
        case FUTURE:
          // Futures are always requested by a Producer.get() call, so if the binding is modifiable,
          // the producer will be wrapped in a modifiable method and the future can refer to that
          // method; even if the producer binding is modified, getModifiableProducer().get() will
          // never need to be modified. Furthermore, because cancellation is treated by wrapped
          // producers, and those producers point to the modifiable producer wrapper methods, we
          // never need or want to change the access of these wrapped producers for entry
          // methods
          return false;

        case LAZY:
        case PROVIDER_OF_LAZY:
          // Lazy and ProviderOfLazy are always created from a Provider, and therefore this request
          // never needs to be modifiable. It will refer (via DoubleCheck.lazy() or
          // ProviderOfLazy.create()) to the modifiable method and not the framework instance.
          return false;

        case MEMBERS_INJECTION:
        case PRODUCED:
          // MEMBERS_INJECTION has a completely different code path for binding expressions, and
          // PRODUCED requests are only requestable in @Produces methods, which are hidden from
          // generated components inside Producer factories
          throw new AssertionError(request);

        case INSTANCE:
        case PROVIDER:
        case PRODUCER:
          // These may be modifiable, so run through the regular logic. They're spelled out
          // explicitly so that ErrorProne will detect if a new enum value is created and missing
          // from this list.
          break;
      }
    }

    ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
    switch (modifiableBindingType) {
      case GENERATED_INSTANCE:
        return !componentImplementation.isAbstract();

      case MISSING:
        // TODO(b/117833324): investigate beder@'s comment about having intermediate component
        // ancestors satisfy missing bindings of their children with their own missing binding
        // methods so that we can minimize the cases where we need to reach into doubly-nested
        // descendant component implementations.

        // Implement a missing binding if it is resolvable, or if we're generating a concrete
        // subcomponent implementation. If a binding is still missing when the subcomponent
        // implementation is concrete then it is assumed to be part of a dependency that would have
        // been passively pruned when implementing the full component hierarchy.
        return resolvableBinding(request) || !componentImplementation.isAbstract();

      case BINDS_METHOD_WITH_MISSING_DEPENDENCY:
        DependencyRequest dependency =
            getOnlyElement(resolvedBindings.contributionBinding().dependencies());
        return !graph.contributionBindings().get(dependency.key()).isEmpty();

      case OPTIONAL:
        // Only override optional binding methods if we have a non-empty binding.
        return !resolvedBindings.contributionBinding().dependencies().isEmpty();

      case MULTIBINDING:
        // Only modify a multibinding if there are new contributions.
        return !componentImplementation
            .superclassContributionsMade(request)
            .containsAll(resolvedBindings.contributionBinding().dependencies());

      case INJECTION:
        return !resolvedBindings.contributionBinding().kind().equals(BindingKind.INJECTION);

      case PRODUCTION:
        // TODO(b/117833324): Profile this to see if this check is slow
        return !resolvedBindings
            .owningComponent()
            .equals(componentImplementation.componentDescriptor());

      default:
        throw new IllegalStateException(
            String.format(
                "Overriding modifiable binding method with unsupported ModifiableBindingType [%s].",
                modifiableBindingType));
    }
  }

  /**
   * Returns true if the binding can be resolved by the graph for this component or any parent
   * component.
   */
  private boolean resolvableBinding(BindingRequest request) {
    for (ModifiableBindingExpressions expressions = this;
        expressions != null;
        expressions = expressions.parent.orElse(null)) {
      if (expressions.resolvedInThisComponent(request)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the binding can be resolved by the graph for this component. */
  private boolean resolvedInThisComponent(BindingRequest request) {
    ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
    return resolvedBindings != null && !resolvedBindings.ownedBindings().isEmpty();
  }

  /**
   * Wraps a modifiable binding expression in a method that can be overridden in a subclass
   * implementation.
   */
  BindingExpression wrapInModifiableMethodBindingExpression(
      ContributionBinding binding,
      BindingRequest request,
      BindingMethodImplementation methodImplementation) {
    ModifiableBindingType modifiableBindingType = getModifiableBindingType(request);
    checkState(modifiableBindingType.isModifiable());
    return new ModifiableConcreteMethodBindingExpression(
        binding,
        request,
        modifiableBindingType,
        methodImplementation,
        componentImplementation,
        newModifiableBindingWillBeFinalized(modifiableBindingType, request),
        types);
  }
}
