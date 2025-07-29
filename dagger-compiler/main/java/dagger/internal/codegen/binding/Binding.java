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

package dagger.internal.codegen.binding;

import dagger.internal.codegen.model.DependencyRequest;
import java.util.Optional;

/**
 * An abstract type for classes representing a Dagger binding. Particularly, contains the element
 * that generated the binding and the {@link DependencyRequest} instances that are required to
 * satisfy the binding, but leaves the specifics of the <i>mechanism</i> of the binding to the
 * subtypes.
 */
public abstract class Binding extends BindingDeclaration {
  /** Returns the optional {@link BindingType}.  */
  abstract Optional<BindingType> optionalBindingType();

  /** The {@link BindingType} of this binding. */
  public final BindingType bindingType() {
    if (optionalBindingType().isPresent()) {
      return optionalBindingType().get();
    }
    throw new AssertionError("bindingType() is not set: " + this);
  }

  /** The {@link FrameworkType} of this binding. */
  public final FrameworkType frameworkType() {
    return FrameworkType.forBindingType(bindingType());
  }
}
