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
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.DoubleCheck;
import dagger.internal.MemoizedSentinel;
import dagger.model.RequestKind;

/**
 * Defines a method body and return type for double checked locking of the given {@link
 * BindingExpression}.
 */
final class DoubleCheckedMethodImplementation extends BindingMethodImplementation {

  private final GeneratedComponentModel generatedComponentModel;
  private final ContributionBinding binding;
  private final Supplier<String> fieldName = Suppliers.memoize(this::createField);

  DoubleCheckedMethodImplementation(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingExpression bindingExpression,
      DaggerTypes types,
      GeneratedComponentModel generatedComponentModel) {
    super(resolvedBindings, requestKind, bindingExpression, generatedComponentModel.name(), types);
    this.generatedComponentModel = generatedComponentModel;
    this.binding = resolvedBindings.contributionBinding();
  }

  @Override
  CodeBlock body() {
    String fieldExpression = fieldName.get().equals("local") ? "this.local" : fieldName.get();
    return CodeBlock.builder()
        .addStatement("$T local = $L", TypeName.OBJECT, fieldExpression)
        .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
        .beginControlFlow("synchronized (local)")
        .addStatement("local = $L", fieldExpression)
        .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
        .addStatement("local = $L", simpleBindingExpression())
        .addStatement("$1L = $2T.reentrantCheck($1L, local)", fieldExpression, DoubleCheck.class)
        .endControlFlow()
        .endControlFlow()
        .endControlFlow()
        .addStatement("return ($T) local", returnType())
        .build();
  }

  private String createField() {
    String name = generatedComponentModel.getUniqueFieldName(BindingVariableNamer.name(binding));
    generatedComponentModel.addField(
        PRIVATE_METHOD_SCOPED_FIELD,
        FieldSpec.builder(TypeName.OBJECT, name, PRIVATE, VOLATILE)
            .initializer("new $T()", MemoizedSentinel.class)
            .build());
    return name;
  }
}
