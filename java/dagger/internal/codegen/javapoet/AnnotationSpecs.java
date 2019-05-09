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

package dagger.internal.codegen.javapoet;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.squareup.javapoet.AnnotationSpec;
import java.util.Arrays;

/** Static factories to create {@link AnnotationSpec}s. */
public final class AnnotationSpecs {
  /** Values for an {@link SuppressWarnings} annotation. */
  public enum Suppression {
    RAWTYPES,
    UNCHECKED,
    ;

    @Override
    public String toString() {
      return Ascii.toLowerCase(name());
    }
  }

  /** Creates an {@link AnnotationSpec} for {@link SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(Suppression first, Suppression... rest) {
    checkNotNull(first);
    Arrays.stream(rest).forEach(Preconditions::checkNotNull);
    AnnotationSpec.Builder builder = AnnotationSpec.builder(SuppressWarnings.class);
    Lists.asList(first, rest).forEach(suppression -> builder.addMember("value", "$S", suppression));
    return builder.build();
  }

  private AnnotationSpecs() {}
}
