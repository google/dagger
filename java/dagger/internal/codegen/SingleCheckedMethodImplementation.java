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

import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.PRIVATE_METHOD_SCOPED_FIELD;
import static dagger.model.RequestKind.INSTANCE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.MemoizedSentinel;
import dagger.model.RequestKind;
import java.util.Optional;

/**
 * Defines a method body and return type for single checked caching of the given {@link
 * BindingExpression}.
 */
final class SingleCheckedMethodImplementation extends BindingMethodImplementation {

  private final GeneratedComponentModel generatedComponentModel;
  private final ResolvedBindings resolvedBindings;
  private final ContributionBinding binding;
  private final RequestKind requestKind;
  private final Supplier<FieldSpec> field = Suppliers.memoize(this::createField);

  SingleCheckedMethodImplementation(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingExpression bindingExpression,
      DaggerTypes types,
      GeneratedComponentModel generatedComponentModel) {
    super(resolvedBindings, requestKind, bindingExpression, generatedComponentModel.name(), types);
    this.generatedComponentModel = generatedComponentModel;
    this.resolvedBindings = resolvedBindings;
    this.binding = resolvedBindings.contributionBinding();
    this.requestKind = requestKind;
  }

  @Override
  CodeBlock body() {
    CodeBlock.Builder builder = CodeBlock.builder();
    if (isNullable()) {
      builder.beginControlFlow("if ($N instanceof $T)", field.get(), MemoizedSentinel.class);
    } else {
      builder.beginControlFlow("if ($N == null)", field.get());
    }

    builder
        .addStatement("$N = $L", field.get(), simpleBindingExpression())
        .endControlFlow();

    if (isNullable()) {
      builder.addStatement("return ($T) $N", returnType(), field.get());
    } else {
      builder.addStatement("return $N", field.get());
    }
    return builder.build();
  }

  private FieldSpec createField() {
    String name =
        generatedComponentModel.getUniqueFieldName(
            requestKind.equals(INSTANCE)
                ? BindingVariableNamer.name(binding)
                : FrameworkField.forResolvedBindings(resolvedBindings, Optional.empty()).name());

    FieldSpec field =
        // Nullable instances use `MemoizedSentinel` instead of `null` as the initialization value.
        isNullable()
            ? FieldSpec.builder(TypeName.OBJECT, name, PRIVATE, VOLATILE)
                .initializer("new $T()", MemoizedSentinel.class)
                .build()
            : FieldSpec.builder(TypeName.get(returnType()), name, PRIVATE, VOLATILE).build();

    generatedComponentModel.addField(PRIVATE_METHOD_SCOPED_FIELD, field);
    return field;
  }

  private boolean isNullable() {
    return requestKind.equals(INSTANCE) && binding.isNullable();
  }
}
