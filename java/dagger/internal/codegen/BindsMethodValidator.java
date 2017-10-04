/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.BindingMethodValidator.AllowsMultibindings.ALLOWS_MULTIBINDINGS;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;
import static dagger.internal.codegen.ErrorMessages.BINDS_ELEMENTS_INTO_SET_METHOD_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.Binds;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A validator for {@link Binds} methods.
 */
final class BindsMethodValidator extends BindingMethodValidator {
  private final Types types;
  private final BindsTypeChecker bindsTypeChecker;

  BindsMethodValidator(Elements elements, Types types) {
    super(
        elements,
        types,
        Binds.class,
        ImmutableSet.of(Module.class, ProducerModule.class),
        MUST_BE_ABSTRACT,
        RUNTIME_EXCEPTION,
        ALLOWS_MULTIBINDINGS);
    this.types = types;
    this.bindsTypeChecker = new BindsTypeChecker(types, elements);
  }

  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMethod(builder);
    checkParameters(builder);
  }

  private void checkParameters(ValidationReport.Builder<ExecutableElement> builder) {
    ExecutableElement method = builder.getSubject();
    List<? extends VariableElement> parameters = method.getParameters();
    if (parameters.size() == 1) {
      VariableElement parameter = getOnlyElement(parameters);
      TypeMirror leftHandSide = boxIfNecessary(method.getReturnType());
      TypeMirror rightHandSide = parameter.asType();
      ContributionType contributionType = ContributionType.fromBindingMethod(method);
      if (contributionType.equals(ContributionType.SET_VALUES) && !SetType.isSet(leftHandSide)) {
        builder.addError(BINDS_ELEMENTS_INTO_SET_METHOD_RETURN_SET);
      }

      if (!bindsTypeChecker.isAssignable(rightHandSide, leftHandSide, contributionType)) {
        builder.addError(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER);
      }
    } else {
      builder.addError(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER);
    }
  }

  private TypeMirror boxIfNecessary(TypeMirror maybePrimitive) {
    if (maybePrimitive.getKind().isPrimitive()) {
      return types.boxedClass(MoreTypes.asPrimitiveType(maybePrimitive)).asType();
    }
    return maybePrimitive;
  }
}
