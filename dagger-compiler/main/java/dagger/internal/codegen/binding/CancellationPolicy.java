/*
 * Copyright (C) 2023 The Dagger Authors.
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
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XAnnotation;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;

/**
 * The cancellation policy for a {@link dagger.producers.ProductionComponent}.
 *
 * <p>@see dagger.producers.CancellationPolicy
 */
public enum CancellationPolicy {
  PROPAGATE,
  IGNORE;

  static CancellationPolicy from(XAnnotation annotation) {
    checkArgument(XAnnotations.asClassName(annotation).equals(XTypeNames.CANCELLATION_POLICY));
    return valueOf(getSimpleName(annotation.getAsEnum("fromSubcomponents")));
  }
}
