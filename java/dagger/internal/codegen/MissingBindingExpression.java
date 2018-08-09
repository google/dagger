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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static dagger.internal.codegen.RequestKinds.requestTypeName;
import static dagger.internal.codegen.SourceFiles.simpleVariableName;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.model.Key;
import dagger.model.RequestKind;

/**
 * A {@link BindingExpression} that invokes a method that encapsulates a binding that is missing
 * when generating the abstract base class implementation of a subcomponent. The (unimplemented)
 * method is added to the {@link GeneratedComponentModel} when the dependency expression is
 * requested. The method is overridden when generating the implementation of an ancestor component.
 */
final class MissingBindingExpression extends BindingExpression {
  private final GeneratedComponentModel generatedComponentModel;
  private final Key key;
  private final RequestKind kind;
  private String methodName;

  MissingBindingExpression(
      GeneratedComponentModel generatedComponentModel, Key key, RequestKind kind) {
    this.generatedComponentModel = generatedComponentModel;
    this.key = key;
    this.kind = kind;
  }

  @Override
  final Expression getDependencyExpression(ClassName requestingClass) {
    addUnimplementedMethod();
    return Expression.create(key.type(), CodeBlock.of("$L()", methodName));
  }

  private void addUnimplementedMethod() {
    if (methodName == null) {
      // Only add the method once in case of repeated references to the missing binding.
      methodName = chooseMethodName();
      generatedComponentModel.addUnimplementedMissingBindingMethod(
          key,
          kind,
          MethodSpec.methodBuilder(methodName)
              .addModifiers(PUBLIC, ABSTRACT)
              .returns(requestTypeName(kind, TypeName.get(key.type())))
              .build());
    }
  }

  private String chooseMethodName() {
    return generatedComponentModel.getUniqueMethodName(
        "get"
            + LOWER_CAMEL.to(UPPER_CAMEL, simpleVariableName(MoreTypes.asTypeElement(key.type())))
            + (kind.equals(RequestKind.INSTANCE)
                ? ""
                : UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name())));
  }
}
