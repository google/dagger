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

import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;

import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.Set;

/**
 * A collection of utility methods for dealing with Dagger framework types. A framework type is any
 * type that the framework itself defines.
 */
public final class FrameworkTypes {
  // TODO(erichang): Add the Jakarta Provider here
  private static final ImmutableSet<ClassName> PROVISION_TYPES =
      ImmutableSet.of(TypeNames.PROVIDER, TypeNames.LAZY, TypeNames.MEMBERS_INJECTOR);

  private static final ImmutableSet<ClassName> ACCESSOR_TYPES =
      ImmutableSet.of(TypeNames.PROVIDER, TypeNames.LAZY, TypeNames.PRODUCED, TypeNames.PRODUCER);

  // NOTE(beder): ListenableFuture is not considered a producer framework type because it is not
  // defined by the framework, so we can't treat it specially in ordinary Dagger.
  private static final ImmutableSet<ClassName> PRODUCTION_TYPES =
      ImmutableSet.of(TypeNames.PRODUCED, TypeNames.PRODUCER);

  private static final ImmutableSet<ClassName> ALL_FRAMEWORK_TYPES =
      ImmutableSet.<ClassName>builder().addAll(PROVISION_TYPES).addAll(PRODUCTION_TYPES).build();

  /** Returns true if the type represents a producer-related framework type. */
  public static boolean isProducerType(XType type) {
    return typeIsOneOf(PRODUCTION_TYPES, type);
  }

  /** Returns true if the type represents a framework type. */
  public static boolean isFrameworkType(XType type) {
    return typeIsOneOf(ALL_FRAMEWORK_TYPES, type);
  }

  /** Returns true if the type represents an accessor type. */
  public static boolean isAccessorType(XType type) {
    return typeIsOneOf(ACCESSOR_TYPES, type);
  }

  private static boolean typeIsOneOf(Set<ClassName> classNames, XType type) {
    return classNames.stream().anyMatch(className -> isTypeOf(type, className));
  }

  private FrameworkTypes() {}
}
