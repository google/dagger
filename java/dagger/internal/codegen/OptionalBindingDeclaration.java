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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import dagger.BindsOptionalOf;
import dagger.model.Key;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** A {@link BindsOptionalOf} declaration. */
@AutoValue
abstract class OptionalBindingDeclaration extends BindingDeclaration {

  /**
   * {@inheritDoc}
   *
   * <p>The key's type is the method's return type, even though the synthetic bindings will be for
   * {@code Optional} of derived types.
   */
  @Override
  public abstract Key key();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  static class Factory {
    private final KeyFactory keyFactory;

    @Inject
    Factory(KeyFactory keyFactory) {
      this.keyFactory = keyFactory;
    }

    OptionalBindingDeclaration forMethod(ExecutableElement method, TypeElement contributingModule) {
      checkArgument(isAnnotationPresent(method, BindsOptionalOf.class));
      return new AutoValue_OptionalBindingDeclaration(
          Optional.<Element>of(method),
          Optional.of(contributingModule),
          keyFactory.forBindsOptionalOfMethod(method, contributingModule));
    }
  }
}
