/*
 * Copyright (C) 2018 The Dagger Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.javapoet.Expression;
import dagger.model.DependencyRequest;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Optional;
import java.util.Set;

/** An abstract base class for multibinding {@link BindingExpression}s. */
abstract class MultibindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final ComponentImplementation componentImplementation;

  MultibindingExpression(
      ResolvedBindings resolvedBindings, ComponentImplementation componentImplementation) {
    super(resolvedBindings);
    this.componentImplementation = componentImplementation;
    this.binding = (ProvisionBinding) resolvedBindings.contributionBinding();
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression expression = buildDependencyExpression(requestingClass);
    componentImplementation.registerImplementedMultibinding(binding, bindingRequest());
    return expression;
  }

  /**
   * Returns an expression that evaluates to the value of a multibinding request for the given
   * requesting class.
   */
  protected abstract Expression buildDependencyExpression(ClassName requestingClass);

  /**
   * Returns the subset of {@code dependencies} that represent multibinding contributions that were
   * not included in a superclass implementation of this multibinding method. This is relevant only
   * for ahead-of-time subcomponents. When not generating ahead-of-time subcomponents there is only
   * one implementation of a multibinding expression and all {@link DependencyRequest}s from the
   * argment are returned.
   */
  protected Set<DependencyRequest> getNewContributions(
      ImmutableSet<DependencyRequest> dependencies) {
    ImmutableSet<Key> superclassContributions = superclassContributions();
    return Sets.filter(
        dependencies, dependency -> !superclassContributions.contains(dependency.key()));
  }

  /**
   * Returns the {@link CodeBlock} representing a call to a superclass implementation of the
   * modifiable binding method that encapsulates this binding, if it exists. This is only possible
   * when generating ahead-of-time subcomponents.
   */
  protected Optional<CodeBlock> superMethodCall() {
    if (componentImplementation.superclassImplementation().isPresent()) {
      Optional<ModifiableBindingMethod> method =
          componentImplementation.getModifiableBindingMethod(bindingRequest());
      if (method.isPresent()) {
        if (!superclassContributions().isEmpty()) {
          return Optional.of(CodeBlock.of("super.$L()", method.get().methodSpec().name));
        }
      }
    }
    return Optional.empty();
  }

  private BindingRequest bindingRequest() {
    return BindingRequest.bindingRequest(binding.key(), RequestKind.INSTANCE);
  }

  private ImmutableSet<Key> superclassContributions() {
    return componentImplementation.superclassContributionsMade(bindingRequest());
  }
}
