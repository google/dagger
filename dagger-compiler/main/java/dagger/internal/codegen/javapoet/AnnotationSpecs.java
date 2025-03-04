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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.squareup.javapoet.AnnotationSpec;

/** Static factories to create {@link AnnotationSpec}s. */
public final class AnnotationSpecs {
  /** Values for an {@link SuppressWarnings} annotation. */
  public enum Suppression {
    RAWTYPES("rawtypes"),
    UNCHECKED("unchecked"),
    FUTURE_RETURN_VALUE_IGNORED("FutureReturnValueIgnored"),
    KOTLIN_INTERNAL("KotlinInternal", "KotlinInternalInJava"),
    CAST("cast"),
    DEPRECATION("deprecation"),
    UNINITIALIZED("nullness:initialization.field.uninitialized");

    private final ImmutableList<String> values;

    Suppression(String... values) {
      this.values = ImmutableList.copyOf(values);
    }
  }

  /** Creates an {@link AnnotationSpec} for {@link SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(Suppression first, Suppression... rest) {
    return suppressWarnings(ImmutableSet.copyOf(Lists.asList(first, rest)));
  }

  /** Creates an {@link AnnotationSpec} for {@link SuppressWarnings}. */
  public static AnnotationSpec suppressWarnings(ImmutableSet<Suppression> suppressions) {
    checkArgument(!suppressions.isEmpty());
    AnnotationSpec.Builder builder = AnnotationSpec.builder(SuppressWarnings.class);
    suppressions.stream()
        .flatMap(suppression -> suppression.values.stream())
        .forEach(value -> builder.addMember("value", "$S", value));
    return builder.build();
  }

  private AnnotationSpecs() {}
}
