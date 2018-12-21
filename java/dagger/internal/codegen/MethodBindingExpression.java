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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import java.util.Optional;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodBindingExpression extends BindingExpression {
  private final BindingRequest request;
  private final BindingMethodImplementation methodImplementation;
  private final ComponentImplementation componentImplementation;
  private final ProducerEntryPointView producerEntryPointView;

  protected MethodBindingExpression(
      BindingRequest request,
      BindingMethodImplementation methodImplementation,
      ComponentImplementation componentImplementation,
      DaggerTypes types) {
    this.request = checkNotNull(request);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.producerEntryPointView = new ProducerEntryPointView(types);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (request.frameworkType().isPresent()) {
      // Initializing a framework instance that participates in a cycle requires that the underlying
      // FrameworkInstanceBindingExpression is invoked in order for a cycle to be detected properly.
      // When a MethodBindingExpression wraps a FrameworkInstanceBindingExpression, the wrapped
      // expression will only be invoked once to implement the method body. This is a hack to work
      // around that weirdness - methodImplementation.body() will invoke the framework instance
      // initialization again in case the field is not fully initialized.
      // TODO(b/121196706): use a less hacky approach to fix this bug
      Object unused = methodImplementation.body();
    }
    
    addMethod();
    return Expression.create(
        methodImplementation.returnType(),
        requestingClass.equals(componentImplementation.name())
            ? CodeBlock.of("$N()", methodName())
            : CodeBlock.of("$T.this.$N()", componentImplementation.name(), methodName()));
  }

  @Override
  final CodeBlock getModifiableBindingMethodImplementation(
      ModifiableBindingMethod modifiableBindingMethod,
      ComponentImplementation component,
      DaggerTypes types) {
    // A matching modifiable binding method means that we have previously created the binding method
    // and we are now implementing it. If there is no matching method we need to first create the
    // method. We create the method by deferring to getDependencyExpression (defined above) via a
    // call to super.getModifiableBindingMethodImplementation().
    if (supertypeModifiableBindingMethod().isPresent()) {
      checkState(
          supertypeModifiableBindingMethod().get().fulfillsSameRequestAs(modifiableBindingMethod));
      return methodImplementation.body();
    }
    return super.getModifiableBindingMethodImplementation(
        modifiableBindingMethod, component, types);
  }

  protected final Optional<ModifiableBindingMethod> supertypeModifiableBindingMethod() {
    return componentImplementation.supertypeModifiableBindingMethod(request);
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(ComponentMethodDescriptor componentMethod,
      ComponentImplementation component) {
    return producerEntryPointView
        .getProducerEntryPointField(this, componentMethod, component)
        .orElseGet(
            () -> super.getDependencyExpressionForComponentMethod(componentMethod, component));
  }

  /** Adds the method to the component (if necessary) the first time it's called. */
  protected abstract void addMethod();

  /** Returns the name of the method to call. */
  protected abstract String methodName();
}
