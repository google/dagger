/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.xprocessing.XTypeNames.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.providerTypeNames;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeNames;

/**
 * A collection of utility methods for dealing with Dagger framework types. A framework type is any
 * type that the framework itself defines.
 */
public final class FrameworkTypes {
  // TODO(erichang): Add the Jakarta Provider here
  private static final ImmutableSet<XClassName> PROVISION_TYPES =
      ImmutableSet.<XClassName>builder()
          .addAll(providerTypeNames())
          .add(XTypeNames.LAZY)
          .add(XTypeNames.MEMBERS_INJECTOR)
          .build();

  // NOTE(beder): ListenableFuture is not considered a producer framework type because it is not
  // defined by the framework, so we can't treat it specially in ordinary Dagger.
  private static final ImmutableSet<XClassName> PRODUCTION_TYPES =
      ImmutableSet.of(XTypeNames.PRODUCED, XTypeNames.PRODUCER);

  private static final ImmutableSet<XClassName> ALL_FRAMEWORK_TYPES =
      ImmutableSet.<XClassName>builder().addAll(PROVISION_TYPES).addAll(PRODUCTION_TYPES).build();

  public static final ImmutableSet<XClassName> SET_VALUE_FRAMEWORK_TYPES =
      ImmutableSet.of(XTypeNames.PRODUCED);

  public static final ImmutableSet<XClassName> MAP_VALUE_FRAMEWORK_TYPES =
      ImmutableSet.<XClassName>builder()
          .addAll(providerTypeNames())
          .add(XTypeNames.PRODUCED)
          .add(XTypeNames.PRODUCER)
          .build();

  // This is a set of types that are disallowed from use, but also aren't framework types in the
  // sense that they aren't supported. Like we shouldn't try to unwrap these if we see them, though
  // we shouldn't see them at all if they are correctly caught in validation.
  private static final ImmutableSet<XClassName> DISALLOWED_TYPES =
      ImmutableSet.of(XTypeNames.DAGGER_PROVIDER);

  /** Returns true if the type represents a producer-related framework type. */
  public static boolean isProducerType(XType type) {
    return isTypeOf(type, PRODUCTION_TYPES);
  }

  /** Returns {@code true} if the given {@code typeName} is a framework type. */
  public static boolean isFrameworkTypeName(XTypeName typeName) {
    return isTypeOf(typeName, ALL_FRAMEWORK_TYPES);
  }

  /** Returns true if the type represents a framework type. */
  public static boolean isFrameworkType(XType type) {
    return isTypeOf(type, ALL_FRAMEWORK_TYPES);
  }

  public static boolean isSetValueFrameworkType(XType type) {
    return isTypeOf(type, SET_VALUE_FRAMEWORK_TYPES);
  }

  public static boolean isMapValueFrameworkType(XType type) {
    return isTypeOf(type, MAP_VALUE_FRAMEWORK_TYPES);
  }

  public static boolean isDisallowedType(XType type) {
    return isTypeOf(type, DISALLOWED_TYPES);
  }

  private FrameworkTypes() {}
}
