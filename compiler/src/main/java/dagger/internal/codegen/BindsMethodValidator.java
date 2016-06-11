/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import dagger.producers.ProducerModule;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_MAP_HAS_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDS_ELEMENTS_INTO_SET_METHOD_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER;
import static dagger.internal.codegen.MapKeys.getMapKeys;

/**
 * A validator for {@link Binds} methods.
 */
final class BindsMethodValidator extends BindingMethodValidator {
  private final Types types;
  private final Elements elements;

  BindsMethodValidator(Elements elements, Types types) {
    super(
        elements,
        types,
        Binds.class,
        ImmutableSet.of(Module.class, ProducerModule.class),
        MUST_BE_ABSTRACT,
        RUNTIME_EXCEPTION);
    this.types = types;
    this.elements = elements;
  }

  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMethod(builder);
    checkParameters(builder);
  }

  @Override // TODO(dpb, ronshapiro): When @Binds methods support @IntoMap, stop overriding.
  protected void checkMapKeys(ValidationReport.Builder<ExecutableElement> builder) {
    if (!isAnnotationPresent(builder.getSubject(), IntoMap.class)) {
      for (AnnotationMirror mapKey : getMapKeys(builder.getSubject())) {
        builder.addError(BINDING_METHOD_NOT_MAP_HAS_MAP_KEY, builder.getSubject(), mapKey);
      }
    }
  }

  @Override
  protected void checkMultibindings(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMultibindings(builder);
    if (isAnnotationPresent(builder.getSubject(), IntoMap.class)) {
      builder.addError("@Binds @IntoMap is not yet supported");
    }
  }

  private void checkParameters(ValidationReport.Builder<ExecutableElement> builder) {
    List<? extends VariableElement> parameters = builder.getSubject().getParameters();
    if (parameters.size() == 1) {
      VariableElement parameter = getOnlyElement(parameters);
      TypeMirror leftHandSide = builder.getSubject().getReturnType();
      TypeMirror rightHandSide = parameter.asType();
      switch (ContributionType.fromBindingMethod(builder.getSubject())) {
        case SET_VALUES:
          if (!SetType.isSet(leftHandSide)) {
            builder.addError(BINDS_ELEMENTS_INTO_SET_METHOD_RETURN_SET);
          } else {
            validateTypesAreAssignable(
                builder,
                rightHandSide,
                methodParameterType(MoreTypes.asDeclared(leftHandSide), "addAll"));
          }
          break;
        case SET:
          DeclaredType parameterizedSetType = types.getDeclaredType(setElement(), leftHandSide);
          validateTypesAreAssignable(
              builder,
              rightHandSide,
              methodParameterType(parameterizedSetType, "add"));
          break;
        case UNIQUE:
          validateTypesAreAssignable(builder, rightHandSide, leftHandSide);
          break;
        default:
          // @IntoMap not yet supported
      }
    } else {
      builder.addError(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER);
    }
  }

  private TypeMirror methodParameterType(DeclaredType type, String methodName) {
    ImmutableList.Builder<ExecutableElement> methodsForName = ImmutableList.builder();
    for (ExecutableElement method :
        ElementFilter.methodsIn(MoreElements.asType(type.asElement()).getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(methodName)) {
        methodsForName.add(method);
      }
    }
    ExecutableElement method = getOnlyElement(methodsForName.build());
    return getOnlyElement(
        MoreTypes.asExecutable(types.asMemberOf(type, method)).getParameterTypes());
  }

  private void validateTypesAreAssignable(
      ValidationReport.Builder<ExecutableElement> builder,
      TypeMirror rightHandSide,
      TypeMirror leftHandSide) {
    if (!types.isAssignable(rightHandSide, leftHandSide)) {
      builder.addError(BINDS_METHOD_ONE_ASSIGNABLE_PARAMETER);
    }
  }

  private TypeElement setElement() {
    return elements.getTypeElement(Set.class.getName());
  }
}
