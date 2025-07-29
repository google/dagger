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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import dagger.BindsOptionalOf;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/** A {@link BindsOptionalOf} declaration. */
@AutoValue
abstract class OptionalBindingDeclaration extends Declaration {

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

    OptionalBindingDeclaration forMethod(XMethodElement method, XTypeElement contributingModule) {
      checkArgument(method.hasAnnotation(XTypeNames.BINDS_OPTIONAL_OF));
      return new AutoValue_OptionalBindingDeclaration(
          Optional.of(method),
          Optional.of(contributingModule),
          keyFactory.forBindsOptionalOfMethod(method, contributingModule));
    }
  }
}
