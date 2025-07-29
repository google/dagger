/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.aliasof;

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/** Generates resource files for {@link dagger.hilt.migration.AliasOf}. */
final class AliasOfPropagatedDataGenerator {

  private final XTypeElement aliasScope;
  private final ImmutableList<XTypeElement> defineComponentScopes;

  AliasOfPropagatedDataGenerator(
      XTypeElement aliasScope,
      ImmutableList<XTypeElement> defineComponentScopes) {
    this.aliasScope = aliasScope;
    this.defineComponentScopes = defineComponentScopes;
  }

  void generate() {
    Processors.generateAggregatingClass(
        ClassNames.ALIAS_OF_PROPAGATED_DATA_PACKAGE,
        propagatedDataAnnotation(),
        aliasScope,
        getClass());
  }

  private AnnotationSpec propagatedDataAnnotation() {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassNames.ALIAS_OF_PROPAGATED_DATA);
    for (XTypeElement defineComponentScope : defineComponentScopes) {
      builder.addMember("defineComponentScopes", "$T.class", defineComponentScope.getClassName());
    }
    builder.addMember("alias", "$T.class", aliasScope.getClassName());
    return builder.build();
  }
}
