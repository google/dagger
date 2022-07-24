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

import static androidx.room.compiler.processing.XTypeKt.isArray;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static dagger.internal.codegen.binding.SourceFiles.protectAgainstKeywords;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import androidx.room.compiler.processing.XArrayType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import java.util.Iterator;

/**
 * Suggests a variable name for a type based on a {@link Key}. Prefer {@link
 * DependencyVariableNamer} for cases where a specific {@link DependencyRequest} is present.
 */
public final class KeyVariableNamer {
  /** Simple names that are very common. Inspired by https://errorprone.info/bugpattern/BadImport */
  private static final ImmutableSet<String> VERY_SIMPLE_NAMES =
      ImmutableSet.of(
          "Builder",
          "Factory",
          "Component",
          "Subcomponent",
          "Injector");

  private KeyVariableNamer() {}

  public static String name(Key key) {
    if (key.multibindingContributionIdentifier().isPresent()) {
      return key.multibindingContributionIdentifier().get().bindingMethod();
    }

    StringBuilder builder = new StringBuilder();

    if (key.qualifier().isPresent()) {
      // TODO(gak): Use a better name for fields with qualifiers with members.
      builder.append(getSimpleName(key.qualifier().get().xprocessing().getType().getTypeElement()));
    }

    typeNamer(key.type().xprocessing(), builder);
    return protectAgainstKeywords(UPPER_CAMEL.to(LOWER_CAMEL, builder.toString()));
  }

  private static void typeNamer(XType type, StringBuilder builder) {
    if (isDeclared(type)) {
      XTypeElement element = type.getTypeElement();
      if (element.isNested() && VERY_SIMPLE_NAMES.contains(getSimpleName(element))) {
        builder.append(getSimpleName(element.getEnclosingTypeElement()));
      }

      builder.append(getSimpleName(element));
      Iterator<? extends XType> argumentIterator = type.getTypeArguments().iterator();
      if (argumentIterator.hasNext()) {
        builder.append("Of");
        XType first = argumentIterator.next();
        typeNamer(first, builder);
        while (argumentIterator.hasNext()) {
          builder.append("And");
          typeNamer(argumentIterator.next(), builder);
        }
      }
    } else if (isPrimitive(type)) {
      builder.append(LOWER_CAMEL.to(UPPER_CAMEL, type.toString()));
    } else if (isArray(type)) {
      typeNamer(((XArrayType) type).getComponentType(), builder);
      builder.append("Array");
    }
  }
}
