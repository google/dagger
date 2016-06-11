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

import com.google.common.collect.ImmutableSet;
import dagger.Multibindings;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** A validator for methods in {@link Multibindings @Multibindings} interfaces. */
final class MultibindingsMethodValidator extends MultibindsMethodValidator {

  MultibindingsMethodValidator(Elements elements, Types types) {
    super(elements, types, Multibindings.class, ImmutableSet.of(Multibindings.class));
  }

  @Override
  protected void checkEnclosingElement(ValidationReport.Builder<ExecutableElement> builder) {
    // no-op, since @Multibindings interfaces can inherit methods from unannotated supertypes.
  }
}

