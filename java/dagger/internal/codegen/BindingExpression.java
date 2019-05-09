/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.lang.model.type.TypeMirror;

/** A factory of code expressions used to access a single request for a binding in a component. */
// TODO(user): Rename this to RequestExpression?
abstract class BindingExpression {

  /**
   * Returns an expression that evaluates to the value of a request based on the given requesting
   * class.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract Expression getDependencyExpression(ClassName requestingClass);

  /**
   * Equivalent to {@link #getDependencyExpression} that is used only when the request is for an
   * implementation of a component method. By default, just delegates to {@link
   * #getDependencyExpression}.
   */
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    return getDependencyExpression(component.name());
  }

  /** Returns {@code true} if this binding expression should be encapsulated in a method. */
  boolean requiresMethodEncapsulation() {
    return false;
  }

  /**
   * Returns an expression for the implementation of a component method with the given request.
   *
   * @param component the component that will contain the implemented method
   */
  CodeBlock getComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    // By default, just delegate to #getDependencyExpression().
    return CodeBlock.of(
        "return $L;",
        getDependencyExpressionForComponentMethod(componentMethod, component).codeBlock());
  }

  /**
   * Returns an expression for the implementation of a modifiable binding method for the given
   * component.
   */
  CodeBlock getModifiableBindingMethodImplementation(
      ModifiableBindingMethod modifiableBindingMethod,
      ComponentImplementation component,
      DaggerTypes types) {
    Expression dependencyExpression = getDependencyExpression(component.name());

    // It's possible to have a case where a modifiable component method delegates to another
    // binding method from an enclosing class that is not itself a component method. In that case,
    // the enclosing class's method may return a publicly accessible type, but the nested class will
    // have a return type that is defined by the component method. In that case, a downcast is
    // necessary so that the return statement is valid.
    //
    // E.g.:
    //
    // public class DaggerAncestor implements Ancestor {
    //   protected Object packagePrivateModifiable() { ... }
    //
    //   protected class LeafImpl extends DaggerLeaf {
    //     @Override
    //     public final PackagePrivateModifiable componentMethod() {
    //       return (PackagePrivateModifiable) DaggerAncestor.this.packagePrivateModifiable();
    //     }
    //   }
    // }
    //
    // DaggerAncestor.packagePrivateModifiable returns Object even though the actual instance's type
    // is PackagePrivateModifiable. So a cast is necessary.
    //
    // This isn't necessary for getComponentMethodImplementation() because that's only used for
    // non-modifiable bindings
    TypeMirror returnType = modifiableBindingMethod.returnType();
    if (!types.isAssignable(dependencyExpression.type(), returnType)
       && isTypeAccessibleFrom(returnType, component.name().packageName())) {
      dependencyExpression = dependencyExpression.castTo(returnType);
    }

    return CodeBlock.of("return $L;", dependencyExpression.codeBlock());
  }
}
