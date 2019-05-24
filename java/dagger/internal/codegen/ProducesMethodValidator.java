/*
 * Copyright (C) 2014 The Dagger Authors.
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
import static dagger.internal.codegen.BindingElementValidator.AllowsMultibindings.ALLOWS_MULTIBINDINGS;
import static dagger.internal.codegen.BindingElementValidator.AllowsScoping.NO_SCOPING;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_CONCRETE;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.EXCEPTION;

import com.google.auto.common.MoreTypes;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.multibindings.ElementsIntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** A validator for {@link Produces} methods. */
final class ProducesMethodValidator extends BindingMethodValidator {

  @Inject
  ProducesMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator) {
    super(
        elements,
        types,
        dependencyRequestValidator,
        Produces.class,
        ProducerModule.class,
        MUST_BE_CONCRETE,
        EXCEPTION,
        ALLOWS_MULTIBINDINGS,
        NO_SCOPING);
  }

  @Override
  protected String elementsIntoSetNotASetMessage() {
    return "@Produces methods of type set values must return a Set or ListenableFuture of Set";
  }

  @Override
  protected String badTypeMessage() {
    return "@Produces methods can return only a primitive, an array, a type variable, "
        + "a declared type, or a ListenableFuture of one of those types";
  }

  @Override
  protected ElementValidator elementValidator(ExecutableElement element) {
    return new Validator(element);
  }

  private class Validator extends MethodValidator {
    Validator(ExecutableElement element) {
      super(element);
    }

    @Override
    protected void checkAdditionalMethodProperties() {
      checkNullable();
    }

    /** Adds a warning if a {@link Produces @Produces} method is declared nullable. */
    // TODO(beder): Properly handle nullable with producer methods.
    private void checkNullable() {
      if (ConfigurationAnnotations.getNullableType(element).isPresent()) {
        report.addWarning("@Nullable on @Produces methods does not do anything");
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Allows {@code keyType} to be a {@link ListenableFuture} of an otherwise-valid key type.
     */
    @Override
    protected void checkKeyType(TypeMirror keyType) {
      Optional<TypeMirror> typeToCheck = unwrapListenableFuture(keyType);
      if (typeToCheck.isPresent()) {
        super.checkKeyType(typeToCheck.get());
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Allows an {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} method to return
     * a {@link ListenableFuture} of a {@link Set} as well.
     */
    @Override
    protected void checkSetValuesType() {
      Optional<TypeMirror> typeToCheck = unwrapListenableFuture(element.getReturnType());
      if (typeToCheck.isPresent()) {
        checkSetValuesType(typeToCheck.get());
      }
    }

    private Optional<TypeMirror> unwrapListenableFuture(TypeMirror type) {
      if (MoreTypes.isType(type) && MoreTypes.isTypeOf(ListenableFuture.class, type)) {
        DeclaredType declaredType = MoreTypes.asDeclared(type);
        if (declaredType.getTypeArguments().isEmpty()) {
          report.addError("@Produces methods cannot return a raw ListenableFuture");
          return Optional.empty();
        } else {
          return Optional.of((TypeMirror) getOnlyElement(declaredType.getTypeArguments()));
        }
      }
      return Optional.of(type);
    }
  }
}
