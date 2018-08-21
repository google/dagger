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
import static dagger.internal.codegen.SourceFiles.simpleVariableName;

import com.google.auto.common.MoreTypes;
import dagger.model.Key;
import dagger.model.RequestKind;

/**
 * A {@link AbstractMethodModifiableBindingExpression} for a binding that is missing when generating
 * the abstract base class implementation of a subcomponent. The (unimplemented) method is added to
 * the {@link GeneratedComponentModel} when the dependency expression is requested. The method is
 * overridden when generating the implementation of an ancestor component.
 */
final class MissingBindingExpression extends AbstractMethodModifiableBindingExpression {
  private final GeneratedComponentModel generatedComponentModel;
  private final Key key;
  private final RequestKind kind;

  MissingBindingExpression(
      GeneratedComponentModel generatedComponentModel, Key key, RequestKind kind) {
    super(generatedComponentModel, ModifiableBindingType.MISSING, key, kind);
    this.generatedComponentModel = generatedComponentModel;
    this.key = key;
    this.kind = kind;
  }

  @Override
  String chooseMethodName() {
    return generatedComponentModel.getUniqueMethodName(
        "get"
            + LOWER_CAMEL.to(UPPER_CAMEL, simpleVariableName(MoreTypes.asTypeElement(key.type())))
            + (kind.equals(RequestKind.INSTANCE)
                ? ""
                : UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name())));
  }
}
