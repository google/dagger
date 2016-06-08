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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.Provides;

final class AnnotationSpecs {

  static final AnnotationSpec SUPPRESS_WARNINGS_UNCHECKED = suppressWarnings("unchecked");
  static final AnnotationSpec SUPPRESS_WARNINGS_RAWTYPES = suppressWarnings("rawtypes");

  private static AnnotationSpec suppressWarnings(String value) {
    return AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", value).build();
  }

  private AnnotationSpecs() {}
}
