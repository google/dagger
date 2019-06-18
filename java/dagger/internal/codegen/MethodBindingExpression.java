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
import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.PRIVATE_METHOD_SCOPED_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.DoubleCheck;
import dagger.internal.MemoizedSentinel;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/** A binding expression that wraps another in a nullary method on the component. */
abstract class MethodBindingExpression extends BindingExpression {
  private final BindingRequest request;
  private final ContributionBinding binding;
  private final BindingMethodImplementation bindingMethodImplementation;
  private final ComponentImplementation componentImplementation;
  private final ProducerEntryPointView producerEntryPointView;
  private final BindingExpression wrappedBindingExpression;
  private final DaggerTypes types;

  protected MethodBindingExpression(
      BindingRequest request,
      ContributionBinding binding,
      MethodImplementationStrategy methodImplementationStrategy,
      BindingExpression wrappedBindingExpression,
      ComponentImplementation componentImplementation,
      DaggerTypes types) {
    this.request = checkNotNull(request);
    this.binding = checkNotNull(binding);
    this.bindingMethodImplementation = bindingMethodImplementation(methodImplementationStrategy);
    this.wrappedBindingExpression = checkNotNull(wrappedBindingExpression);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.producerEntryPointView = new ProducerEntryPointView(types);
    this.types = checkNotNull(types);
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
      Object unused = methodBody();
    }

    addMethod();
    return Expression.create(
        returnType(),
        requestingClass.equals(componentImplementation.name())
            ? CodeBlock.of("$N()", methodName())
            : CodeBlock.of("$T.this.$N()", componentImplementation.name(), methodName()));
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

  /** The method's body. */
  protected final CodeBlock methodBody() {
    return implementation(
        wrappedBindingExpression.getDependencyExpression(componentImplementation.name())
            ::codeBlock);
  }

  /** The method's body if this method is a component method. */
  protected final CodeBlock methodBodyForComponentMethod(
      ComponentMethodDescriptor componentMethod) {
    return implementation(
        wrappedBindingExpression.getDependencyExpressionForComponentMethod(
                componentMethod, componentImplementation)
            ::codeBlock);
  }

  private CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression) {
    return bindingMethodImplementation.implementation(simpleBindingExpression);
  }

  private BindingMethodImplementation bindingMethodImplementation(
      MethodImplementationStrategy methodImplementationStrategy) {
    switch (methodImplementationStrategy) {
      case SIMPLE:
        return new SimpleMethodImplementation();
      case SINGLE_CHECK:
        return new SingleCheckedMethodImplementation();
      case DOUBLE_CHECK:
        return new DoubleCheckedMethodImplementation();
    }
    throw new AssertionError(methodImplementationStrategy);
  }

  /** Returns the return type for the dependency request. */
  protected TypeMirror returnType() {
    if (request.isRequestKind(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }

    if (matchingComponentMethod().isPresent()) {
      // Component methods are part of the user-defined API, and thus we must use the user-defined
      // type.
      return matchingComponentMethod().get().resolvedReturnType(types);
    }

    TypeMirror requestedType = request.requestedType(binding.contributedType(), types);
    return types.accessibleType(requestedType, componentImplementation.name());
  }

  private Optional<ComponentMethodDescriptor> matchingComponentMethod() {
    return componentImplementation.componentDescriptor().firstMatchingComponentMethod(request);
  }

  /** Strateg for implementing the body of this method. */
  enum MethodImplementationStrategy {
    SIMPLE,
    SINGLE_CHECK,
    DOUBLE_CHECK,
    ;
  }

  private abstract static class BindingMethodImplementation {
    /**
     * Returns the method body, which contains zero or more statements (including semicolons).
     *
     * <p>If the implementation has a non-void return type, the body will also include the {@code
     * return} statement.
     *
     * @param simpleBindingExpression the expression to retrieve an instance of this binding without
     *     the wrapping method.
     */
    abstract CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression);
  }

  /** Returns the {@code wrappedBindingExpression} directly. */
  private static final class SimpleMethodImplementation extends BindingMethodImplementation {
    @Override
    CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression) {
      return CodeBlock.of("return $L;", simpleBindingExpression.get());
    }
  }

  /**
   * Defines a method body for single checked caching of the given {@code wrappedBindingExpression}.
   */
  private final class SingleCheckedMethodImplementation extends BindingMethodImplementation {
    private final Supplier<FieldSpec> field = Suppliers.memoize(this::createField);

    @Override
    CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression) {
      String fieldExpression = field.get().name.equals("local") ? "this.local" : field.get().name;

      CodeBlock.Builder builder = CodeBlock.builder()
          .addStatement("Object local = $N", fieldExpression);

      if (isNullable()) {
        builder.beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class);
      } else {
        builder.beginControlFlow("if (local == null)");
      }

      return builder
          .addStatement("local = $L", simpleBindingExpression.get())
          .addStatement("$N = ($T) local", fieldExpression, returnType())
          .endControlFlow()
          .addStatement("return ($T) local", returnType())
          .build();
    }

    FieldSpec createField() {
      String name =
          componentImplementation.getUniqueFieldName(
              request.isRequestKind(RequestKind.INSTANCE)
                  ? KeyVariableNamer.name(binding.key())
                  : FrameworkField.forBinding(binding, Optional.empty()).name());

      FieldSpec.Builder builder = FieldSpec.builder(fieldType(), name, PRIVATE, VOLATILE);
      if (isNullable()) {
        builder.initializer("new $T()", MemoizedSentinel.class);
      }

      FieldSpec field = builder.build();
      componentImplementation.addField(PRIVATE_METHOD_SCOPED_FIELD, field);
      return field;
    }

    TypeName fieldType() {
      if (isNullable()) {
        // Nullable instances use `MemoizedSentinel` instead of `null` as the initialization value,
        // so the field type must accept that and the return type
        return TypeName.OBJECT;
      }
      TypeName returnType = TypeName.get(returnType());
      return returnType.isPrimitive() ? returnType.box() : returnType;
    }

    private boolean isNullable() {
      return request.isRequestKind(RequestKind.INSTANCE) && binding.isNullable();
    }
  }

  /**
   * Defines a method body for double checked caching of the given {@code wrappedBindingExpression}.
   */
  private final class DoubleCheckedMethodImplementation extends BindingMethodImplementation {
    private final Supplier<String> fieldName = Suppliers.memoize(this::createField);

    @Override
    CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression) {
      String fieldExpression = fieldName.get().equals("local") ? "this.local" : fieldName.get();
      return CodeBlock.builder()
          .addStatement("$T local = $L", TypeName.OBJECT, fieldExpression)
          .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
          .beginControlFlow("synchronized (local)")
          .addStatement("local = $L", fieldExpression)
          .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
          .addStatement("local = $L", simpleBindingExpression.get())
          .addStatement("$1L = $2T.reentrantCheck($1L, local)", fieldExpression, DoubleCheck.class)
          .endControlFlow()
          .endControlFlow()
          .endControlFlow()
          .addStatement("return ($T) local", returnType())
          .build();
    }

    private String createField() {
      String name =
          componentImplementation.getUniqueFieldName(KeyVariableNamer.name(binding.key()));
      componentImplementation.addField(
          PRIVATE_METHOD_SCOPED_FIELD,
          FieldSpec.builder(TypeName.OBJECT, name, PRIVATE, VOLATILE)
              .initializer("new $T()", MemoizedSentinel.class)
              .build());
      return name;
    }
  }

}
