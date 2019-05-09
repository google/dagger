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
import static javax.lang.model.element.Modifier.PROTECTED;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.Accessibility;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link BindingExpression} that invokes a method that encapsulates a binding that cannot be
 * satisfied when generating the abstract base class implementation of a subcomponent. The
 * (unimplemented) method is added to the {@link ComponentImplementation} when the dependency
 * expression is requested. The method is overridden when generating the implementation of an
 * ancestor component.
 */
abstract class ModifiableAbstractMethodBindingExpression extends BindingExpression {
  private final ComponentImplementation componentImplementation;
  private final ModifiableBindingType modifiableBindingType;
  private final BindingRequest request;
  private final Optional<ComponentMethodDescriptor> matchingComponentMethod;
  private final DaggerTypes types;
  private Optional<String> methodName;

  ModifiableAbstractMethodBindingExpression(
      ComponentImplementation componentImplementation,
      ModifiableBindingType modifiableBindingType,
      BindingRequest request,
      Optional<ModifiableBindingMethod> matchingModifiableBindingMethod,
      Optional<ComponentMethodDescriptor> matchingComponentMethod,
      DaggerTypes types) {
    this.componentImplementation = componentImplementation;
    this.modifiableBindingType = modifiableBindingType;
    this.request = request;
    this.matchingComponentMethod = matchingComponentMethod;
    this.types = types;
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
    return Expression.create(
        returnType(),
        componentImplementation.name().equals(requestingClass)
            ? CodeBlock.of("$N()", methodName.get())
            : CodeBlock.of("$T.this.$N()", componentImplementation.name(), methodName.get()));
  }

  private void addUnimplementedMethod() {
    if (!methodName.isPresent()) {
      // Only add the method once in case of repeated references to the missing binding.
      methodName = Optional.of(chooseMethodName());
      TypeMirror returnType = returnType();
      componentImplementation.addModifiableBindingMethod(
          modifiableBindingType,
          request,
          returnType,
          MethodSpec.methodBuilder(methodName.get())
              .addModifiers(PROTECTED, ABSTRACT)
              .returns(TypeName.get(returnType))
              .build(),
          false /* finalized */);
    }
  }

  /**
   * The return type of this abstract method expression:
   *
   * <ul>
   *   <li>If there's a {@code matchingComponentMethod}, use its return type.
   *   <li>Otherwise, use the {@linkplain DaggerTypes#publiclyAccessibleType(TypeMirror) publicly
   *       accessible type} of the request. We can't use the {@linkplain
   *       Accessibility#isTypeAccessibleFrom(TypeMirror, String) type accessible from the current
   *       implementation's package} because a subclass implementation may be in a different package
   *       from which the request type is not accessible.
   * </ul>
   */
  private TypeMirror returnType() {
    if (matchingComponentMethod.isPresent()) {
      return matchingComponentMethod.get().resolvedReturnType(types);
    }

    TypeMirror requestedType = request.requestedType(contributedType(), types);
    return types.publiclyAccessibleType(requestedType);
  }

  /**
   * The {@link ContributionBinding#contributedType() type contributed} by the binding of this
   * expression. For missing bindings, this will be the key type.
   */
  protected abstract TypeMirror contributedType();

  /** Returns a unique 'getter' method name for the current component. */
  abstract String chooseMethodName();
}
