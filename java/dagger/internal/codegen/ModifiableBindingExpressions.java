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

import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.model.BindingKind;
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
  private final GeneratedComponentModel generatedComponentModel;
  private final CompilerOptions compilerOptions;

  ModifiableBindingExpressions(
      Optional<ModifiableBindingExpressions> parent,
      ComponentBindingExpressions bindingExpressions,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      CompilerOptions compilerOptions) {
    this.parent = parent;
    this.bindingExpressions = bindingExpressions;
    this.graph = graph;
    this.generatedComponentModel = generatedComponentModel;
    this.compilerOptions = compilerOptions;
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
      generatedComponentModel.registerModifiableBindingMethod(
          modifiableBindingType,
          request,
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
  Optional<ModifiableBindingMethod> getModifiableBindingMethod(
      ModifiableBindingMethod modifiableBindingMethod) {
    if (shouldModifyKnownBinding(modifiableBindingMethod)) {
      MethodSpec baseMethod = modifiableBindingMethod.methodSpec();
      return Optional.of(
          ModifiableBindingMethod.implement(
              modifiableBindingMethod,
              MethodSpec.methodBuilder(baseMethod.name)
                  .addModifiers(PUBLIC)
                  // TODO(b/72748365): Mark method as final if binding should not be further
                  // modified.
                  .returns(baseMethod.returnType)
                  .addAnnotation(Override.class)
                  .addCode(
                      bindingExpressions
                          .getBindingExpression(modifiableBindingMethod.request())
                          .getModifiableBindingMethodImplementation(
                              modifiableBindingMethod, generatedComponentModel))
                  .build(),
              knownModifiableBindingWillBeFinalized(modifiableBindingMethod)));
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
        shouldModifyBinding(newModifiableBindingType, modifiableBindingMethod.request()));
  }

  /**
   * Returns true if a newly discovered modifiable binding method, once it is defined in this
   * subcomponent implementation, should be marked as "finalized", meaning we should not attempt to
   * modify the binding in any subcomponent subclass.
   */
  private boolean newModifiableBindingWillBeFinalized(
      ModifiableBindingType modifiableBindingType, BindingRequest request) {
    return modifiableBindingWillBeFinalized(
        modifiableBindingType, shouldModifyBinding(modifiableBindingType, request));
  }

  /**
   * Returns true if we shouldn't attempt to further modify a modifiable binding once we complete
   * the implementation for the current subcomponent.
   */
  private boolean modifiableBindingWillBeFinalized(
      ModifiableBindingType modifiableBindingType, boolean modifyingBinding) {
    switch (modifiableBindingType) {
      case MISSING:
      case GENERATED_INSTANCE:
      case OPTIONAL:
      case INJECTION:
        // Once we modify any of the above a single time, then they are finalized.
        return modifyingBinding;
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
        generatedComponentModel.getModifiableBindingMethod(request);
    Optional<ComponentMethodDescriptor> matchingComponentMethod =
        graph.componentDescriptor().findMatchingComponentMethod(request);
    switch (type) {
      case GENERATED_INSTANCE:
        return new GeneratedInstanceBindingExpression(
            generatedComponentModel,
            resolvedBindings,
            request,
            matchingModifiableBindingMethod,
            matchingComponentMethod);
      case MISSING:
        return new MissingBindingExpression(
            generatedComponentModel,
            request,
            matchingModifiableBindingMethod,
            matchingComponentMethod);
      case OPTIONAL:
      case MULTIBINDING:
      case INJECTION:
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
  private ModifiableBindingType getModifiableBindingType(BindingRequest request) {
    if (!compilerOptions.aheadOfTimeSubcomponents()) {
      return ModifiableBindingType.NONE;
    }

    // When generating a final (concrete) implementation of a (sub)component the binding is no
    // longer considered modifiable. It cannot be further modified by a subclass implementation.
    if (!generatedComponentModel.isAbstract()) {
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

      if (binding.kind().equals(BindingKind.OPTIONAL)) {
        return ModifiableBindingType.OPTIONAL;
      }

      if (resolvedBindings.bindingType().equals(BindingType.PROVISION)
          && binding.isSyntheticMultibinding()) {
        return ModifiableBindingType.MULTIBINDING;
      }

      if (binding.kind().equals(BindingKind.INJECTION)) {
        return ModifiableBindingType.INJECTION;
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
  private boolean shouldModifyKnownBinding(ModifiableBindingMethod modifiableBindingMethod) {
    ModifiableBindingType newModifiableBindingType =
        getModifiableBindingType(modifiableBindingMethod.request());
    if (!newModifiableBindingType.equals(modifiableBindingMethod.type())) {
      // It is possible that a binding can change types, in which case we should always modify the
      // binding.
      return true;
    }
    return shouldModifyBinding(modifiableBindingMethod.type(), modifiableBindingMethod.request());
  }

  /**
   * Returns true if the current binding graph can, and should, modify a binding by overriding a
   * modifiable binding method.
   */
  private boolean shouldModifyBinding(
      ModifiableBindingType modifiableBindingType, BindingRequest request) {
    ResolvedBindings resolvedBindings = graph.resolvedBindings(request);
    switch (modifiableBindingType) {
      case GENERATED_INSTANCE:
        return !generatedComponentModel.isAbstract();
      case MISSING:
        // TODO(b/72748365): investigate beder@'s comment about having intermediate component
        // ancestors satisfy missing bindings of their children with their own missing binding
        // methods so that we can minimize the cases where we need to reach into doubly-nested
        // descendant component implementations
        return resolvableBinding(request);
      case OPTIONAL:
        // Only override optional binding methods if we have a non-empty binding.
        return !resolvedBindings.contributionBinding().dependencies().isEmpty();
      case MULTIBINDING:
        // Only modify a multibinding if there are new contributions.
        return !generatedComponentModel
            .superclassContributionsMade(request.key())
            .containsAll(resolvedBindings.contributionBinding().dependencies());
      case INJECTION:
        return !resolvedBindings.contributionBinding().kind().equals(BindingKind.INJECTION);
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
   * Returns a binding expression that invokes a method whose implementation is the given binding
   * expression. It will only return such an expression if the binding represents a modifiable
   * binding that should be wrapped in a method. We wrap expressions in this way so we can modify
   * the binding when generating a subcomponent subclass by overriding the method.
   */
  Optional<BindingExpression> maybeWrapInModifiableMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      BindingRequest request,
      BindingExpression bindingExpression,
      BindingMethodImplementation methodImplementation,
      Optional<ComponentMethodDescriptor> matchingComponentMethod) {
    ModifiableBindingType modifiableBindingType = getModifiableBindingType(request);
    if (shouldUseAModifiableConcreteMethodBindingExpression(
        modifiableBindingType, matchingComponentMethod)) {
      return Optional.of(
          new ModifiableConcreteMethodBindingExpression(
              resolvedBindings,
              request,
              modifiableBindingType,
              methodImplementation,
              generatedComponentModel,
              generatedComponentModel.getModifiableBindingMethod(request),
              newModifiableBindingWillBeFinalized(modifiableBindingType, request)));
    }
    return Optional.empty();
  }

  /**
   * Returns true if we should wrap a binding expression using a {@link
   * ModifiableConcreteMethodBindingExpression}. If we're generating the abstract base class of a
   * subcomponent and the binding matches a component method, even if it is modifiable, then it
   * should be "wrapped" by a {@link ComponentMethodBindingExpression}. If it isn't a base class
   * then modifiable methods should be handled by a {@link
   * ModifiableConcreteMethodBindingExpression}. When generating an inner subcomponent it doesn't
   * matter whether the binding matches a component method: All modifiable bindings should be
   * handled by a {@link ModifiableConcreteMethodBindingExpression}.
   */
  private boolean shouldUseAModifiableConcreteMethodBindingExpression(
      ModifiableBindingType type, Optional<ComponentMethodDescriptor> matchingComponentMethod) {
    return type.isModifiable()
        && (generatedComponentModel.supermodel().isPresent()
            || !matchingComponentMethod.isPresent());
  }
}
