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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.langmodel.DaggerElements.getAllAnnotations;

import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Utility methods related to processing {@link dagger.multibindings.IntoSet}, {@link
 * dagger.multibindings.ElementsIntoSet}, and {@link dagger.multibindings.IntoMap}.
 */
public final class MultibindingAnnotations {
  public static ImmutableSet<AnnotationMirror> forElement(Element method) {
    return getAllAnnotations(
        method, TypeNames.INTO_SET, TypeNames.ELEMENTS_INTO_SET, TypeNames.INTO_MAP);
  }
}
