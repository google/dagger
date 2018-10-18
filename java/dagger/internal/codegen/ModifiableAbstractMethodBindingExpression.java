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

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import java.util.Optional;

/**
 * A {@link BindingExpression} that invokes a method that encapsulates a binding that cannot be
 * satisfied when generating the abstract base class implementation of a subcomponent. The
 * (unimplemented) method is added to the {@link GeneratedComponentModel} when the dependency
 * expression is requested. The method is overridden when generating the implementation of an
 * ancestor component.
 */
// TODO(b/72748365): There may be unimplemented abstract binding methods even after considering a
// complete binding graph: If there are @Provides-over-@Inject bindings then there could be branches
// of dependencies (of the @Inject binding) that have induced abstract modifiable binding methods
// that are missing in the full binding graph (given the @Provides binding). Such abstract
// modifiable methods should be overridden and an exception thrown.
abstract class ModifiableAbstractMethodBindingExpression extends BindingExpression {
  private final GeneratedComponentModel generatedComponentModel;
  private final ModifiableBindingType modifiableBindingType;
  private final BindingRequest request;
  private Optional<String> methodName;

  ModifiableAbstractMethodBindingExpression(
      GeneratedComponentModel generatedComponentModel,
      ModifiableBindingType modifiableBindingType,
      BindingRequest request,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      Optional<ComponentMethodDescriptor> matchingComponentMethod) {
    this.generatedComponentModel = generatedComponentModel;
    this.modifiableBindingType = modifiableBindingType;
    this.request = request;
    this.methodName =
        initializeMethodName(matchingComponentMethod, matchingModifiableBindingMethod);
  }

  /**
   * If this binding corresponds to an existing component method, or a known modifiable binding
   * method, use them to initialize the method name, which is a signal to call the existing method
   * rather than emit an abstract method.
   */
  private static Optional<String> initializeMethodName(
      Optional<ComponentMethodDescriptor> matchingComponentMethod,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod) {
    if (matchingComponentMethod.isPresent()) {
      return Optional.of(matchingComponentMethod.get().methodElement().getSimpleName().toString());
    }
    if (matchingModifiableBindingMethod.isPresent()) {
      return Optional.of(matchingModifiableBindingMethod.get().methodSpec().name);
    }
    return Optional.empty();
  }

  @Override
  final Expression getDependencyExpression(ClassName requestingClass) {
    addUnimplementedMethod();
    return Expression.create(request.key().type(), CodeBlock.of("$L()", methodName.get()));
  }

  private void addUnimplementedMethod() {
    if (!methodName.isPresent()) {
      // Only add the method once in case of repeated references to the missing binding.
      methodName = Optional.of(chooseMethodName());
      generatedComponentModel.addModifiableBindingMethod(
          modifiableBindingType,
          request,
          MethodSpec.methodBuilder(methodName.get())
              .addModifiers(PUBLIC, ABSTRACT)
              .returns(request.typeName())
              .build(),
          false /* finalized */);
    }
  }

  /** Returns a unique 'getter' method name for the current component. */
  abstract String chooseMethodName();
}
