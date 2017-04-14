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

import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.BindingMethodValidator.AllowsMultibindings.NO_MULTIBINDINGS;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;
import static dagger.internal.codegen.ErrorMessages.MultibindsMessages.METHOD_MUST_RETURN_MAP_OR_SET;
import static dagger.internal.codegen.ErrorMessages.MultibindsMessages.PARAMETERS;
import static dagger.internal.codegen.FrameworkTypes.isFrameworkType;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.multibindings.Multibinds;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** A validator for {@link Multibinds} methods. */
class MultibindsMethodValidator extends BindingMethodValidator {

  /** Creates a validator for {@link Multibinds @Multibinds} methods. */
  MultibindsMethodValidator(Elements elements, Types types) {
    this(elements, types, Multibinds.class, ImmutableSet.of(Module.class, ProducerModule.class));
  }

  protected MultibindsMethodValidator(
      Elements elements,
      Types types,
      Class<? extends Annotation> methodAnnotation,
      Iterable<? extends Class<? extends Annotation>> enclosingElementAnnotations) {
    super(
        elements,
        types,
        methodAnnotation,
        enclosingElementAnnotations,
        MUST_BE_ABSTRACT,
        NO_EXCEPTIONS,
        NO_MULTIBINDINGS);
  }
  
  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMethod(builder);

    checkParameters(builder);
  }

  private void checkParameters(ValidationReport.Builder<ExecutableElement> builder) {
    if (!builder.getSubject().getParameters().isEmpty()) {
      builder.addError(formatErrorMessage(PARAMETERS));
    }
  }

  /** Adds an error unless the method returns a {@code Map<K, V>} or {@code Set<T>}. */
  @Override
  protected void checkReturnType(ValidationReport.Builder<ExecutableElement> builder) {
    if (!isPlainMap(builder.getSubject().getReturnType())
        && !isPlainSet(builder.getSubject().getReturnType())) {
      builder.addError(formatErrorMessage(METHOD_MUST_RETURN_MAP_OR_SET));
    }
  }

  private boolean isPlainMap(TypeMirror returnType) {
    if (!MapType.isMap(returnType)) {
      return false;
    }
    MapType mapType = MapType.from(returnType);
    return !mapType.isRawType()
        && MoreTypes.isType(mapType.valueType()) // No wildcards.
        && !isFrameworkType(mapType.valueType());
  }

  private boolean isPlainSet(TypeMirror returnType) {
    if (!SetType.isSet(returnType)) {
      return false;
    }
    SetType setType = SetType.from(returnType);
    return !setType.isRawType()
        && MoreTypes.isType(setType.elementType()) // No wildcards.
        && !isFrameworkType(setType.elementType());
  }
}
